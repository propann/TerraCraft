package com.terracraft.geo.world;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modèle de terrain réel : altitude → hauteur Minecraft et zone climatique simplifiée.
 *
 * <p>Ce code ne dépend pas des classes Minecraft pour rester testable et partagé entre le
 * générateur de chunks et la source de biomes.
 *
 * <h2>Hauteurs</h2>
 * Le niveau de la mer réel correspond à {@link #SEA_LEVEL} (eau jusqu'à y=62). L'altitude
 * est convertie en blocs avec la même échelle que l'horizontale Mercator
 * ({@code scale / cos(latitude)} blocs par mètre), linéairement jusqu'à
 * {@link #LINEAR_LIMIT} blocs (≈ 800 m réels en France), puis compressée en racine carrée pour
 * que l'Everest tienne sous la limite de la dimension terracraft_geo:earth (y=1471). Les fonds marins sont compressés de la même façon.
 */
public final class EarthTerrain {
    public static final int SEA_LEVEL = 63;
    public static final int MIN_Y = -64;
    public static final int MAX_SURFACE_Y = 1440;
    public static final int MIN_FLOOR_Y = -54;
    static final double LINEAR_LIMIT = 1200.0;
    static final double DEPTH_LINEAR_LIMIT = 20.0;
    /** Taille de la grille du bruit de variété des biomes, en blocs. */
    private static final int VARIETY_CELL = 1536;

    public enum Zone {
        DEEP_OCEAN, OCEAN, SHORE,
        ICE_CAP, TUNDRA, BOREAL, TEMPERATE, TEMPERATE_FOREST, MEDITERRANEAN,
        DESERT, SAVANNA, TROPICAL, MONSOON, STEPPE, COLD_TAIGA,
        // Les zones d'altitude restent en dernier (GeoBiomeSource compare les ordinaux).
        ALPINE_MEADOW, ROCKY_PEAK, SNOWY_PEAK
    }

    private static final Map<Double, EarthTerrain> SHARED = new ConcurrentHashMap<>();

    /** Zoom maximal où les tuiles Terrarium contiennent encore la bathymétrie (ETOPO1). */
    static final int BATHYMETRY_ZOOM = 10;

    private final double scale;
    private final ElevationTiles tiles;
    private final ElevationTiles bathymetry;
    private final OsmCells osm;
    private final double worldSize;

    /**
     * Instance partagée par échelle : le codec du générateur peut être décodé plusieurs fois
     * (préréglage, level.dat), mais le cache de tuiles doit rester unique.
     */
    public static EarthTerrain shared(double scale, Path cacheDir) {
        return SHARED.computeIfAbsent(scale, s -> new EarthTerrain(s, cacheDir));
    }

    public EarthTerrain(double scale, Path cacheDir) {
        this.scale = scale;
        int zoom = zoomFor(scale);
        this.tiles = new ElevationTiles(zoom, ElevationTiles.DEFAULT_URL, cacheDir.resolve("terrarium"));
        this.bathymetry = zoom > BATHYMETRY_ZOOM
                ? new ElevationTiles(BATHYMETRY_ZOOM, ElevationTiles.DEFAULT_URL, cacheDir.resolve("terrarium"))
                : tiles;
        this.worldSize = WebMercator.worldSize(scale);
        this.osm = new OsmCells(this, cacheDir.resolve("osm").resolve("scale-" + scale));
    }

    /** Zoom de tuile visant environ 20 blocs par pixel (les données SRTM font ~30 m). */
    static int zoomFor(double scale) {
        double blocksPerTile = WebMercator.worldSize(scale) / 256.0;
        int zoom = (int) Math.round(Math.log(blocksPerTile / 20.0) / Math.log(2.0));
        return Math.max(0, Math.min(15, zoom));
    }

    /** Routes, bâtiments, eau et occupation du sol OpenStreetMap. */
    public OsmCells osm() {
        return osm;
    }

    public double scale() {
        return scale;
    }

    public double latitudeAt(double blockZ) {
        return WebMercator.latitudeAt(blockZ, scale);
    }

    public double longitudeAt(double blockX) {
        return WebMercator.longitudeAt(blockX, scale);
    }

    /** Altitude réelle en mètres (négative en mer), interpolée en bicubique. */
    public double elevation(double blockX, double blockZ) {
        int pixels = tiles.worldPixels();
        double px = (blockX / worldSize + 0.5) * pixels - 0.5;
        double py = (blockZ / worldSize + 0.5) * pixels - 0.5;
        int x0 = (int) Math.floor(px);
        int y0 = (int) Math.floor(py);
        double fx = px - x0;
        double fy = py - y0;
        double[] rows = new double[4];
        for (int j = 0; j < 4; j++) {
            double p0 = sample(x0 - 1, y0 - 1 + j);
            double p1 = sample(x0, y0 - 1 + j);
            double p2 = sample(x0 + 1, y0 - 1 + j);
            double p3 = sample(x0 + 2, y0 - 1 + j);
            rows[j] = cubic(p0, p1, p2, p3, fx);
        }
        return cubic(rows[0], rows[1], rows[2], rows[3], fy);
    }

    private double sample(int gx, int gy) {
        float value = tiles.pixel(gx, gy);
        if (value == 0f && bathymetry != tiles) {
            // Au-delà du zoom 10, la pleine mer est codée 0 : on reprend la bathymétrie.
            double depth = bathymetry(gx, gy);
            if (depth < 0) {
                return depth;
            }
        }
        // Tuile indisponible : terrain plat juste au-dessus de la mer plutôt qu'un crash.
        return Float.isNaN(value) ? 1.0 : value;
    }

    /** Bathymétrie bilinéaire au zoom {@link #BATHYMETRY_ZOOM}, pour un pixel du zoom fin. */
    private double bathymetry(int gx, int gy) {
        double factor = (double) bathymetry.worldPixels() / tiles.worldPixels();
        double px = (gx + 0.5) * factor - 0.5;
        double py = (gy + 0.5) * factor - 0.5;
        int x0 = (int) Math.floor(px);
        int y0 = (int) Math.floor(py);
        double fx = px - x0;
        double fy = py - y0;
        double a = bathymetry.pixel(x0, y0);
        double b = bathymetry.pixel(x0 + 1, y0);
        double c = bathymetry.pixel(x0, y0 + 1);
        double d = bathymetry.pixel(x0 + 1, y0 + 1);
        double value = (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
        return Double.isNaN(value) ? 0 : value;
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** Blocs par mètre réel à cette latitude (facteur d'échelle Mercator). */
    public double blocksPerMetre(double latitude) {
        return scale / Math.cos(Math.toRadians(latitude));
    }

    /** Y du bloc de surface (sol ou fond marin) pour une altitude réelle. */
    public int surfaceY(double elevationMetres, double latitude) {
        double h = elevationMetres * blocksPerMetre(latitude);
        if (elevationMetres > 0) {
            int y = (int) Math.floor(SEA_LEVEL + compress(h, LINEAR_LIMIT));
            return Math.max(SEA_LEVEL, Math.min(MAX_SURFACE_Y, y));
        }
        int y = (int) Math.floor(SEA_LEVEL - 1 - compress(-h, DEPTH_LINEAR_LIMIT));
        return Math.max(MIN_FLOOR_Y, Math.min(SEA_LEVEL - 2, y));
    }

    public int surfaceY(int blockX, int blockZ) {
        return surfaceY(elevation(blockX, blockZ), latitudeAt(blockZ));
    }

    /** Identité jusqu'à {@code limit}, puis croissance en racine carrée avec une pente continue. */
    static double compress(double value, double limit) {
        if (value <= limit) {
            return value;
        }
        return limit + 2.0 * (Math.sqrt(1.0 + value - limit) - 1.0);
    }

    /** Altitude réelle au-dessus de laquelle la neige persiste, selon la latitude. */
    public static double snowLine(double latitude) {
        return 3000.0 + 2000.0 * Math.cos(Math.toRadians(2.0 * Math.abs(latitude)));
    }

    /**
     * Zone climatique : climat réel de Köppen-Geiger ({@link KoppenMap}), corrigé par
     * l'altitude (alpages, sommets) et varié par un bruit à grande échelle. Sans carte, on
     * retombe sur une estimation par la latitude.
     */
    public Zone zone(int blockX, int blockZ, double elevationMetres) {
        double latitude = latitudeAt(blockZ);
        double absLat = Math.abs(latitude);
        if (elevationMetres <= 0) {
            return elevationMetres < -200 ? Zone.DEEP_OCEAN : Zone.OCEAN;
        }
        if (elevationMetres < 1.0) {
            return Zone.SHORE;
        }
        double snowLine = snowLine(latitude);
        if (elevationMetres > snowLine) {
            return Zone.SNOWY_PEAK;
        }
        if (elevationMetres > snowLine - 500) {
            return Zone.ROCKY_PEAK;
        }
        if (elevationMetres > snowLine - 1200) {
            return Zone.ALPINE_MEADOW;
        }
        double variety = variety(blockX, blockZ);
        Zone climate = fromKoppen(KoppenMap.get().classAt(latitude, longitudeAt(blockX)), variety);
        if (climate != null) {
            return climate;
        }
        if (absLat > 70) {
            return Zone.ICE_CAP;
        }
        if (absLat > 62) {
            return Zone.TUNDRA;
        }
        if (absLat > 50) {
            return variety < 0.25 ? Zone.TEMPERATE_FOREST : Zone.BOREAL;
        }
        if (absLat > 38) {
            return variety < 0.55 ? Zone.TEMPERATE : Zone.TEMPERATE_FOREST;
        }
        if (absLat > 30) {
            return variety < 0.6 ? Zone.MEDITERRANEAN : Zone.TEMPERATE;
        }
        if (absLat > 16) {
            return variety < 0.7 ? Zone.DESERT : Zone.SAVANNA;
        }
        if (absLat > 8) {
            return variety < 0.75 ? Zone.SAVANNA : Zone.TROPICAL;
        }
        return Zone.TROPICAL;
    }

    /** Classe de Köppen → zone de paysage, ou null si la classe est inconnue (océan). */
    static Zone fromKoppen(int koppen, double variety) {
        return switch (koppen) {
            case KoppenMap.AF -> Zone.TROPICAL;
            case KoppenMap.AM -> Zone.MONSOON;
            case KoppenMap.AW, KoppenMap.BSH -> Zone.SAVANNA;
            case KoppenMap.BWH, KoppenMap.BWK -> Zone.DESERT;
            case KoppenMap.BSK -> Zone.STEPPE;
            case KoppenMap.CSA -> variety < 0.6 ? Zone.MEDITERRANEAN : Zone.TEMPERATE;
            case KoppenMap.CSB, KoppenMap.CSC -> variety < 0.5 ? Zone.TEMPERATE : Zone.TEMPERATE_FOREST;
            case KoppenMap.CFA, KoppenMap.CWA -> variety < 0.6 ? Zone.TEMPERATE_FOREST : Zone.TEMPERATE;
            case KoppenMap.CFB, KoppenMap.CWB -> variety < 0.55 ? Zone.TEMPERATE : Zone.TEMPERATE_FOREST;
            case KoppenMap.CFC, KoppenMap.CWC, KoppenMap.DSC, KoppenMap.DWC, KoppenMap.DFC -> Zone.BOREAL;
            case KoppenMap.DSA, KoppenMap.DSB, KoppenMap.DWA, KoppenMap.DWB, KoppenMap.DFA, KoppenMap.DFB ->
                    variety < 0.7 ? Zone.TEMPERATE_FOREST : Zone.BOREAL;
            case KoppenMap.DSD, KoppenMap.DWD, KoppenMap.DFD -> Zone.COLD_TAIGA;
            case KoppenMap.ET -> Zone.TUNDRA;
            case KoppenMap.EF -> Zone.ICE_CAP;
            default -> null;
        };
    }

    /** Bruit de valeur lissé dans [0, 1), stable pour une position. */
    static double variety(int blockX, int blockZ) {
        double gx = (double) blockX / VARIETY_CELL;
        double gz = (double) blockZ / VARIETY_CELL;
        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        double fx = smooth(gx - x0);
        double fz = smooth(gz - z0);
        double a = hash(x0, z0);
        double b = hash(x0 + 1, z0);
        double c = hash(x0, z0 + 1);
        double d = hash(x0 + 1, z0 + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Précharge le relief autour d'un point (rayon en blocs) avant d'y envoyer un joueur. */
    public CompletableFuture<Void> prefetch(double blockX, double blockZ, double radius) {
        int pixels = tiles.worldPixels();
        int minX = (int) Math.floor(((blockX - radius) / worldSize + 0.5) * pixels) - 2;
        int maxX = (int) Math.floor(((blockX + radius) / worldSize + 0.5) * pixels) + 2;
        int minY = (int) Math.floor(((blockZ - radius) / worldSize + 0.5) * pixels) - 2;
        int maxY = (int) Math.floor(((blockZ + radius) / worldSize + 0.5) * pixels) + 2;
        double factor = (double) bathymetry.worldPixels() / tiles.worldPixels();
        return CompletableFuture.allOf(
                tiles.prefetch(minX, minY, maxX, maxY),
                bathymetry.prefetch((int) (minX * factor) - 1, (int) (minY * factor) - 1,
                        (int) (maxX * factor) + 1, (int) (maxY * factor) + 1));
    }
}
