package com.terracraft.geo.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rapport de génération (/terracraft rapport) : bâtiments des cellules OSM déjà chargées autour d'un point, par
 * nombre d'étages et forme de toit, le plus haut, et les bâtiments suspects (hauteur des données fortement réduite)
 * avec leurs coordonnées, pour aller les voir. Ne télécharge rien : seules les cellules en mémoire comptent.
 */
public final class GenerationReport {
    private GenerationReport() {
    }

    public static List<String> around(EarthTerrain terrain, int x, int z, int radius) {
        OsmCells osm = terrain.osm();
        Set<Long> seen = new HashSet<>();
        Set<OsmCells.Cell> cells = new HashSet<>();
        int size = OsmCells.CELL_SIZE;
        for (int cx = Math.floorDiv(x - radius, size); cx <= Math.floorDiv(x + radius, size); cx++) {
            for (int cz = Math.floorDiv(z - radius, size); cz <= Math.floorDiv(z + radius, size); cz++) {
                OsmCells.Cell cell = osm.cellIfLoaded(cx * size, cz * size);
                if (cell != null) {
                    cells.add(cell);
                }
            }
        }
        int[] floors = new int[5]; // 1 · 2-3 · 4-6 · 7-10 · 11+
        Map<OsmCells.RoofShape, Integer> roofs = new HashMap<>();
        int total = 0;
        int shops = 0;
        int towers = 0;
        int blocks = 0;
        OsmCells.Building tallest = null;
        List<OsmCells.Suspect> suspects = new ArrayList<>();
        for (OsmCells.Cell cell : cells) {
            for (OsmCells.Building building : cell.buildings()) {
                if (!seen.add(building.seed())) {
                    continue; // Un bâtiment à cheval sur deux cellules n'est compté qu'une fois.
                }
                total++;
                int n = Math.max(1, (building.topY() - building.baseY()) / building.floorStep());
                floors[n == 1 ? 0 : n <= 3 ? 1 : n <= 6 ? 2 : n <= 10 ? 3 : 4]++;
                roofs.merge(building.shape(), 1, Integer::sum);
                shops += building.shopFront() ? 1 : 0;
                towers += building.curtain() ? 1 : 0;
                blocks += building.block() ? 1 : 0;
                if (tallest == null || building.topY() - building.baseY() > tallest.topY() - tallest.baseY()) {
                    tallest = building;
                }
            }
            for (OsmCells.Suspect suspect : cell.suspects()) {
                if (Math.abs(suspect.x() - x) <= radius && Math.abs(suspect.z() - z) <= radius) {
                    suspects.add(suspect);
                }
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("cellules chargées " + cells.size() + " · bâtiments " + total + " · îlots (cour et lots) " + blocks
                + " · vitrines " + shops + " · tours vitrées " + towers);
        lines.add("étages : 1 → " + floors[0] + " · 2-3 → " + floors[1] + " · 4-6 → " + floors[2] + " · 7-10 → " + floors[3]
                + " · 11+ → " + floors[4]);
        lines.add("toits : " + roofs);
        if (tallest != null) {
            lines.add("plus haut : " + (tallest.topY() - tallest.baseY()) / tallest.floorStep() + " étages (y " + tallest.baseY()
                    + " → " + tallest.topY() + ")");
        }
        lines.add("suspects (hauteur des données réduite) : " + suspects.size());
        suspects.stream().limit(10).forEach(s -> lines.add("  " + s.type() + " en " + s.x() + " " + s.z() + " : données "
                + s.dataMetres() + " m, gardé " + s.keptMetres() + " m"));
        return lines;
    }
}
