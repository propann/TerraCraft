package com.terracraft.geo.world;

/**
 * Projection Web Mercator (EPSG:3857), la même que les tuiles OSM et les tuiles de relief.
 *
 * <p>Le monde Minecraft est une grande carte Mercator centrée sur (0°, 0°) : le bloc (0, 0)
 * est l'« Null Island » dans le golfe de Guinée, l'est est vers +X et le nord vers -Z.
 * À l'échelle {@code s}, un bloc vaut {@code cos(latitude) / s} mètre. Le préréglage du monde utilise
 * {@code s = cos(48,86°) = 0,6583} : un bloc = un mètre à Paris (Madrid 1,16 m, Berlin 0,93 m, Oslo 0,76 m).
 * C'est une seule carte continue pour toute la planète : chaque lieu réel a une seule position en blocs.
 * Comme les tuiles sont aussi en Mercator, un pixel de tuile correspond à un nombre
 * constant de blocs : aucune reprojection n'est nécessaire pour lire le relief.
 */
public final class WebMercator {
    public static final double EARTH_CIRCUMFERENCE_M = 40_075_016.686;
    public static final double MAX_LATITUDE = 85.05112878;

    private WebMercator() {
    }

    /** Coordonnée X normalisée [0, 1] depuis la longitude. */
    public static double u(double longitude) {
        return (longitude + 180.0) / 360.0;
    }

    /** Coordonnée Y normalisée [0, 1] (0 = nord) depuis la latitude. */
    public static double v(double latitude) {
        double lat = Math.toRadians(clampLatitude(latitude));
        return (1.0 - Math.log(Math.tan(lat) + 1.0 / Math.cos(lat)) / Math.PI) / 2.0;
    }

    public static double longitude(double u) {
        return u * 360.0 - 180.0;
    }

    public static double latitude(double v) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * v))));
    }

    public static double clampLatitude(double latitude) {
        return Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitude));
    }

    /** Largeur du monde en blocs pour une échelle donnée (blocs par mètre à l'équateur). */
    public static double worldSize(double scale) {
        return EARTH_CIRCUMFERENCE_M * scale;
    }

    public static double blockX(double longitude, double scale) {
        return (u(longitude) - 0.5) * worldSize(scale);
    }

    public static double blockZ(double latitude, double scale) {
        return (v(latitude) - 0.5) * worldSize(scale);
    }

    public static double longitudeAt(double blockX, double scale) {
        return longitude(blockX / worldSize(scale) + 0.5);
    }

    public static double latitudeAt(double blockZ, double scale) {
        return latitude(blockZ / worldSize(scale) + 0.5);
    }
}
