package com.terracraft.geo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Règles de vol communes aux fusées et aux vaisseaux construits : routes permises, navigation martienne, point
 * d'arrivée. Une seule source de vérité pour le décollage et pour la carte des étoiles.
 */
public final class SpaceTravel {
    /** Rayon autour d'une balise où un engin est considéré amarré à la station. */
    public static final int DOCK_RADIUS = 16;

    private SpaceTravel() {
    }

    /**
     * Depuis la Terre on ne va qu'en orbite terrestre (la station orbitale est la porte de l'espace) ; pour aller plus
     * loin, l'engin doit partir amarré à une station ou une base. Rentrer sur Terre ou en orbite terrestre reste
     * toujours possible. Renvoie le problème, ou null si la route est permise.
     */
    public static @Nullable String routeProblem(ServerPlayer player, ServerLevel level, BlockPos pos, byte to) {
        if (player.isCreative() || to == Space.EARTH || to == Space.ORBIT_ID) {
            return null;
        }
        if (Space.id(level) == Space.EARTH) {
            return "Depuis la Terre, cap sur l'orbite terrestre : construis d'abord ta station orbitale (kit de station).";
        }
        if (!Stations.get().dockedNear(level, pos, DOCK_RADIUS)) {
            return "Les voyages lointains partent d'une station : amarre-toi à une balise de station.";
        }
        return null;
    }

    /** Mars, son orbite et les astéroïdes exigent la navigation martienne (sauf en créatif). */
    public static boolean needsMarsNavigation(ServerPlayer player, byte to, boolean installed) {
        return Space.requiresMoon(to) && !player.isCreative() && !installed;
    }

    /**
     * Arrivée dans {@code target} : balise de la station du pilote (ou de sa ville) si elle existe, sinon quai orbital
     * construit au besoin, sinon le sol. {@code groundY} est le niveau du sol ou de l'aire d'atterrissage (bloc).
     */
    public record Arrival(double x, double z, int groundY, @Nullable BlockPos beacon) {
    }

    public static Arrival arrival(ServerLevel target, byte destination, @Nullable ServerPlayer pilot, double x, double z) {
        BlockPos beacon = pilot != null ? Stations.get().landing(pilot, target) : null;
        if (beacon != null) {
            target.getChunk(beacon.getX() >> 4, beacon.getZ() >> 4);
            return new Arrival(beacon.getX() + 0.5, beacon.getZ() + 0.5, beacon.getY(), beacon);
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (Space.isOrbitId(destination)) {
            Space.buildDock(target, bx, bz);
            return new Arrival(x, z, Space.DOCK_Y, null);
        }
        target.getChunk(bx >> 4, bz >> 4);
        return new Arrival(x, z, target.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1, null);
    }
}
