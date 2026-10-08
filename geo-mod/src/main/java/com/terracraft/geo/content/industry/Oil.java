package com.terracraft.geo.content.industry;

/**
 * Gisements de pétrole de la Terre : déterministes (un au plus par carré de 96 blocs, 30 % des carrés), rayon de
 * 28 blocs autour de leur centre, richesse 1 à 3. Signalés en surface par des flaques dans les chunks neufs, et
 * repérables partout avec le détecteur de pétrole.
 */
public final class Oil {
    public static final int CELL = 96;
    public static final int RADIUS = 28;

    public record Deposit(int x, int z, int richness) {
    }

    private Oil() {
    }

    private static long hash(long a, long b) {
        long h = a * 0x632BE59BD9B4E019L ^ b * 0x9E3779B97F4A7C15L ^ 0x1D8E4E27C47D124FL;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        return h ^ h >>> 32;
    }

    /** Gisement du carré contenant (x, z), ou null. */
    public static Deposit deposit(int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        long h = hash(cx, cz);
        if (Math.floorMod(h, 100) >= 30) {
            return null;
        }
        int margin = RADIUS + 4;
        return new Deposit(cx * CELL + margin + (int) Math.floorMod(h >>> 8, CELL - 2 * margin),
                cz * CELL + margin + (int) Math.floorMod(h >>> 24, CELL - 2 * margin), 1 + (int) Math.floorMod(h >>> 40, 3));
    }

    /** Richesse du sous-sol en (x, z) : 0 hors gisement, sinon 1 à 3. */
    public static int richness(int x, int z) {
        Deposit d = deposit(x, z);
        return d != null && Math.hypot(d.x - x, d.z - z) <= RADIUS ? d.richness : 0;
    }

    /** Gisement le plus proche (carrés voisins compris), ou null à plus de ~200 blocs. */
    public static Deposit nearest(int x, int z) {
        Deposit best = null;
        double distance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Deposit d = deposit(x + dx * CELL, z + dz * CELL);
                if (d != null && Math.hypot(d.x - x, d.z - z) < distance) {
                    distance = Math.hypot(d.x - x, d.z - z);
                    best = d;
                }
            }
        }
        return best;
    }
}
