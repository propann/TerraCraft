package com.terracraft.geo.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Générateur de chunks « Terre réelle » : chaque colonne suit l'altitude réelle du point
 * géographique correspondant (projection {@link WebMercator}).
 */
public final class GeoChunkGenerator extends ChunkGenerator {
    public static final MapCodec<GeoChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.<Biome, GeoChunkGenerator>retrieveGetter(Registries.BIOME),
            Codec.doubleRange(0.01, 1.0).optionalFieldOf("scale", 1.0).forGetter(GeoChunkGenerator::scale)
    ).apply(i, i.stable(GeoChunkGenerator::create)));

    private static final int GEN_DEPTH = 384;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private final double scale;
    private final EarthTerrain terrain;

    private GeoChunkGenerator(GeoBiomeSource biomeSource, double scale) {
        super(biomeSource);
        this.scale = scale;
        this.terrain = EarthTerrain.shared(scale, FabricLoader.getInstance().getGameDir().resolve("terracraft-cache"));
        biomeSource.bind(terrain);
    }

    private static GeoChunkGenerator create(HolderGetter<Biome> biomes, double scale) {
        return new GeoChunkGenerator(new GeoBiomeSource(biomes), scale);
    }

    public double scale() {
        return scale;
    }

    public EarthTerrain terrain() {
        return terrain;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** Matériaux d'une colonne, du haut vers le bas, juste sous la surface. */
    private record Column(int surfaceY, BlockState[] top) {
    }

    private Column column(int blockX, int blockZ, double elevation, int surfaceY, int slope) {
        EarthTerrain.Zone zone = terrain.zone(blockX, blockZ, elevation);
        BlockState[] top = switch (zone) {
            case OCEAN, DEEP_OCEAN -> EarthTerrain.SEA_LEVEL - 1 - surfaceY <= 15
                    ? new BlockState[]{SAND, SAND, SAND, SANDSTONE}
                    : new BlockState[]{GRAVEL, GRAVEL};
            case SHORE -> new BlockState[]{SAND, SAND, SAND, SANDSTONE};
            case DESERT -> slope >= 4
                    ? new BlockState[]{SANDSTONE, SANDSTONE}
                    : new BlockState[]{SAND, SAND, SAND, SANDSTONE, SANDSTONE};
            case SNOWY_PEAK -> slope >= 6 ? new BlockState[]{STONE} : new BlockState[]{SNOW, SNOW};
            case ROCKY_PEAK -> slope >= 3 ? new BlockState[]{STONE} : new BlockState[]{GRAVEL, STONE};
            default -> slope >= 4 ? new BlockState[]{STONE} : new BlockState[]{GRASS, DIRT, DIRT, DIRT};
        };
        return new Column(surfaceY, top);
    }

    private static BlockState below(int y, Column column) {
        int depth = column.surfaceY - y;
        if (depth < column.top.length) {
            return column.top[depth];
        }
        return y < 0 ? DEEPSLATE : STONE;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
                                                       StructureManager structureManager, BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion carverBiomeRegion,
                                                       Set<Holder<Biome>> possibleBiomes) {
        return CompletableFuture.supplyAsync(() -> {
            fill(chunk);
            return chunk;
        }, Util.backgroundExecutor().forName("terracraftTerrain"));
    }

    private void fill(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        // Grille 18×18 : la bordure sert à mesurer la pente des colonnes du chunk.
        double[] elevation = new double[18 * 18];
        int[] surface = new int[18 * 18];
        for (int dz = 0; dz < 18; dz++) {
            int z = minZ + dz - 1;
            double latitude = terrain.latitudeAt(z);
            for (int dx = 0; dx < 18; dx++) {
                double e = terrain.elevation(minX + dx - 1, z);
                elevation[dz * 18 + dx] = e;
                surface[dz * 18 + dx] = terrain.surfaceY(e, latitude);
            }
        }

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int chunkMinY = chunk.getMinY();
        int chunkMaxY = chunk.getMaxY();
        int topY = Math.min(chunkMaxY, EarthTerrain.MAX_SURFACE_Y);
        int top = chunk.getSectionIndex(topY);
        int bottom = chunk.getSectionIndex(chunkMinY);
        for (int i = bottom; i <= top; i++) {
            chunk.getSection(i).acquire();
        }
        try {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int index = (z + 1) * 18 + (x + 1);
                    int surfaceY = surface[index];
                    int slope = Math.max(
                            Math.max(Math.abs(surfaceY - surface[index - 1]), Math.abs(surfaceY - surface[index + 1])),
                            Math.max(Math.abs(surfaceY - surface[index - 18]), Math.abs(surfaceY - surface[index + 18])));
                    Column column = column(minX + x, minZ + z, elevation[index], surfaceY, slope);
                    int waterTop = EarthTerrain.SEA_LEVEL - 1;
                    for (int y = Math.max(surfaceY, waterTop); y >= chunkMinY; y--) {
                        if (y > chunkMaxY) {
                            continue;
                        }
                        BlockState state = y == chunkMinY ? BEDROCK : y > surfaceY ? WATER : below(y, column);
                        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                        section.setBlockState(x, SectionPos.sectionRelative(y), z, state, false);
                        oceanFloor.update(x, y, z, state);
                        worldSurface.update(x, y, z, state);
                    }
                }
            }
        } finally {
            for (int i = bottom; i <= top; i++) {
                chunk.getSection(i).release();
            }
        }
    }

    private BlockState[] baseColumn(int blockX, int blockZ, LevelHeightAccessor height) {
        double elevation = terrain.elevation(blockX, blockZ);
        int surfaceY = terrain.surfaceY(elevation, terrain.latitudeAt(blockZ));
        Column column = column(blockX, blockZ, elevation, surfaceY, 0);
        BlockState[] states = new BlockState[height.getHeight()];
        Arrays.fill(states, AIR);
        for (int i = 0; i < states.length; i++) {
            int y = height.getMinY() + i;
            if (y == height.getMinY()) {
                states[i] = BEDROCK;
            } else if (y <= surfaceY) {
                states[i] = below(y, column);
            } else if (y < EarthTerrain.SEA_LEVEL) {
                states[i] = WATER;
            }
        }
        return states;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState randomState) {
        BlockState[] states = baseColumn(x, z, height);
        for (int i = states.length - 1; i >= 0; i--) {
            if (type.isOpaque().test(states[i])) {
                return height.getMinY() + i + 1;
            }
        }
        return height.getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState randomState) {
        return new NoiseColumn(height.getMinY(), baseColumn(x, z, height));
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        double latitude = terrain.latitudeAt(feetPos.getZ());
        double longitude = terrain.longitudeAt(feetPos.getX());
        result.add(String.format(Locale.ROOT, "TerraCraft: %.5f, %.5f | alt. réelle %.0f m | 1 bloc = %.2f m",
                latitude, longitude, terrain.elevation(feetPos.getX(), feetPos.getZ()),
                1.0 / terrain.blocksPerMetre(latitude)));
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos center = region.getCenter();
        BlockPos sourcePos = center.getWorldPosition().atY(region.getMaxY());
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, sourcePos, center, random);
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getSeaLevel() {
        return EarthTerrain.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return EarthTerrain.MIN_Y;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor height) {
        return EarthTerrain.SEA_LEVEL + 1;
    }
}
