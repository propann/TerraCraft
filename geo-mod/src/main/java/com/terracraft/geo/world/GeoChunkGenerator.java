package com.terracraft.geo.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
            Codec.doubleRange(0.01, 1.0).optionalFieldOf("scale", 1.0).forGetter(GeoChunkGenerator::scale),
            Codec.BOOL.optionalFieldOf("osm", true).forGetter(g -> g.osm),
            Codec.BOOL.optionalFieldOf("apocalypse", false).forGetter(g -> g.apocalypse)
    ).apply(i, i.stable(GeoChunkGenerator::create)));

    private static final int GEN_DEPTH = 1536;
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
    private static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
    private static final BlockState ROAD_MAJOR = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
    private static final BlockState ROAD_MINOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState FOOTWAY = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState TRACK = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState BRIDGE = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState PARAPET = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.SPRUCE_PLANKS.defaultBlockState();
    /** Bloc lumineux invisible : empêche l'apparition de monstres dans les bâtiments. */
    private static final BlockState LIGHT = Blocks.LIGHT.defaultBlockState();
    private static final BlockState LAMP_POST = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final Map<String, BlockState> BLOCK_CACHE = new ConcurrentHashMap<>();

    private final double scale;
    private final boolean osm;
    /** Monde post-apocalyptique : ruines, végétation, voitures abandonnées, aucune lumière. */
    private final boolean apocalypse;
    private final EarthTerrain terrain;

    private GeoChunkGenerator(GeoBiomeSource biomeSource, double scale, boolean osm, boolean apocalypse) {
        super(biomeSource);
        this.scale = scale;
        this.osm = osm;
        this.apocalypse = apocalypse;
        this.terrain = EarthTerrain.shared(scale, FabricLoader.getInstance().getGameDir().resolve("terracraft-cache"));
        biomeSource.bind(terrain, osm);
    }

    private static GeoChunkGenerator create(HolderGetter<Biome> biomes, double scale, boolean osm, boolean apocalypse) {
        return new GeoChunkGenerator(new GeoBiomeSource(biomes), scale, osm, apocalypse);
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
            case ICE_CAP -> new BlockState[]{SNOW, PACKED_ICE, PACKED_ICE};
            // Steppe : herbe rase et plaques de terre sèche.
            case STEPPE -> Math.floorMod(Apocalypse.hash(blockX, blockZ, 71), 100) < 30
                    ? new BlockState[]{TRACK, DIRT, DIRT} : new BlockState[]{GRASS, DIRT, DIRT};
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

    /** Revêtement de surface selon l'occupation du sol OSM (null = garder le terrain). */
    private static BlockState[] landTop(byte land) {
        return switch (land) {
            case OsmCells.LAND_SAND -> new BlockState[]{SAND, SAND, SAND, SANDSTONE};
            case OsmCells.LAND_ROCK -> new BlockState[]{STONE};
            case OsmCells.LAND_GLACIER -> new BlockState[]{PACKED_ICE, PACKED_ICE, SNOW};
            case OsmCells.LAND_WETLAND -> new BlockState[]{MUD, MUD, DIRT};
            default -> null;
        };
    }

    private static BlockState roadBlock(byte code) {
        return switch (code) {
            case OsmCells.ROAD_MAJOR -> ROAD_MAJOR;
            case OsmCells.ROAD_MINOR -> ROAD_MINOR;
            case OsmCells.FOOTWAY -> FOOTWAY;
            case OsmCells.TRACK -> TRACK;
            case OsmCells.RAIL -> GRAVEL;
            default -> null;
        };
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
        OsmCells.Cell osm = this.osm ? terrain.osm().cellAt(minX, minZ) : null;

        int chunkMinY = chunk.getMinY();
        int chunkMaxY = chunk.getMaxY();
        int top = chunk.getSectionIndex(chunkMaxY);
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
                    fillColumn(chunk, osm, minX + x, minZ + z, x, z, elevation[index], surfaceY, slope);
                }
            }
            if (apocalypse) {
                Wasteland.carve(chunk, terrain, osm,
                        (bx, bz) -> bx >= minX && bx < minX + 16 && bz >= minZ && bz < minZ + 16
                                ? surface[(bz - minZ + 1) * 18 + (bx - minX + 1)]
                                : terrain.surfaceY(bx, bz),
                        (lx, y, lz, state) -> {
                            if (y > chunkMinY && y < chunkMaxY) {
                                set(chunk, lx, y, lz, state);
                            }
                        });
            }
        } finally {
            for (int i = bottom; i <= top; i++) {
                chunk.getSection(i).release();
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private void fillColumn(ChunkAccess chunk, OsmCells.@Nullable Cell osm, int blockX, int blockZ, int x, int z,
                            double elevation, int surfaceY, int slope) {
        Column column = column(blockX, blockZ, elevation, surfaceY, slope);
        int solidTop = surfaceY;
        int waterTop = EarthTerrain.SEA_LEVEL - 1;
        boolean land = surfaceY >= EarthTerrain.SEA_LEVEL;
        OsmCells.Building building = null;
        short deck = OsmCells.NO_LEVEL;
        if (osm != null && land) {
            byte code = osm.surface(blockX, blockZ);
            BlockState[] landTop = landTop(osm.land(blockX, blockZ));
            if (code == OsmCells.WATER) {
                // Rivière ou lac : niveau constant, lit creusé de trois blocs.
                waterTop = Math.min(osm.waterLevel(blockX, blockZ), surfaceY - 1);
                solidTop = waterTop - 3;
                column = new Column(solidTop, new BlockState[]{GRAVEL, DIRT, DIRT});
            } else if (isTreePit(UrbanTrees.kind(osm, blockX, blockZ, apocalypse))) {
                BlockState[] top = column.top.clone();
                top[0] = GRASS; // Fosse d'arbre dans le trottoir.
                column = new Column(surfaceY, top);
            } else if (roadBlock(code) != null) {
                BlockState[] top = column.top.clone();
                top[0] = apocalypse ? Apocalypse.road(roadBlock(code), blockX, blockZ) : roadBlock(code);
                column = new Column(surfaceY, top);
            } else if (landTop != null) {
                column = new Column(surfaceY, landTop);
            }
            building = osm.building(blockX, blockZ);
            deck = osm.deck(blockX, blockZ);
        }

        int minY = chunk.getMinY();
        for (int y = Math.max(solidTop, waterTop); y >= minY; y--) {
            BlockState state = y == minY ? BEDROCK : y > solidTop ? WATER : below(y, column);
            set(chunk, x, y, z, state);
        }
        if (building != null) {
            fillBuilding(chunk, osm, building, blockX, blockZ, x, z, solidTop, apocalypse);
        } else if (deck != OsmCells.NO_LEVEL && deck > Math.max(solidTop, waterTop) && deck + 1 < chunk.getMaxY()) {
            fillBridge(chunk, osm.deckFlags(blockX, blockZ), x, z, deck, solidTop, waterTop);
        } else if (osm != null && land && isLampSpot(osm, blockX, blockZ) && solidTop + 7 < chunk.getMaxY()) {
            // Lampadaire : poteau de 5 blocs et lanterne (éclaire la rue, pas de monstres).
            // Apocalypse : plus d'électricité, poteaux parfois cassés.
            int height = apocalypse ? Apocalypse.lampHeight(blockX, blockZ) : 5;
            for (int y = solidTop + 1; y <= solidTop + height; y++) {
                set(chunk, x, y, z, LAMP_POST);
            }
            if (!apocalypse) {
                set(chunk, x, solidTop + 6, z, LANTERN);
            }
        } else if (osm != null && land && apocalypse && solidTop >= waterTop) {
            decorateRuinedStreet(chunk, osm, blockX, blockZ, x, z, solidTop);
        }
        if (osm != null && land && building == null) {
            balconies(chunk, osm, blockX, blockZ, x, z, solidTop, apocalypse);
        }
    }

    /** Tablier de 2 blocs (si la place existe), parapet sur les bords, pilier jusqu'au sol ou au lit. */
    private static void fillBridge(ChunkAccess chunk, byte flags, int x, int z, int deck, int solidTop, int waterTop) {
        int free = Math.max(solidTop, waterTop);
        set(chunk, x, deck, z, BRIDGE);
        if (deck - 1 > free) {
            set(chunk, x, deck - 1, z, BRIDGE);
        }
        if ((flags & OsmCells.DECK_EDGE) != 0) {
            set(chunk, x, deck + 1, z, PARAPET);
        }
        if (solidTop >= waterTop && deck - solidTop <= 5) {
            // Rampe au-dessus de la terre ferme : remblai plein plutôt qu'un tablier qui flotte.
            for (int y = solidTop + 1; y < deck - 1; y++) {
                set(chunk, x, y, z, FOUNDATION);
            }
        } else if ((flags & OsmCells.DECK_PIER) != 0) {
            for (int y = solidTop + 1; y < deck - 1; y++) {
                set(chunk, x, y, z, BRIDGE);
            }
        }
    }

    private static final BlockState BALCONY = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
    private static final BlockState RAILING = Blocks.IRON_BARS.defaultBlockState();

    /** Balcons filants (3 blocs sur 6) aux étages des immeubles à mansarde, côté extérieur. */
    private static void balconies(ChunkAccess chunk, OsmCells.Cell osm, int blockX, int blockZ, int x, int z, int ground,
                                  boolean apocalypse) {
        if (Math.floorMod(blockX + blockZ, 6) >= 3) {
            return;
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            OsmCells.Building b = osm.building(blockX + d[0], blockZ + d[1]);
            if (b == null || b.shape() != OsmCells.RoofShape.MANSARD || !osm.isWall(blockX + d[0], blockZ + d[1])) {
                continue;
            }
            int top = apocalypse ? Apocalypse.effectiveTop(b, blockX + d[0], blockZ + d[1]) : b.topY();
            if (apocalypse && Apocalypse.ruin(b) == Apocalypse.Ruin.RUBBLE) {
                return;
            }
            for (int y = b.baseY() + b.floorStep(); y + 2 < top && y + 1 < chunk.getMaxY(); y += b.floorStep()) {
                if (y > ground + 3 && (!apocalypse || Apocalypse.roll(blockX, blockZ * 7L + y, 263) >= 20)) {
                    set(chunk, x, y, z, BALCONY);
                    set(chunk, x, y + 1, z, RAILING);
                }
            }
            return;
        }
    }

    private static boolean isTreePit(int kind) {
        return kind == UrbanTrees.AVENUE || kind == UrbanTrees.WILD;
    }

    /** Rue en ruine : voitures abandonnées sur la chaussée, lierre sur les façades voisines. */
    private static void decorateRuinedStreet(ChunkAccess chunk, OsmCells.Cell osm, int blockX, int blockZ,
                                             int x, int z, int ground) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int d = 0; d < around.length; d++) {
            int nx = blockX + around[d][0];
            int nz = blockZ + around[d][1];
            OsmCells.Building neighbour = osm.building(nx, nz);
            if (neighbour == null || !osm.isWall(nx, nz) || Apocalypse.ruin(neighbour) == Apocalypse.Ruin.RUBBLE) {
                continue;
            }
            int height = Apocalypse.effectiveTop(neighbour, nx, nz) - ground;
            int length = Apocalypse.vineLength(blockX, blockZ, d, Math.min(height, 40));
            BlockState vine = Apocalypse.vine(around[d][0], around[d][1]);
            for (int y = ground + 1; y <= ground + length && y < chunk.getMaxY(); y++) {
                set(chunk, x, y, z, vine);
            }
            return;
        }
    }

    /** Résout un identifiant de bloc venant d'OSM (mis en cache, pierre si inconnu). */
    private static BlockState block(String id) {
        return BLOCK_CACHE.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key);
            return BuiltInRegistries.BLOCK.getOptional(identifier).map(Block::defaultBlockState).orElse(STONE);
        });
    }

    /**
     * Bâtiment : fondations, murs avec fenêtres, vitrine et portes au rez-de-chaussée,
     * planchers à chaque étage, éclairage invisible (pas de monstres à l'intérieur) et toit
     * dont la pente suit la distance au mur.
     */
    private static void fillBuilding(ChunkAccess chunk, OsmCells.Cell osm, OsmCells.Building building,
                                     int blockX, int blockZ, int x, int z, int groundY, boolean apocalypse) {
        int base = building.baseY();
        int top = building.topY();
        Apocalypse.Ruin ruin = apocalypse ? Apocalypse.ruin(building) : Apocalypse.Ruin.INTACT;
        if (ruin == Apocalypse.Ruin.RUBBLE) {
            for (int y = groundY + 1; y < base; y++) {
                set(chunk, x, y, z, FOUNDATION);
            }
            int pile = Math.min(chunk.getMaxY() - 1, base + Apocalypse.rubbleHeight(blockX, blockZ));
            for (int y = base; y <= pile; y++) {
                set(chunk, x, y, z, Apocalypse.rubble(blockX, y, blockZ));
            }
            return;
        }
        boolean roofless = ruin == Apocalypse.Ruin.BROKEN;
        if (roofless) {
            top = Apocalypse.effectiveTop(building, blockX, blockZ);
        }
        int maxY = chunk.getMaxY();
        int step = building.floorStep();
        boolean wall = osm.isWall(blockX, blockZ);
        int inset = osm.inset(blockX, blockZ);
        BlockState wallBlock = block(building.wall());
        BlockState roofBlock = block(building.roof());
        BlockState windowBlock = block(building.window());
        // Bandeaux horizontaux : une ligne de contraste par étage donne une façade lisible
        // même quand les données OSM ne précisent ni matériau ni couleur.
        BlockState facadeTrim = Blocks.POLISHED_ANDESITE.defaultBlockState();
        boolean door = wall && Math.floorMod(blockX * 31 + blockZ * 17, 11) == 0 && facesOutside(osm, blockX, blockZ);
        boolean lamp = !apocalypse && !wall && Math.floorMod(blockX, 6) == 3 && Math.floorMod(blockZ, 6) == 3;
        net.minecraft.core.Direction ladder = wall ? null : BuildingInterior.ladder(osm, blockX, blockZ);
        boolean partition = !wall && ladder == null && BuildingInterior.isPartition(osm, blockX, blockZ);
        boolean doorway = partition && BuildingInterior.isDoorway(blockX, blockZ);
        BlockState furnitureTop = null;

        for (int y = groundY + 1; y < base; y++) {
            set(chunk, x, y, z, FOUNDATION);
        }
        set(chunk, x, base, z, FOUNDATION);
        for (int y = base + 1; y < top && y < maxY; y++) {
            int level = (y - base) % step;
            boolean groundFloor = y - base < step;
            BlockState state;
            if (wall) {
                boolean floorBand = level == step - 1 && y > base + step;
                if (floorBand && !building.curtain()) {
                    state = facadeTrim;
                } else if (door && groundFloor && level <= 2) {
                    state = AIR;
                } else if (groundFloor && building.shopFront() && level >= 1 && level < step - 1) {
                    state = windowBlock;
                } else if (building.curtain()) {
                    // Façade rideau : verre partout, sauf les dalles d'étage et un poteau sur cinq.
                    state = level == 0 || Math.floorMod(blockX + blockZ, 5) == 0 ? wallBlock : windowBlock;
                } else {
                    boolean window = level >= 2 && level < step - 1 && Math.floorMod(blockX + blockZ, 3) != 0;
                    state = window ? windowBlock : wallBlock;
                }
                if (apocalypse) {
                    state = state == windowBlock ? Apocalypse.window(windowBlock, blockX, y, blockZ)
                            : state == wallBlock ? Apocalypse.wall(wallBlock, blockX, y, blockZ) : state;
                }
            } else if (ladder != null) {
                // Échelle continue du rez-de-chaussée au dernier étage (traverse les planchers).
                state = BuildingInterior.ladderState(ladder);
            } else if (partition && level > 0) {
                state = doorway && level <= 2 ? AIR : BuildingInterior.partition();
            } else if (apocalypse) {
                state = Apocalypse.interior(FLOOR, level, blockX, y, blockZ);
            } else if (level == 0) {
                state = FLOOR;
            } else {
                state = lamp && level == step - 1 ? LIGHT : AIR;
            }
            // Mobilier posé sur le plancher (pas sur les cloisons ni dans les trous).
            if (!wall && ladder == null && !partition && level == 1 && state.isAir() && y + 1 < top) {
                BlockState[] furniture = BuildingInterior.furniture(blockX, y, blockZ);
                if (furniture != null) {
                    state = furniture[0];
                    furnitureTop = furniture[1];
                }
            } else if (level == 2 && furnitureTop != null) {
                state = furnitureTop;
                furnitureTop = null;
            } else if (level != 1) {
                furnitureTop = null;
            }
            set(chunk, x, y, z, state);
        }

        // Toit : hauteur selon la distance au mur (0 sur le mur → monte vers le centre).
        int rise;
        int thickness = 1;
        switch (building.shape()) {
            case HIPPED -> rise = Math.min(inset, 8);
            case STEEP -> { rise = Math.min(inset * 2, 20); thickness = 2; }
            case MANSARD -> { rise = Math.min(inset * 2, step); thickness = 2; }
            default -> rise = 0;
        }
        if (top >= maxY) {
            return;
        }
        if (roofless) {
            // Étages supérieurs effondrés : pas de toit, un plancher à ciel ouvert.
            set(chunk, x, top, z, wall ? Apocalypse.wall(wallBlock, blockX, top, blockZ) : Apocalypse.interior(FLOOR, 0, blockX, top, blockZ));
            return;
        }
        if (apocalypse) {
            roofBlock = Apocalypse.roof(roofBlock, blockX, blockZ);
        }
        if (building.shape() == OsmCells.RoofShape.FLAT) {
            set(chunk, x, top, z, wall ? wallBlock : roofBlock);
            if (wall && top + 1 < maxY) {
                set(chunk, x, top + 1, z, wallBlock); // Acrotère.
            }
            return;
        }
        set(chunk, x, top, z, wall ? wallBlock : FLOOR);
        for (int y = top + 1; y < top + rise && y < maxY; y++) {
            set(chunk, x, y, z, y > top + rise - thickness ? roofBlock : AIR);
        }
        if (top + rise < maxY) {
            // Toit à quatre pans : escaliers orientés vers le faîte, bloc plein au sommet.
            BlockState cover = roofBlock;
            if (building.shape() == OsmCells.RoofShape.HIPPED && rise < 8 && !roofBlock.isAir()) {
                BlockState stair = BuildingInterior.roofStair(osm, blockX, blockZ, roofBlock);
                if (stair != null) {
                    cover = stair;
                }
            }
            set(chunk, x, top + Math.max(rise, 1), z, cover);
        }
    }

    /** Essence selon le climat ; les avenues tempérées ont surtout de grands arbres (type platane). */
    private static ResourceKey<Feature> species(EarthTerrain.Zone zone, int kind, long hash) {
        int roll = (int) Math.floorMod(hash, 100);
        switch (zone) {
            case BOREAL, COLD_TAIGA, TUNDRA, ICE_CAP -> {
                return roll < 70 ? TreeFeatures.SPRUCE : TreeFeatures.BIRCH;
            }
            case TROPICAL, MONSOON -> {
                return roll < 60 ? TreeFeatures.JUNGLE_TREE_NO_VINE : TreeFeatures.ACACIA;
            }
            case DESERT, SAVANNA, STEPPE -> {
                return roll < 60 ? TreeFeatures.ACACIA : TreeFeatures.OAK;
            }
            default -> {
            }
        }
        if (kind == UrbanTrees.AVENUE) {
            return roll < 65 ? TreeFeatures.FANCY_OAK : TreeFeatures.OAK;
        }
        return roll < 40 ? TreeFeatures.OAK : roll < 70 ? TreeFeatures.FANCY_OAK : roll < 90 ? TreeFeatures.BIRCH : TreeFeatures.DARK_OAK;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (!osm) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        OsmCells.Cell cell = terrain.osm().cellAt(pos.getMinBlockX(), pos.getMinBlockZ());
        HolderLookup.RegistryLookup<Feature> features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int blockX = pos.getMinBlockX() + x;
                int blockZ = pos.getMinBlockZ() + z;
                int kind = UrbanTrees.kind(cell, blockX, blockZ, apocalypse);
                if (kind == UrbanTrees.NONE || cell.surface(blockX, blockZ) == OsmCells.WATER) {
                    continue;
                }
                int ground = terrain.surfaceY(blockX, blockZ);
                if (ground < EarthTerrain.SEA_LEVEL) {
                    continue;
                }
                BlockState soil = level.getBlockState(below.set(blockX, ground, blockZ));
                BlockPos trunk = new BlockPos(blockX, ground + 1, blockZ);
                if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT)) || !level.getBlockState(trunk).canBeReplaced()) {
                    continue;
                }
                long hash = UrbanTrees.mix(blockX * 0x9E3779B97F4A7C15L ^ blockZ * 0xC2B2AE3D27D4EB4FL);
                RandomSource random = RandomSource.create(hash);
                features.get(species(terrain.zone(blockX, blockZ, terrain.elevation(blockX, blockZ)), kind, hash >>> 8))
                        .ifPresent(feature -> feature.value().place(level, this, random, trunk));
            }
        }
        if (apocalypse) {
            Wasteland.populate(level, chunk, this, terrain, cell);
        }
    }

    /** Bord de chaussée (route touchant un terrain libre), environ un point tous les 23 blocs. */
    private static boolean isLampSpot(OsmCells.Cell osm, int x, int z) {
        byte code = osm.surface(x, z);
        if (code != OsmCells.ROAD_MAJOR && code != OsmCells.ROAD_MINOR || Math.floorMod(x * 7 + z * 13, 23) != 0
                || osm.deck(x, z) != OsmCells.NO_LEVEL) {
            return false;
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            byte next = osm.surface(x + d[0], z + d[1]);
            if ((next == OsmCells.NONE || next == OsmCells.FOOTWAY) && osm.building(x + d[0], z + d[1]) == null) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si un voisin direct est hors bâtiment et hors eau (une porte y mène quelque part). */
    private static boolean facesOutside(OsmCells.Cell osm, int x, int z) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            if (osm.building(x + d[0], z + d[1]) == null && osm.surface(x + d[0], z + d[1]) != OsmCells.WATER) {
                return true;
            }
        }
        return false;
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x, SectionPos.sectionRelative(y), z, state, false);
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state,
                                 StructureManager structureManager, ChunkAccess chunk,
                                 StructureTemplateManager templateManager, ResourceKey<Level> level) {
        // Pas de villages ni de temples vanilla au milieu des vraies villes.
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
