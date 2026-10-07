package com.terracraft.geo.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.geo.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Terrain rouge contrôlé de Mars : relief doux, canyons rares, glace et fer. */
public final class MarsChunkGenerator extends ChunkGenerator {
    public static final MapCodec<MarsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(MarsChunkGenerator::new)));
    private static final int BASE = 72;
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DEEP = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState ROCK = Blocks.RED_SANDSTONE.defaultBlockState();
    private static final BlockState SURFACE = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState SUBSURFACE = Blocks.TERRACOTTA.defaultBlockState();
    private static final BlockState ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState ORE = ModBlocks.TITANIUM_ORE.defaultBlockState();

    public MarsChunkGenerator(BiomeSource source) { super(source); }
    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    public static int height(int x, int z) {
        double broad = noise(x / 520.0, z / 520.0, 11);
        double detail = noise(x / 90.0, z / 90.0, 19);
        double canyon = Math.max(0, Math.abs(noise(x / 180.0, z / 180.0, 31)) - 0.62) * 18;
        return BASE + Mth.floor(broad * 18 + detail * 5 - canyon);
    }

    private static double noise(double x, double z, long salt) {
        int ix = Mth.floor(x), iz = Mth.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = unit(ix, iz, salt), b = unit(ix + 1, iz, salt);
        double c = unit(ix, iz + 1, salt), d = unit(ix + 1, iz + 1, salt);
        return ((a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz) * 2 - 1;
    }

    private static double unit(int x, int z, long salt) {
        return (Apocalypse.hash(x, z, salt) >>> 11) * 0x1.0p-53;
    }

    private static BlockState block(int x, int z, int y, int surface) {
        int depth = surface - y;
        if (depth == 0) return SURFACE;
        if (depth < 4) return SUBSURFACE;
        if (depth > 7 && y > -20 && Math.floorMod(Apocalypse.hash(x, z, y), 1000) < 3) return ORE;
        return y < 0 ? DEEP : ROCK;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structures, BiomeManager biomes, @Nullable WorldGenRegion carver, Set<Holder<Biome>> possible) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos pos = chunk.getPos();
            int minY = chunk.getMinY(), top = chunk.getSectionIndex(chunk.getMaxY());
            for (int i = chunk.getSectionIndex(minY); i <= top; i++) chunk.getSection(i).acquire();
            try {
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    int x = pos.getMinBlockX() + lx, z = pos.getMinBlockZ() + lz;
                    int surface = Math.min(chunk.getMaxY() - 1, height(x, z));
                    for (int y = minY; y <= surface; y++) {
                        BlockState state = y == minY ? BEDROCK : block(x, z, y, surface);
                        chunk.getSection(chunk.getSectionIndex(y)).setBlockState(lx, SectionPos.sectionRelative(y), lz, state, false);
                    }
                    if (surface < BASE - 8 && Math.floorMod(Apocalypse.hash(x, z, 73), 100) < 2) {
                        chunk.getSection(chunk.getSectionIndex(surface + 1)).setBlockState(lx, SectionPos.sectionRelative(surface + 1), lz, ICE, false);
                    }
                }
            } finally {
                for (int i = chunk.getSectionIndex(minY); i <= top; i++) chunk.getSection(i).release();
            }
            Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
            return chunk;
        }, Util.backgroundExecutor().forName("terracraftMars"));
    }

    private BlockState[] column(int x, int z, LevelHeightAccessor height) {
        int surface = height(x, z); BlockState[] states = new BlockState[height.getHeight()]; Arrays.fill(states, Blocks.AIR.defaultBlockState());
        for (int i = 0; i < states.length; i++) { int y = height.getMinY() + i; if (y <= surface) states[i] = i == 0 ? BEDROCK : block(x, z, y, surface); }
        return states;
    }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor h, RandomState r) { return Math.min(h.getMaxY(), height(x, z) + 1); }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor h, RandomState r) { return new NoiseColumn(h.getMinY(), column(x, z, h)); }
    @Override public void addDebugScreenInfo(List<String> result, RandomState r, BlockPos pos, SamplerContext context) { result.add("TerraCraft : Mars"); }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return -63; }
    @Override public int getMinY() { return -64; }
    @Override public int getSpawnHeight(LevelHeightAccessor height) { return BASE + 1; }
}
