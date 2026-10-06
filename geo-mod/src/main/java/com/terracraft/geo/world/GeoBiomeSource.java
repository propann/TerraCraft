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
            Map.entry(EarthTerrain.Zone.ICE_CAP, Biomes.ICE_SPIKES),
            Map.entry(EarthTerrain.Zone.TUNDRA, Biomes.SNOWY_PLAINS),
            Map.entry(EarthTerrain.Zone.COLD_TAIGA, Biomes.SNOWY_TAIGA),
            Map.entry(EarthTerrain.Zone.MONSOON, Biomes.SPARSE_JUNGLE),
            Map.entry(EarthTerrain.Zone.STEPPE, Biomes.PLAINS),
            Map.entry(EarthTerrain.Zone.BOREAL, Biomes.TAIGA),
            Map.entry(EarthTerrain.Zone.TEMPERATE, Biomes.PLAINS),
            Map.entry(EarthTerrain.Zone.TEMPERATE_FOREST, Biomes.FOREST),
            Map.entry(EarthTerrain.Zone.MEDITERRANEAN, Biomes.PLAINS),
            Map.entry(EarthTerrain.Zone.DESERT, Biomes.DESERT),
            Map.entry(EarthTerrain.Zone.SAVANNA, Biomes.SAVANNA),
            Map.entry(EarthTerrain.Zone.TROPICAL, Biomes.JUNGLE),
            Map.entry(EarthTerrain.Zone.ALPINE_MEADOW, Biomes.MEADOW),
            Map.entry(EarthTerrain.Zone.ROCKY_PEAK, Biomes.STONY_PEAKS),
            Map.entry(EarthTerrain.Zone.SNOWY_PEAK, Biomes.SNOWY_SLOPES)
    ));

    private final HolderGetter<Biome> biomes;
    private volatile EarthTerrain terrain;
    private volatile boolean osm;
    private final ThreadLocal<long[]> lastColumn = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE, 0});

    public GeoBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
    }

    void bind(EarthTerrain terrain, boolean osm) {
        this.terrain = terrain;
        this.osm = osm;
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
                Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN,
                Biomes.RIVER, Biomes.FROZEN_RIVER, Biomes.FOREST, Biomes.TAIGA, Biomes.JUNGLE, Biomes.PLAINS,
                Biomes.SAVANNA, Biomes.SPARSE_JUNGLE);
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
        if (osm && elevation > 0 && zone.ordinal() < EarthTerrain.Zone.ALPINE_MEADOW.ordinal()) {
            ResourceKey<Biome> mapped = fromOsm(terrain.osm().cellAt(blockX, blockZ), blockX, blockZ,
                    Math.abs(terrain.latitudeAt(blockZ)), zone);
            if (mapped != null) {
                return biomes.getOrThrow(mapped);
            }
        }
        return biomes.getOrThrow(switch (zone) {
            case OCEAN, DEEP_OCEAN -> ocean(Math.abs(terrain.latitudeAt(blockZ)), zone == EarthTerrain.Zone.DEEP_OCEAN);
            default -> LAND.get(zone);
        });
    }

    /** Biome dicté par OpenStreetMap (forêt, parc, eau…), ou null pour garder le climat. */
    private static ResourceKey<Biome> fromOsm(OsmCells.Cell cell, int x, int z, double absLatitude, EarthTerrain.Zone zone) {
        boolean cold = zone == EarthTerrain.Zone.BOREAL || zone == EarthTerrain.Zone.COLD_TAIGA
                || zone == EarthTerrain.Zone.TUNDRA || zone == EarthTerrain.Zone.ICE_CAP;
        boolean tropical = zone == EarthTerrain.Zone.TROPICAL || zone == EarthTerrain.Zone.MONSOON;
        boolean dry = zone == EarthTerrain.Zone.DESERT || zone == EarthTerrain.Zone.SAVANNA;
        if (cell.surface(x, z) == OsmCells.WATER) {
            return cold && absLatitude > 55 ? Biomes.FROZEN_RIVER : Biomes.RIVER;
        }
        return switch (cell.land(x, z)) {
            // Forêts OSM : l'essence suit le climat réel.
            case OsmCells.LAND_FOREST -> cold ? Biomes.TAIGA : tropical ? Biomes.JUNGLE : dry ? Biomes.SAVANNA : Biomes.FOREST;
            // Ville, parcs et champs : peu d'arbres, de l'herbe et des fleurs.
            case OsmCells.LAND_GRASS, OsmCells.LAND_FARMLAND, OsmCells.LAND_URBAN, OsmCells.LAND_PARK, OsmCells.LAND_PITCH ->
                    cold ? null : tropical ? Biomes.SPARSE_JUNGLE : dry ? Biomes.SAVANNA : Biomes.PLAINS;
            case OsmCells.LAND_SAND -> Biomes.BEACH;
            default -> null;
        };
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
