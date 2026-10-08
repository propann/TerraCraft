package com.terracraft.geo;

import java.util.List;

/** Registre central des corps célestes. Les dimensions sont ajoutées ici avant leur contenu. */
public final class SolarSystem {
    public record Body(byte id, String key, String displayName, boolean active) {}

    public static final Body EARTH = new Body((byte) 0, "earth", "la Terre", true);
    public static final Body MOON = new Body((byte) 1, "moon", "la Lune", true);
    public static final Body EARTH_ORBIT = new Body((byte) 2, "earth_orbit", "l'orbite terrestre", true);
    public static final Body MARS = new Body((byte) 3, "mars", "Mars", true);
    public static final Body MARS_ORBIT = new Body((byte) 4, "mars_orbit", "l'orbite de Mars", true);
    public static final Body MERCURY = new Body((byte) 5, "mercury", "Mercure", false);
    public static final Body MERCURY_ORBIT = new Body((byte) 6, "mercury_orbit", "l'orbite de Mercure", false);
    public static final Body VENUS = new Body((byte) 7, "venus", "Vénus", false);
    public static final Body VENUS_ORBIT = new Body((byte) 8, "venus_orbit", "l'orbite de Vénus", false);
    public static final Body ASTEROIDS = new Body((byte) 9, "asteroids", "la ceinture d'astéroïdes", true);
    public static final Body ASTEROIDS_ORBIT = new Body((byte) 10, "asteroids_orbit", "l'orbite des astéroïdes", false);
    public static final Body MOON_ORBIT = new Body((byte) 11, "moon_orbit", "l'orbite lunaire", true);

    /** Ordre de choix des destinations dans la fusée : du plus proche au plus lointain. */
    private static final List<Body> ACTIVE = List.of(EARTH, EARTH_ORBIT, MOON_ORBIT, MOON, MARS_ORBIT, MARS, ASTEROIDS);
    private static final List<Body> ALL = List.of(EARTH, MOON, EARTH_ORBIT, MARS, MARS_ORBIT, MOON_ORBIT,
            MERCURY, MERCURY_ORBIT, VENUS, VENUS_ORBIT, ASTEROIDS, ASTEROIDS_ORBIT);

    private SolarSystem() {}

    public static Body byId(byte id) {
        return ALL.stream().filter(body -> body.id() == id).findFirst().orElse(EARTH);
    }

    public static byte nextActive(byte current) {
        return nextActive(current, Byte.MIN_VALUE);
    }

    public static byte nextActive(byte current, byte skip) {
        int index = 0;
        for (int i = 0; i < ACTIVE.size(); i++) {
            if (ACTIVE.get(i).id() == current) {
                index = i;
                break;
            }
        }
        for (int step = 1; step <= ACTIVE.size(); step++) {
            byte next = ACTIVE.get((index + step) % ACTIVE.size()).id();
            if (next != skip) {
                return next;
            }
        }
        return ACTIVE.get(index).id();
    }

    public static List<Body> activeBodies() {
        return ACTIVE;
    }
}
