package com.terracraft.geo.world;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aménagement des bâtiments : échelles dans les coins intérieurs (accès à tous les étages),
 * cloisons qui découpent des pièces avec des portes, mobilier, et escaliers de toit orientés.
 * Tout est déduit de la grille de la cellule OSM, sans état : chaque colonne se décide seule.
 */
final class BuildingInterior {
    private BuildingInterior() {
    }

    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int ROOM = 7;
    private static final BlockState PARTITION = Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
    private static final Map<Block, BlockState> STAIRS = new ConcurrentHashMap<>();

    /**
     * Coin intérieur (colonne juste à l'intérieur, touchant le mur sur deux côtés
     * perpendiculaires) : on y pose une échelle accrochée à l'un des murs. Renvoie le sens de
     * l'échelle, ou null.
     */
    static @Nullable Direction ladder(OsmCells.Cell osm, int x, int z) {
        if (osm.inset(x, z) != 1) {
            return null;
        }
        Direction first = null;
        int walls = 0;
        boolean northSouth = false;
        boolean eastWest = false;
        for (Direction d : SIDES) {
            if (osm.isWall(x + d.getStepX(), z + d.getStepZ())) {
                walls++;
                if (first == null) {
                    first = d;
                }
                northSouth |= d.getAxis() == Direction.Axis.Z;
                eastWest |= d.getAxis() == Direction.Axis.X;
            }
        }
        // Un coin sur deux (en moyenne deux échelles par maison rectangulaire). L'échelle
        // « regarde » à l'opposé du mur qui la porte.
        boolean corner = walls >= 2 && northSouth && eastWest;
        return corner && Math.floorMod(Apocalypse.hash(x, z, 271), 2) == 0 ? first.getOpposite() : null;
    }

    static BlockState ladderState(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
    }

    /** Cloison intérieure (grille de 7 blocs), sauf près des murs et des échelles. */
    static boolean isPartition(OsmCells.Cell osm, int x, int z) {
        return osm.inset(x, z) >= 2 && (Math.floorMod(x, ROOM) == 0 || Math.floorMod(z, ROOM) == 0);
    }

    /** Passage de porte dans une cloison (2 blocs de haut). */
    static boolean isDoorway(int x, int z) {
        return Math.floorMod(x * 3 + z * 5, ROOM) <= 1;
    }

    static BlockState partition() {
        return PARTITION;
    }

    /**
     * Meuble posé sur un plancher (environ 2 % des cases libres) : {bloc au sol, bloc au-dessus}
     * ou null. Une table est faite d'une barrière surmontée d'une plaque de pression.
     */
    static BlockState @Nullable [] furniture(int x, int y, int z) {
        int roll = Apocalypse.roll(x * 13L + y, z, 241);
        // Un peu plus de mobilier donne des intérieurs vivants sans remplir les pièces.
        if (roll >= 5) {
            return null;
        }
        int kind = Math.floorMod((int) Apocalypse.hash(x, z * 7L + y, 251), 7);
        return switch (kind) {
            case 0 -> new BlockState[]{Blocks.BOOKSHELF.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState()};
            case 1 -> new BlockState[]{Blocks.CRAFTING_TABLE.defaultBlockState(), null};
            case 2 -> new BlockState[]{Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_PRESSURE_PLATE.defaultBlockState()};
            case 3 -> new BlockState[]{Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, SIDES[Math.floorMod(x + z, 4)]), null};
            case 4 -> new BlockState[]{Blocks.CAULDRON.defaultBlockState(), null};
            case 5 -> new BlockState[]{Blocks.POTTED_FERN.defaultBlockState(), null};
            default -> new BlockState[]{Blocks.LOOM.defaultBlockState(), null};
        };
    }

    /**
     * Escalier de toit orienté vers le faîte (côté où la distance au mur augmente), ou null
     * au sommet (on y garde un bloc plein).
     */
    static @Nullable BlockState roofStair(OsmCells.Cell osm, int x, int z, BlockState roof) {
        int inset = osm.inset(x, z);
        for (Direction d : SIDES) {
            if (osm.building(x + d.getStepX(), z + d.getStepZ()) == osm.building(x, z)
                    && osm.inset(x + d.getStepX(), z + d.getStepZ()) > inset) {
                BlockState stairs = stairsFor(roof);
                return stairs == null ? null : stairs.setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM);
            }
        }
        return null;
    }

    /** Escalier assorti au matériau du toit (minecraft:<bloc>_stairs, ou équivalent). */
    private static @Nullable BlockState stairsFor(BlockState roof) {
        return STAIRS.computeIfAbsent(roof.getBlock(), block -> {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String path = id.getPath();
            String candidate = switch (path) {
                case "deepslate_tiles" -> "deepslate_tile_stairs";
                case "bricks", "terracotta", "red_terracotta", "brown_terracotta", "orange_terracotta" -> "brick_stairs";
                case "white_concrete", "light_gray_concrete", "quartz_block" -> "quartz_stairs";
                case "gray_concrete" -> "cobbled_deepslate_stairs";
                case "smooth_stone" -> "stone_stairs";
                default -> path.endsWith("s") ? path.substring(0, path.length() - 1) + "_stairs" : path + "_stairs";
            };
            return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(candidate))
                    .map(Block::defaultBlockState)
                    .orElse(Blocks.STONE_BRICK_STAIRS.defaultBlockState());
        });
    }
}
