package com.terracraft.geo.world;

/** Emplacements des arbres urbains, calculés uniquement à partir des grilles OSM (sans Minecraft). */
final class UrbanTrees {
    private UrbanTrees() {
    }

    static final int NONE = 0;
    static final int PARK = 1;
    static final int MEADOW = 2;
    static final int AVENUE = 3;
    /** Mode apocalypse : arbre sauvage poussé dans une rue ou sur une place. */
    static final int WILD = 4;

    /**
     * Emplacements d'arbres déterministes : parcs (grille de 6 blocs décalée au hasard, 65 %),
     * pelouses et prairies (grille de 12 blocs, 25 %) et alignements d'avenue (environ tous les 8 blocs
     * sur le trottoir, en ligne parallèle aux grands axes).
     */
    static int kind(OsmCells.Cell osm, int x, int z) {
        return kind(osm, x, z, false);
    }

    static int kind(OsmCells.Cell osm, int x, int z, boolean apocalypse) {
        if (osm.building(x, z) != null || osm.deck(x, z) != OsmCells.NO_LEVEL) {
            return NONE;
        }
        byte surface = osm.surface(x, z);
        byte land = osm.land(x, z);
        if (surface == OsmCells.NONE && land == OsmCells.LAND_PARK && jittered(x, z, 6, apocalypse ? 85 : 65)) {
            return PARK;
        }
        if (surface == OsmCells.NONE && land == OsmCells.LAND_GRASS && jittered(x, z, 12, 25)) {
            return MEADOW;
        }
        if ((surface == OsmCells.NONE || surface == OsmCells.FOOTWAY) && Math.floorMod(x * 3 + z * 5, 8) == 0
                && avenueDistance(osm, x, z) == AVENUE_OFFSET) {
            return AVENUE;
        }
        if (apocalypse && surface != OsmCells.WATER && surface != OsmCells.RAIL && jittered(x + 3, z + 5, 9, 14)) {
            return WILD;
        }
        return NONE;
    }

    /** Distance (Tchebychev) entre l'alignement d'arbres et le bord de la chaussée. */
    static final int AVENUE_OFFSET = 3;

    /** Distance de Tchebychev à la grande route la plus proche, jusqu'à AVENUE_OFFSET (sinon -1). */
    private static int avenueDistance(OsmCells.Cell osm, int x, int z) {
        for (int d = 1; d <= AVENUE_OFFSET; d++) {
            for (int i = -d; i <= d; i++) {
                if (osm.surface(x + i, z - d) == OsmCells.ROAD_MAJOR || osm.surface(x + i, z + d) == OsmCells.ROAD_MAJOR
                        || osm.surface(x - d, z + i) == OsmCells.ROAD_MAJOR || osm.surface(x + d, z + i) == OsmCells.ROAD_MAJOR) {
                    return d;
                }
            }
        }
        return -1;
    }

    /** Un point au hasard par case de la grille, retenu avec la probabilité donnée (%). */
    static boolean jittered(int x, int z, int grid, int percent) {
        int cx = Math.floorDiv(x, grid);
        int cz = Math.floorDiv(z, grid);
        long h = mix(cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL);
        return x == cx * grid + (int) Math.floorMod(h, grid)
                && z == cz * grid + (int) Math.floorMod(h >>> 16, grid)
                && Math.floorMod(h >>> 32, 100) < percent;
    }

    static long mix(long h) {
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

}
