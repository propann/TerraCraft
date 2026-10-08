package com.terracraft.geo.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.geo.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Ceinture d'astéroïdes : rochers flottants dans le vide (rayon 3 à 11), entre y = 50 et 250. Trois sortes : rocheux
 * (fer, titane, rares diamants), métalliques (fer et or abondants) et glacés (cristaux d'hélium-3). Déterministe ;
 * le voisinage du point d'arrivée (0, 0) reste dégagé pour le quai.
 */
public final class AsteroidChunkGenerator extends ChunkGenerator {
    public static final MapCodec<AsteroidChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(AsteroidChunkGenerator::new)));

    private static final int CELL = 36;
    private static final int MIN_Y = 50;
    private static final int MAX_Y = 250;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    record Asteroid(int x, int y, int z, int radius, int sort, long seed) {
    }

    public AsteroidChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** Astéroïde de la cellule (cx, cy, cz), ou null (55 % des cellules en ont un). */
    static Asteroid asteroid(int cx, int cy, int cz) {
        long h = Apocalypse.hash(cx * 1_000_003L + cy, cz, 401);
        if (Math.floorMod(h, 100) >= 55) {
            return null;
        }
        int x = cx * CELL + 6 + (int) Math.floorMod(h >>> 8, CELL - 12);
        int y = cy * CELL + 6 + (int) Math.floorMod(h >>> 16, CELL - 12);
        int z = cz * CELL + 6 + (int) Math.floorMod(h >>> 24, CELL - 12);
        if (y < MIN_Y || y > MAX_Y || Math.hypot(x, z) < 40) {
            return null; // Le quai d'arrivée (autour de 0, 0) reste dégagé.
        }
        int radius = 3 + (int) Math.floorMod(h >>> 32, 9);
        int sort = (int) Math.floorMod(h >>> 40, 10); // 0-5 rocheux, 6-7 métallique, 8-9 glacé
        return new Asteroid(x, y, z, radius, sort, h);
    }

    /** Astéroïdes qui touchent la colonne (x, z). */
    static List<Asteroid> around(int x, int z) {
        List<Asteroid> found = new ArrayList<>();
        int gx = Math.floorDiv(x, CELL);
        int gz = Math.floorDiv(z, CELL);
        for (int cx = gx - 1; cx <= gx + 1; cx++) {
            for (int cz = gz - 1; cz <= gz + 1; cz++) {
                for (int cy = Math.floorDiv(MIN_Y, CELL); cy <= Math.floorDiv(MAX_Y, CELL); cy++) {
                    Asteroid a = asteroid(cx, cy, cz);
                    if (a != null && Math.abs(a.x - x) <= a.radius + 2 && Math.abs(a.z - z) <= a.radius + 2) {
                        found.add(a);
                    }
                }
            }
        }
        return found;
    }

    /** Astéroïde le plus proche de (x, y, z) (commande d'administration). */
    public static int[] nearest(int x, int y, int z) {
        int[] best = null;
        double distance = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (Asteroid a : around(x + dx * CELL, z + dz * CELL)) {
                    double d = Math.sqrt(Math.pow(a.x - x, 2) + Math.pow(a.y - y, 2) + Math.pow(a.z - z, 2));
                    if (d < distance) {
                        distance = d;
                        best = new int[]{a.x, a.y, a.z, a.radius};
                    }
                }
            }
        }
        return best;
    }

    private static BlockState block(Asteroid a, int x, int y, int z, double depth) {
        long h = Apocalypse.hash(x * 31L + y, z, a.seed & 0xFFFF);
        int roll = (int) Math.floorMod(h, 1000);
        if (a.sort >= 8) {
            if (roll < 40) {
                return ModBlocks.HELIUM3_CRYSTALS.defaultBlockState();
            }
            return depth < 1.5 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
        }
        if (a.sort >= 6) {
            if (roll < 90) {
                return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            }
            if (roll < 115) {
                return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
            }
            return roll < 600 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        if (roll < 25) {
            return Blocks.IRON_ORE.defaultBlockState();
        }
        if (roll < 40) {
            return ModBlocks.TITANIUM_ORE.defaultBlockState();
        }
        if (roll < 42 && depth > 3) {
            return Blocks.DIAMOND_ORE.defaultBlockState();
        }
        return roll < 500 ? Blocks.STONE.defaultBlockState() : roll < 800 ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    /** Remplit la colonne (indice = y - minY) ; renvoie le y le plus haut occupé, ou minY - 1. */
    private static int fill(int x, int z, int minY, BlockState[] states) {
        Arrays.fill(states, AIR);
        int top = minY - 1;
        for (Asteroid a : around(x, z)) {
            double dx = x - a.x;
            double dz = z - a.z;
            for (int y = a.y - a.radius - 1; y <= a.y + a.radius + 1; y++) {
                double dy = (y - a.y) * 1.3; // Un peu aplatis.
                double bump = (Math.floorMod(Apocalypse.hash(x, z * 7L + y, a.seed & 0xFF), 100) - 50) / 100.0;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < a.radius + bump && y - minY >= 0 && y - minY < states.length) {
                    states[y - minY] = block(a, x, y, z, a.radius - distance);
                    top = Math.max(top, y);
                }
            }
        }
        return top;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
                                                       StructureManager structureManager, BiomeManager biomeManager,
                                                       @Nullable WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos pos = chunk.getPos();
            int minY = chunk.getMinY();
            BlockState[] states = new BlockState[chunk.getHeight()];
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int top = fill(pos.getMinBlockX() + lx, pos.getMinBlockZ() + lz, minY, states);
                    for (int y = minY; y <= top; y++) {
                        BlockState state = states[y - minY];
                        if (!state.isAir()) {
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            section.setBlockState(lx, SectionPos.sectionRelative(y), lz, state, false);
                        }
                    }
                }
            }
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
            return chunk;
        }, Util.backgroundExecutor().forName("terracraftAsteroids"));
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState randomState) {
        BlockState[] states = new BlockState[height.getHeight()];
        return fill(x, z, height.getMinY(), states) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState randomState) {
        BlockState[] states = new BlockState[height.getHeight()];
        fill(x, z, height.getMinY(), states);
        return new NoiseColumn(height.getMinY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        result.add("TerraCraft : ceinture d'astéroïdes");
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
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
        return 151;
    }
}
