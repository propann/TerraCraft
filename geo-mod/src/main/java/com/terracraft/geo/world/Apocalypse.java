package com.terracraft.geo.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le monde a redémarré sans humains, il y a quelques décennies : bâtiments en ruine, vitres
 * brisées, lierre, routes fissurées envahies par l'herbe, voitures abandonnées, plus aucune
 * lumière. Les véhicules sont de vraies entités réparables et conduisibles, pas des blocs
 * décoratifs. Tout est déterministe (hachage de la position ou de l'identifiant OSM), donc
 * identique d'un chunk à l'autre et d'un redémarrage à l'autre.
 */
final class Apocalypse {
    private Apocalypse() {
    }

    enum Ruin { INTACT, BROKEN, RUBBLE }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState[] RUBBLE = {
            Blocks.GRAVEL.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState()};
    private static final BlockState[] CRACKS = {
            Blocks.COBBLESTONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
    static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    static final BlockState COBWEB = Blocks.COBWEB.defaultBlockState();
    static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();

    static long hash(long a, long b, long salt) {
        return UrbanTrees.mix(a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L);
    }

    static int roll(long a, long b, long salt) {
        return (int) Math.floorMod(hash(a, b, salt), 100);
    }

    /** État du bâtiment : 10 % en tas de gravats, 30 % décapités, le reste debout mais abîmé. */
    static Ruin ruin(OsmCells.Building building) {
        int r = (int) Math.floorMod(UrbanTrees.mix(building.seed()), 100);
        return r < 10 ? Ruin.RUBBLE : r < 40 ? Ruin.BROKEN : Ruin.INTACT;
    }

    /** Haut effectif des murs à cette colonne (sommets déchiquetés pour les bâtiments décapités). */
    static int effectiveTop(OsmCells.Building building, int x, int z) {
        int top = building.topY();
        if (ruin(building) != Ruin.BROKEN) {
            return top;
        }
        int floors = Math.max(1, (top - building.baseY()) / building.floorStep());
        int lost = 1 + (int) Math.floorMod(UrbanTrees.mix(building.seed() + 7), Math.max(1, floors / 2 + 1));
        int jag = (int) Math.floorMod(hash(x, z, 11), 4);
        return Math.max(building.baseY() + 2, top - lost * building.floorStep() - jag);
    }

    /** Hauteur du tas de gravats d'un bâtiment effondré. */
    static int rubbleHeight(int x, int z) {
        return 1 + (int) Math.floorMod(hash(x, z, 13), 4);
    }

    static BlockState rubble(int x, int y, int z) {
        return RUBBLE[(int) Math.floorMod(hash(x * 31L + y, z, 17), RUBBLE.length)];
    }

    /** Mur usé : trous (4 %), mousse (8 %), sinon le matériau d'origine. */
    static BlockState wall(BlockState original, int x, int y, int z) {
        int r = roll(x * 7L + y, z, 19);
        if (r < 4) {
            return AIR;
        }
        if (r < 12) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (original.is(Blocks.STONE_BRICKS) && r < 20) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        return original;
    }

    /** Vitre : 70 % brisées. */
    static BlockState window(BlockState glass, int x, int y, int z) {
        return roll(x, z * 13L + y, 23) < 70 ? AIR : glass;
    }

    /** Intérieur : planchers troués (12 %), toiles d'araignée et gravats occasionnels. */
    static BlockState interior(BlockState floor, int level, int x, int y, int z) {
        int r = roll(x * 3L + y, z, 29);
        if (level == 0) {
            return r < 12 ? AIR : floor;
        }
        if (level == 1 && r < 3) {
            return rubble(x, y, z);
        }
        return r > 98 ? COBWEB : AIR;
    }

    /** Revêtement de route fissuré : 15 % cailloux/terre, 7 % herbe qui perce. */
    static BlockState road(BlockState road, int x, int z) {
        int r = roll(x, z, 31);
        if (r < 15) {
            return CRACKS[(int) Math.floorMod(hash(x, z, 37), CRACKS.length)];
        }
        return r < 22 ? GRASS : road;
    }

    /** Lampadaire mort : poteau parfois cassé (1 à 5 blocs), plus de lanterne. */
    static int lampHeight(int x, int z) {
        return roll(x, z, 41) < 30 ? 1 + (int) Math.floorMod(hash(x, z, 43), 3) : 5;
    }

    /** Lierre sur la face d'un mur voisin : 35 % des colonnes, longueur variable. */
    static int vineLength(int x, int z, int direction, int maxHeight) {
        if (roll(x * 5L + direction, z, 47) >= 35 || maxHeight <= 1) {
            return 0;
        }
        return 1 + (int) Math.floorMod(hash(x, z, 53 + direction), maxHeight);
    }

    static BlockState vine(int dx, int dz) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        if (dx > 0) {
            return vine.setValue(VineBlock.EAST, true);
        }
        if (dx < 0) {
            return vine.setValue(VineBlock.WEST, true);
        }
        return dz > 0 ? vine.setValue(VineBlock.SOUTH, true) : vine.setValue(VineBlock.NORTH, true);
    }

    /** Variante moussue d'un bloc de toit plat. */
    static BlockState roof(BlockState roof, int x, int z) {
        int r = roll(x, z, 61);
        return r < 8 ? AIR : r < 20 ? MOSS : roof;
    }

    static boolean isBlock(BlockState state, Block block) {
        return state.is(block);
    }
}
