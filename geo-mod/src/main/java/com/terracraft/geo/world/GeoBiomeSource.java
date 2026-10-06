package com.terracraft.geo.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Biomes choisis à partir de la zone climatique réelle calculée par {@link EarthTerrain}. */
public final class GeoBiomeSource extends BiomeSource {
    public static final MapCodec<GeoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, i.stable(GeoBiomeSource::new)));

    private static final Map<EarthTerrain.Zone, ResourceKey<Biome>> LAND = new EnumMap<>(Map.ofEntries(
            Map.entry(EarthTerrain.Zone.SHORE, Biomes.BEACH),
            Map.entry(EarthTerrain.Zone.ICE_CAP, Biomes.SNOWY_PLAINS),
            Map.entry(EarthTerrain.Zone.TUNDRA, Biomes.SNOWY_TAIGA),
            Map.entry(EarthTerrain.Zone.BOREAL, Biomes.TAIGA),
            Map.entry(EarthTerrain.Zone.TEMPERATE, Biomes.PLAINS),
            Map.entry(EarthTerrain.Zone.TEMPERATE_FOREST, Biomes.FOREST),
            Map.entry(EarthTerrain.Zone.MEDITERRANEAN, Biomes.SAVANNA_PLATEAU),
            Map.entry(EarthTerrain.Zone.DESERT, Biomes.DESERT),
            Map.entry(EarthTerrain.Zone.SAVANNA, Biomes.SAVANNA),
            Map.entry(EarthTerrain.Zone.TROPICAL, Biomes.JUNGLE),
            Map.entry(EarthTerrain.Zone.ALPINE_MEADOW, Biomes.MEADOW),
            Map.entry(EarthTerrain.Zone.ROCKY_PEAK, Biomes.STONY_PEAKS),
            Map.entry(EarthTerrain.Zone.SNOWY_PEAK, Biomes.SNOWY_SLOPES)
    ));

    private final HolderGetter<Biome> biomes;
    private volatile EarthTerrain terrain;
    private final ThreadLocal<long[]> lastColumn = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE, 0});

    public GeoBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
    }

    void bind(EarthTerrain terrain) {
        this.terrain = terrain;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<ResourceKey<Biome>> oceans = Stream.of(
                Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
                Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN,
                Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN);
        return Stream.concat(oceans, LAND.values().stream()).distinct().map(biomes::getOrThrow);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (quartX, quartY, quartZ) -> biomeAt(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
    }

    private Holder<Biome> biomeAt(int blockX, int blockZ) {
        EarthTerrain terrain = this.terrain;
        if (terrain == null) {
            return biomes.getOrThrow(Biomes.PLAINS);
        }
        // Le résolveur est appelé pour chaque Y d'une même colonne : on mémorise la dernière.
        long[] cache = lastColumn.get();
        long column = ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
        double elevation;
        if (cache[0] == column) {
            elevation = Double.longBitsToDouble(cache[1]);
        } else {
            elevation = terrain.elevation(blockX, blockZ);
            cache[0] = column;
            cache[1] = Double.doubleToRawLongBits(elevation);
        }
        EarthTerrain.Zone zone = terrain.zone(blockX, blockZ, elevation);
        return biomes.getOrThrow(switch (zone) {
            case OCEAN, DEEP_OCEAN -> ocean(Math.abs(terrain.latitudeAt(blockZ)), zone == EarthTerrain.Zone.DEEP_OCEAN);
            default -> LAND.get(zone);
        });
    }

    private static ResourceKey<Biome> ocean(double absLatitude, boolean deep) {
        if (absLatitude < 23) {
            return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.WARM_OCEAN;
        }
        if (absLatitude < 40) {
            return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
        }
        if (absLatitude < 58) {
            return deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
        }
        if (absLatitude < 68) {
            return deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
        }
        return deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
    }
}
