package com.terracraft.geo.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * La Lune : plaines grises de régolithe, mers de basalte sombres et cratères de toutes tailles
 * avec leurs remparts. Procédural et déterministe (aucune donnée à télécharger).
 */
public final class MoonChunkGenerator extends ChunkGenerator {
    public static final MapCodec<MoonChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(MoonChunkGenerator::new)));

    public static final int BASE = 80;
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEP = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState ROCK = Blocks.STONE.defaultBlockState();
    private static final BlockState HIGHLAND = Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
    private static final BlockState HIGHLAND_SUB = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState MARE = Blocks.SMOOTH_BASALT.defaultBlockState();
    private static final BlockState MARE_SUB = Blocks.BASALT.defaultBlockState();
    private static final BlockState EJECTA = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState TITANIUM = ModBlocks.TITANIUM_ORE.defaultBlockState();
    private static final BlockState CRYSTALS = ModBlocks.HELIUM3_CRYSTALS.defaultBlockState();
    static final ResourceKey<LootTable> SATELLITE_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/satellite"));

    public MoonChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // --- Relief ---------------------------------------------------------------------------

    /** Hauteur du sol (y du bloc de surface). */
    public static int height(int x, int z) {
        double h = BASE + 10 * fbm(x / 420.0, z / 420.0, 4, 1) + 3 * fbm(x / 60.0, z / 60.0, 2, 2);
        if (isMare(x, z)) {
            h = BASE - 6 + (h - BASE) * 0.25; // Mers : bassins plus bas et lisses.
        }
        h += craterDepth(x, z);
        return (int) Math.floor(h);
    }

    /** Relief dû aux cratères (négatif au fond, positif sur les remparts). */
    static double craterDepth(int x, int z) {
        return craters(x, z, 96, 8, 40, 60, 11) + craters(x, z, 520, 50, 150, 35, 13);
    }

    /** Bruit de valeur 3D lissé dans [0, 1) pour les filons. */
    private static double noise3(double x, double y, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fy = smooth(y - y0);
        double fz = smooth(z - z0);
        double c00 = lerp(unit3(x0, y0, z0, salt), unit3(x0 + 1, y0, z0, salt), fx);
        double c10 = lerp(unit3(x0, y0 + 1, z0, salt), unit3(x0 + 1, y0 + 1, z0, salt), fx);
        double c01 = lerp(unit3(x0, y0, z0 + 1, salt), unit3(x0 + 1, y0, z0 + 1, salt), fx);
        double c11 = lerp(unit3(x0, y0 + 1, z0 + 1, salt), unit3(x0 + 1, y0 + 1, z0 + 1, salt), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double unit3(int x, int y, int z, long salt) {
        return (Apocalypse.hash(x * 31L + y * 1_000_003L, z, salt) >>> 11) * 0x1.0p-53;
    }

    static boolean isMare(int x, int z) {
        return fbm(x / 1400.0, z / 1400.0, 3, 5) < -0.18;
    }

    /** Somme des profils de cratères (cuvette parabolique + rempart) des cellules voisines. */
    private static double craters(int x, int z, int grid, int minR, int maxR, int percent, long salt) {
        double sum = 0;
        int gx = Math.floorDiv(x, grid);
        int gz = Math.floorDiv(z, grid);
        for (int cx = gx - 1; cx <= gx + 1; cx++) {
            for (int cz = gz - 1; cz <= gz + 1; cz++) {
                long h = Apocalypse.hash(cx, cz, salt);
                if (Math.floorMod(h, 100) >= percent) {
                    continue;
                }
                double r = minR + Math.floorMod(h >>> 8, maxR - minR + 1) * Math.pow(Math.floorMod(h >>> 20, 100) / 100.0, 1.5);
                r = Math.max(minR, r);
                double px = cx * grid + Math.floorMod(h >>> 30, grid);
                double pz = cz * grid + Math.floorMod(h >>> 42, grid);
                double d = Math.hypot(x - px, z - pz) / r;
                double depth = r * 0.3;
                if (d < 1) {
                    sum -= depth * (1 - d * d);
                    sum += depth * 0.35 * Math.max(0, d - 0.7) / 0.3; // remontée vers le rempart
                } else if (d < 1.6) {
                    sum += depth * 0.35 * (1 - (d - 1) / 0.6);
                }
            }
        }
        return sum;
    }

    private static double fbm(double x, double z, int octaves, long salt) {
        double sum = 0;
        double amplitude = 1;
        double norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * (valueNoise(x, z, salt + i) * 2 - 1);
            norm += amplitude;
            amplitude *= 0.5;
            x *= 2;
            z *= 2;
        }
        return sum / norm;
    }

    private static double valueNoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = unit(x0, z0, salt);
        double b = unit(x0 + 1, z0, salt);
        double c = unit(x0, z0 + 1, salt);
        double d = unit(x0 + 1, z0 + 1, salt);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double unit(int x, int z, long salt) {
        return (Apocalypse.hash(x, z, salt * 977 + 3) >>> 11) * 0x1.0p-53;
    }

    // --- Génération -------------------------------------------------------------------------

    private static BlockState block(int x, int z, int y, int surface, boolean rim) {
        int depth = surface - y;
        boolean mare = isMare(x, z);
        if (depth <= 1 && craterDepth(x, z) < -12) {
            return depth == 0 ? ICE : PACKED_ICE; // Glace piégée au fond des grands cratères.
        }
        if (depth == 0) {
            return rim ? EJECTA : mare ? MARE : HIGHLAND;
        }
        if (depth < 4) {
            return mare ? MARE_SUB : HIGHLAND_SUB;
        }
        if (depth > 6 && y > -40 && y < 75 && noise3(x / 5.0, y / 4.0, z / 5.0, 41) > 0.91) {
            return TITANIUM;
        }
        return y < 0 ? DEEP : ROCK;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
                                                       StructureManager structureManager, BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos pos = chunk.getPos();
            int minY = chunk.getMinY();
            int top = chunk.getSectionIndex(chunk.getMaxY());
            for (int i = chunk.getSectionIndex(minY); i <= top; i++) {
                chunk.getSection(i).acquire();
            }
            try {
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = pos.getMinBlockX() + lx;
                        int z = pos.getMinBlockZ() + lz;
                        int surface = Math.min(chunk.getMaxY() - 1, height(x, z));
                        boolean rim = Math.abs(height(x + 1, z) - surface) + Math.abs(height(x, z + 1) - surface) >= 3;
                        for (int y = minY; y <= surface; y++) {
                            BlockState state = y == minY ? BEDROCK : block(x, z, y, surface, rim);
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            section.setBlockState(lx, SectionPos.sectionRelative(y), lz, state, false);
                        }
                        // Cristaux d'hélium-3 affleurant sur les hautes terres (0,3 % des colonnes).
                        if (!isMare(x, z) && Math.floorMod(Apocalypse.hash(x, z, 43), 1000) < 3 && surface + 1 < chunk.getMaxY()) {
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(surface + 1));
                            section.setBlockState(lx, SectionPos.sectionRelative(surface + 1), lz, CRYSTALS, false);
                        }
                    }
                }
            } finally {
                for (int i = chunk.getSectionIndex(minY); i <= top; i++) {
                    chunk.getSection(i).release();
                }
            }
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
            return chunk;
        }, Util.backgroundExecutor().forName("terracraftMoon"));
    }

    private BlockState[] column(int x, int z, LevelHeightAccessor height) {
        int surface = height(x, z);
        BlockState[] states = new BlockState[height.getHeight()];
        Arrays.fill(states, AIR);
        for (int i = 0; i < states.length; i++) {
            int y = height.getMinY() + i;
            if (y <= surface) {
                states[i] = i == 0 ? BEDROCK : block(x, z, y, surface, false);
            }
        }
        return states;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState randomState) {
        return Math.min(height.getMaxY(), height(x, z) + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState randomState) {
        return new NoiseColumn(height.getMinY(), column(x, z, height));
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        result.add("TerraCraft : la Lune" + (isMare(feetPos.getX(), feetPos.getZ()) ? " (mer)" : " (hautes terres)"));
    }

    /** Épave de satellite : carcasse métallique, panneaux solaires brisés et un coffre (1,5 % des chunks). */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        ChunkPos pos = chunk.getPos();
        long h = Apocalypse.hash(pos.x(), pos.z(), 47);
        if (Math.floorMod(h, 1000) >= 15) {
            return;
        }
        int cx = pos.getMinBlockX() + 8;
        int cz = pos.getMinBlockZ() + 8;
        int ground = height(cx, cz) + 1;
        BlockState body = ModBlocks.STATION_HULL.defaultBlockState();
        BlockState panel = Blocks.STAINED_GLASS.pick(DyeColor.BLUE).defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (Apocalypse.roll(cx + dx, cz + dz * 7L + dy, 49) < 80) {
                        level.setBlock(new BlockPos(cx + dx, ground + dy, cz + dz), dy == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : body, 2);
                    }
                }
            }
        }
        for (int k = 2; k <= 5; k++) {
            if (Apocalypse.roll(cx + k, cz, 53) < 75) {
                level.setBlock(new BlockPos(cx + k, ground, cz), panel, 2);
                level.setBlock(new BlockPos(cx - k, ground, cz), panel, 2);
            }
        }
        BlockPos chest = new BlockPos(cx, ground + 2, cz);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(h), chest, SATELLITE_LOOT);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor height) {
        return BASE + 1;
    }
}
