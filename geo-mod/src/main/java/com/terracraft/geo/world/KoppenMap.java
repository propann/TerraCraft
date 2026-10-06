package com.terracraft.geo.world;

import com.terracraft.geo.GeoMod;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Climats réels : classification de Köppen-Geiger (Beck et al. 2023, période 1991-2020,
 * 0,1°, licence CC BY 4.0), embarquée dans le mod (assets/terracraft_geo/koppen_0p1.bin.gz,
 * générée par tools/make_koppen.py). Classes 1-30 ; 0 = océan ou hors carte.
 */
public final class KoppenMap {
    public static final String ATTRIBUTION = "Climats : Beck et al. (2023), Köppen-Geiger 1991-2020, CC BY 4.0";

    public static final int AF = 1, AM = 2, AW = 3, BWH = 4, BWK = 5, BSH = 6, BSK = 7, CSA = 8, CSB = 9, CSC = 10,
            CWA = 11, CWB = 12, CWC = 13, CFA = 14, CFB = 15, CFC = 16, DSA = 17, DSB = 18, DSC = 19, DSD = 20,
            DWA = 21, DWB = 22, DWC = 23, DWD = 24, DFA = 25, DFB = 26, DFC = 27, DFD = 28, ET = 29, EF = 30;

    private static volatile KoppenMap instance;

    private final int width;
    private final int height;
    private final byte[] grid;

    private KoppenMap(int width, int height, byte[] grid) {
        this.width = width;
        this.height = height;
        this.grid = grid;
    }

    public static KoppenMap get() {
        KoppenMap map = instance;
        if (map == null) {
            synchronized (KoppenMap.class) {
                map = instance;
                if (map == null) {
                    map = load();
                    instance = map;
                }
            }
        }
        return map;
    }

    private static KoppenMap load() {
        try (InputStream raw = KoppenMap.class.getResourceAsStream("/assets/terracraft_geo/koppen_0p1.bin.gz")) {
            if (raw == null) {
                throw new IOException("ressource introuvable");
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            int width = in.readInt();
            int height = in.readInt();
            byte[] grid = new byte[width * height];
            in.readFully(grid);
            GeoMod.LOGGER.info("Carte des climats Köppen chargée ({}×{}).", width, height);
            return new KoppenMap(width, height, grid);
        } catch (IOException e) {
            GeoMod.LOGGER.error("Carte des climats indisponible : climats estimés par la latitude.", e);
            return new KoppenMap(1, 1, new byte[1]);
        }
    }

    /**
     * Classe climatique (1-30) au point, ou 0. Sur la côte, la case peut être marquée « océan »
     * alors que le relief est émergé : on prend alors la classe terrestre la plus proche.
     */
    public int classAt(double latitude, double longitude) {
        if (width <= 1) {
            return 0;
        }
        int x = Math.floorMod((int) Math.floor((longitude + 180) / 360 * width), width);
        int y = Math.max(0, Math.min(height - 1, (int) Math.floor((90 - latitude) / 180 * height)));
        int value = grid[y * width + x];
        if (value != 0) {
            return value;
        }
        for (int r = 1; r <= 3; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) {
                        continue;
                    }
                    int v = grid[yy * width + Math.floorMod(x + dx, width)];
                    if (v != 0) {
                        return v;
                    }
                }
            }
        }
        return 0;
    }
}
