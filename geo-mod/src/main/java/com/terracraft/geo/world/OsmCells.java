package com.terracraft.geo.world;

import com.google.gson.JsonObject;
import com.terracraft.geo.GeoMod;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Données OpenStreetMap découpées en cellules carrées de {@link #CELL_SIZE} blocs.
 *
 * <p>Les données viennent des tuiles vectorielles OpenFreeMap (schéma OpenMapTiles, zoom 14),
 * mises en cache disque par {@link VectorTiles}. Chaque cellule est « rastérisée » en grilles d'un octet par bloc : revêtement (route, eau…),
 * occupation du sol, bâtiment, niveau d'eau et tablier de pont. Le générateur de chunks lit
 * ensuite ces grilles sans calcul géométrique.
 */
public final class OsmCells {
    public static final int CELL_SIZE = 512;
    /** Marge rastérisée autour de la cellule pour connaître les voisins (murs, berges). */
    private static final int MARGIN = 16;
    private static final int GRID = CELL_SIZE + 2 * MARGIN;
    private static final int MEMORY_CELLS = 24;
    public static final String ATTRIBUTION = "Routes et bâtiments © OpenStreetMap contributors (ODbL)";

    // Codes de revêtement.
    public static final byte NONE = 0;
    public static final byte ROAD_MAJOR = 1;
    public static final byte ROAD_MINOR = 2;
    public static final byte FOOTWAY = 3;
    public static final byte TRACK = 4;
    public static final byte RAIL = 5;
    public static final byte WATER = 6;

    // Codes d'occupation du sol.
    public static final byte LAND_NONE = 0;
    public static final byte LAND_FOREST = 1;
    public static final byte LAND_GRASS = 2;
    public static final byte LAND_FARMLAND = 3;
    public static final byte LAND_SAND = 4;
    public static final byte LAND_ROCK = 5;
    public static final byte LAND_GLACIER = 6;
    public static final byte LAND_WETLAND = 7;
    public static final byte LAND_URBAN = 8;
    public static final byte LAND_PARK = 9;
    public static final byte LAND_PITCH = 10;

    public static final short NO_LEVEL = Short.MIN_VALUE;
    public static final byte DECK_EDGE = 1;
    public static final byte DECK_PIER = 2;

    public enum RoofShape { FLAT, HIPPED, MANSARD, STEEP }

    /**
     * Bâtiment rastérisé : hauteurs en Y Minecraft absolus, blocs en identifiants
     * ({@code minecraft:bricks}) résolus par le générateur.
     */
    public record Building(int baseY, int topY, int floorStep, String wall, String roof, String window,
                           RoofShape shape, boolean shopFront, long seed) {
    }

    /** Grilles d'une cellule ; indices locaux de -MARGIN à CELL_SIZE+MARGIN-1. */
    public static final class Cell {
        static final Cell EMPTY = new Cell(0, 0);
        final int originX;
        final int originZ;
        final byte[] surface = new byte[GRID * GRID];
        final byte[] land = new byte[GRID * GRID];
        final int[] building = new int[GRID * GRID];
        final short[] water = new short[GRID * GRID];
        final short[] deck = new short[GRID * GRID];
        /** Détails du pont : {@link #DECK_EDGE} parapet, {@link #DECK_PIER} pilier. */
        final byte[] deckFlags = new byte[GRID * GRID];
        /** Distance (en blocs) au mur le plus proche, à l'intérieur d'un bâtiment : sert aux toits. */
        final byte[] inset = new byte[GRID * GRID];
        final List<Building> buildings = new ArrayList<>();

        Cell(int originX, int originZ) {
            this.originX = originX;
            this.originZ = originZ;
            Arrays.fill(water, NO_LEVEL);
            Arrays.fill(deck, NO_LEVEL);
        }

        private int index(int blockX, int blockZ) {
            int lx = blockX - originX + MARGIN;
            int lz = blockZ - originZ + MARGIN;
            if (lx < 0 || lz < 0 || lx >= GRID || lz >= GRID) {
                return -1;
            }
            return lz * GRID + lx;
        }

        public byte surface(int x, int z) {
            int i = index(x, z);
            return i < 0 ? NONE : surface[i];
        }

        public byte land(int x, int z) {
            int i = index(x, z);
            return i < 0 ? LAND_NONE : land[i];
        }

        public short waterLevel(int x, int z) {
            int i = index(x, z);
            return i < 0 ? NO_LEVEL : water[i];
        }

        public short deck(int x, int z) {
            int i = index(x, z);
            return i < 0 ? NO_LEVEL : deck[i];
        }

        public byte deckFlags(int x, int z) {
            int i = index(x, z);
            return i < 0 ? 0 : deckFlags[i];
        }

        /** Bâtiment couvrant ce bloc, ou {@code null}. */
        public Building building(int x, int z) {
            int i = index(x, z);
            return i < 0 || building[i] == 0 ? null : buildings.get(building[i] - 1);
        }

        /** 0 sur le mur, 1 juste à l'intérieur, etc. */
        public int inset(int x, int z) {
            int i = index(x, z);
            return i < 0 ? 0 : inset[i];
        }

        /** Vrai si le bloc fait partie du contour du bâtiment (mur extérieur). */
        public boolean isWall(int x, int z) {
            int i = index(x, z);
            if (i < 0 || building[i] == 0) {
                return false;
            }
            int id = building[i];
            int lx = i % GRID;
            int lz = i / GRID;
            return lx == 0 || lz == 0 || lx == GRID - 1 || lz == GRID - 1
                    || building[i - 1] != id || building[i + 1] != id
                    || building[i - GRID] != id || building[i + GRID] != id;
        }
    }

    private final EarthTerrain terrain;
    private final VectorTiles vectorTiles;
    private final ExecutorService downloads = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "TerraCraft-osm");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, CompletableFuture<Cell>> inFlight = new ConcurrentHashMap<>();
    private final Map<Long, Cell> memory = new LinkedHashMap<>(MEMORY_CELLS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Cell> eldest) {
            return size() > MEMORY_CELLS;
        }
    };

    OsmCells(EarthTerrain terrain, Path cacheDir) {
        this.terrain = terrain;
        this.vectorTiles = new VectorTiles(cacheDir);
    }

    /** Cellule contenant le bloc ; bloquant au premier accès (téléchargement). */
    public Cell cellAt(int blockX, int blockZ) {
        return load(Math.floorDiv(blockX, CELL_SIZE), Math.floorDiv(blockZ, CELL_SIZE)).join();
    }

    /** Prépare toutes les cellules à moins de {@code radius} blocs du point. */
    public CompletableFuture<Void> prefetch(double blockX, double blockZ, double radius) {
        List<CompletableFuture<Cell>> futures = new ArrayList<>();
        for (int cx = Math.floorDiv((int) Math.floor(blockX - radius), CELL_SIZE);
             cx <= Math.floorDiv((int) Math.floor(blockX + radius), CELL_SIZE); cx++) {
            for (int cz = Math.floorDiv((int) Math.floor(blockZ - radius), CELL_SIZE);
                 cz <= Math.floorDiv((int) Math.floor(blockZ + radius), CELL_SIZE); cz++) {
                futures.add(load(cx, cz));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Cell> load(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        synchronized (memory) {
            Cell cell = memory.get(key);
            if (cell != null) {
                return CompletableFuture.completedFuture(cell);
            }
        }
        CompletableFuture<Cell> future = inFlight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            Cell cell = build(cx, cz);
            synchronized (memory) {
                memory.put(k, cell);
            }
            return cell;
        }, downloads));
        future.whenComplete((cell, error) -> inFlight.remove(key, future));
        return future;
    }

    // --- Téléchargement ------------------------------------------------------------------

    /** Vrai si toute la cellule est en mer : inutile de télécharger des tuiles. */
    private boolean isOpenSea(int cx, int cz) {
        for (int i = 0; i <= 8; i++) {
            for (int j = 0; j <= 8; j++) {
                if (terrain.elevation(cx * CELL_SIZE + i * CELL_SIZE / 8.0, cz * CELL_SIZE + j * CELL_SIZE / 8.0) > -5) {
                    return false;
                }
            }
        }
        return true;
    }

    private Cell build(int cx, int cz) {
        try {
            if (isOpenSea(cx, cz)) {
                return Cell.EMPTY;
            }
            double worldSize = WebMercator.worldSize(terrain.scale());
            int tiles = 1 << VectorTiles.ZOOM;
            int minTx = (int) Math.floor(((cx * CELL_SIZE - MARGIN) / worldSize + 0.5) * tiles);
            int maxTx = (int) Math.floor((((cx + 1) * CELL_SIZE + MARGIN) / worldSize + 0.5) * tiles);
            int minTy = (int) Math.floor(((cz * CELL_SIZE - MARGIN) / worldSize + 0.5) * tiles);
            int maxTy = (int) Math.floor((((cz + 1) * CELL_SIZE + MARGIN) / worldSize + 0.5) * tiles);
            List<Shape> shapes = new ArrayList<>();
            for (int ty = minTy; ty <= maxTy; ty++) {
                for (int tx = minTx; tx <= maxTx; tx++) {
                    List<MvtDecoder.Feature> features = vectorTiles.tile(Math.floorMod(tx, tiles), ty);
                    for (MvtDecoder.Feature feature : features) {
                        List<double[]> parts = new ArrayList<>(feature.parts().size());
                        for (double[] part : feature.parts()) {
                            double[] xz = new double[part.length];
                            for (int k = 0; k < part.length; k += 2) {
                                xz[k] = ((tx + part[k]) / tiles - 0.5) * worldSize;
                                xz[k + 1] = ((ty + part[k + 1]) / tiles - 0.5) * worldSize;
                            }
                            parts.add(xz);
                        }
                        shapes.add(new Shape(feature, parts));
                    }
                }
            }
            return new Rasterizer(cx * CELL_SIZE, cz * CELL_SIZE).run(shapes);
        } catch (RuntimeException e) {
            GeoMod.LOGGER.error("Cellule OSM {},{} illisible : générée sans routes ni bâtiments", cx, cz, e);
            return Cell.EMPTY;
        }
    }

    /** Élément de tuile converti en coordonnées bloc. */
    private record Shape(MvtDecoder.Feature feature, List<double[]> parts) {
        String layer() {
            return feature.layer();
        }

        String get(String key) {
            return feature.string(key);
        }
    }

    // --- Rastérisation -------------------------------------------------------------------

    private final class Rasterizer {
        private final Cell cell;
        private final int gridX;
        private final int gridZ;

        Rasterizer(int originX, int originZ) {
            this.cell = new Cell(originX, originZ);
            this.gridX = originX - MARGIN;
            this.gridZ = originZ - MARGIN;
        }

        /** Vrai si la forme touche la grille de la cellule (les tuiles débordent largement). */
        private boolean touches(Shape shape) {
            for (double[] part : shape.parts()) {
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                for (int k = 0; k < part.length; k += 2) {
                    minX = Math.min(minX, part[k]);
                    maxX = Math.max(maxX, part[k]);
                    minZ = Math.min(minZ, part[k + 1]);
                    maxZ = Math.max(maxZ, part[k + 1]);
                }
                if (maxX >= gridX - 32 && minX <= gridX + GRID + 32 && maxZ >= gridZ - 32 && minZ <= gridZ + GRID + 32) {
                    return true;
                }
            }
            return false;
        }

        Cell run(List<Shape> shapes) {
            List<Shape> land = new ArrayList<>();
            List<Shape> water = new ArrayList<>();
            List<Shape> waterways = new ArrayList<>();
            List<Shape> roads = new ArrayList<>();
            List<Shape> buildings = new ArrayList<>();
            for (Shape shape : shapes) {
                if (!touches(shape) || "tunnel".equals(shape.get("brunnel"))) {
                    continue;
                }
                switch (shape.layer()) {
                    case "building" -> {
                        if (shape.feature().type() == MvtDecoder.POLYGON && !shape.feature().bool("hide_3d")) {
                            buildings.add(shape);
                        }
                    }
                    case "transportation" -> roads.add(shape);
                    case "water" -> {
                        if (!shape.feature().bool("intermittent")) {
                            water.add(shape);
                        }
                    }
                    case "waterway" -> waterways.add(shape);
                    case "landcover", "landuse" -> {
                        if (landCode(shape) != LAND_NONE) {
                            land.add(shape);
                        }
                    }
                    default -> {
                    }
                }
            }
            // landuse (quartiers) d'abord, puis landcover (bois, herbe) par-dessus.
            land.sort((x, y) -> Boolean.compare("landcover".equals(x.layer()), "landcover".equals(y.layer())));
            for (Shape shape : land) {
                byte code = landCode(shape);
                fillPolygon(shape.parts(), i -> cell.land[i] = code);
            }
            for (Shape shape : water) {
                short level = waterLevel(shape.parts());
                fillPolygon(shape.parts(), i -> setWater(i, level));
            }
            for (Shape shape : waterways) {
                short level = waterLevel(shape.parts());
                double width = width(switch (shape.get("class")) {
                    case "river" -> 15;
                    case "canal" -> 8;
                    case "stream" -> 2;
                    default -> 1;
                });
                for (double[] line : shape.parts()) {
                    strokeLine(line, width, i -> setWater(i, level));
                }
            }
            for (Shape shape : roads) {
                road(shape);
            }
            for (Shape shape : buildings) {
                building(shape);
            }
            computeInsets();
            return cell;
        }

        private void setWater(int i, short level) {
            cell.surface[i] = WATER;
            cell.water[i] = cell.water[i] == NO_LEVEL ? level : (short) Math.min(cell.water[i], level);
        }

        private void road(Shape shape) {
            String type = shape.get("class");
            String subclass = shape.get("subclass");
            byte code;
            double metres;
            switch (type) {
                case "motorway", "trunk" -> { code = ROAD_MAJOR; metres = 12; }
                case "primary" -> { code = ROAD_MAJOR; metres = 10; }
                case "secondary", "raceway" -> { code = ROAD_MAJOR; metres = 8; }
                case "tertiary" -> { code = ROAD_MINOR; metres = 7; }
                case "minor", "busway" -> { code = ROAD_MINOR; metres = 6; }
                case "service" -> { code = ROAD_MINOR; metres = 4; }
                case "track" -> { code = TRACK; metres = 3; }
                case "path" -> { code = FOOTWAY; metres = "pedestrian".equals(subclass) ? 5 : 2; }
                case "pier" -> { code = FOOTWAY; metres = 3; }
                case "rail", "transit" -> {
                    if ("subway".equals(subclass)) {
                        return;
                    }
                    code = RAIL;
                    metres = "tram".equals(subclass) ? 2 : 3;
                }
                default -> { return; }
            }
            if ("yes".equals(shape.get("ramp")) || shape.get("ramp").equals("1")) {
                metres *= 0.7;
            }
            double width = width(metres);
            if (shape.feature().type() == MvtDecoder.POLYGON) {
                byte areaCode = code;
                fillPolygon(shape.parts(), i -> {
                    if (cell.surface[i] != WATER) {
                        cell.surface[i] = areaCode;
                    }
                });
                return;
            }
            byte roadCode = code;
            if ("bridge".equals(shape.get("brunnel"))) {
                // La chaussée au sol rejoint le pont là où la rampe touche terre.
                for (double[] line : shape.parts()) {
                    strokeLine(line, width, i -> {
                        if (cell.surface[i] != WATER && cell.surface[i] == NONE) {
                            cell.surface[i] = roadCode;
                        }
                    });
                }
                bridge(shape.parts(), width);
                return;
            }
            for (double[] line : shape.parts()) {
                strokeLine(line, width, i -> {
                    // Une route mineure ne recouvre pas une route majeure ni l'eau.
                    if (cell.surface[i] != WATER && (cell.surface[i] == NONE || cell.surface[i] >= roadCode)) {
                        cell.surface[i] = roadCode;
                    }
                });
            }
        }

        /**
         * Pont : tablier calé entre 4 et 12 blocs au-dessus de l'eau (le relief SRTM des villes
         * est gonflé par les toits, on ne peut pas se fier aux culées), rampes de 1 bloc pour 3
         * vers le sol à chaque extrémité, parapets sur les bords et piliers tous les 16 blocs.
         */
        private void bridge(List<double[]> lines, double width) {
            double half = Math.max(1.5, width / 2);
            for (double[] line : lines) {
                int n = line.length;
                int startY = terrain.surfaceY((int) Math.floor(line[0]), (int) Math.floor(line[1]));
                int endY = terrain.surfaceY((int) Math.floor(line[n - 2]), (int) Math.floor(line[n - 1]));
                int[] waterMin = {Integer.MAX_VALUE};
                strokeDetailed(line, half, (i, along, lateral, length) -> {
                    if (cell.water[i] != NO_LEVEL) {
                        waterMin[0] = Math.min(waterMin[0], cell.water[i]);
                    }
                });
                int deck = Math.max(startY, endY) + 1;
                if (waterMin[0] != Integer.MAX_VALUE) {
                    deck = Math.max(waterMin[0] + 4, Math.min(deck, waterMin[0] + 12));
                }
                int target = deck;
                strokeDetailed(line, half, (i, along, lateral, length) -> {
                    int y = (int) Math.min(target, Math.min(startY + 1 + along / 3, endY + 1 + (length - along) / 3));
                    if (y > cell.deck[i]) {
                        cell.deck[i] = (short) y;
                    }
                    byte flags = cell.deckFlags[i];
                    if (Math.abs(lateral) > half - 1) {
                        flags |= DECK_EDGE;
                    }
                    if (Math.abs(lateral) < 1 && Math.floorMod((int) along, 16) == 8 && along > 6 && length - along > 6) {
                        flags |= DECK_PIER;
                    }
                    cell.deckFlags[i] = flags;
                });
            }
        }

        private interface LineAction {
            void apply(int index, double along, double lateral, double length);
        }

        /** Comme strokeLine, mais donne la position le long de la ligne et l'écart latéral. */
        private void strokeDetailed(double[] line, double half, LineAction action) {
            double total = 0;
            for (int k = 0; k + 3 < line.length; k += 2) {
                total += Math.hypot(line[k + 2] - line[k], line[k + 3] - line[k + 1]);
            }
            double offset = 0;
            for (int k = 0; k + 3 < line.length; k += 2) {
                double x1 = line[k] - gridX;
                double z1 = line[k + 1] - gridZ;
                double dx = line[k + 2] - line[k];
                double dz = line[k + 3] - line[k + 1];
                double segment = Math.hypot(dx, dz);
                int minX = Math.max(0, (int) Math.floor(Math.min(x1, x1 + dx) - half));
                int maxX = Math.min(GRID - 1, (int) Math.ceil(Math.max(x1, x1 + dx) + half));
                int minZ = Math.max(0, (int) Math.floor(Math.min(z1, z1 + dz) - half));
                int maxZ = Math.min(GRID - 1, (int) Math.ceil(Math.max(z1, z1 + dz) + half));
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        double px = x + 0.5 - x1;
                        double pz = z + 0.5 - z1;
                        double t = segment == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + pz * dz) / (segment * segment)));
                        double ex = px - t * dx;
                        double ez = pz - t * dz;
                        if (ex * ex + ez * ez <= half * half) {
                            double lateral = segment == 0 ? 0 : (px * dz - pz * dx) / segment;
                            action.apply(z * GRID + x, offset + t * segment, lateral, total);
                        }
                    }
                }
                offset += segment;
            }
        }

        private void building(Shape shape) {
            List<double[]> rings = shape.parts();
            // Base : point le plus bas du contour, pour ne jamais flotter.
            int base = Integer.MAX_VALUE;
            double sumX = 0;
            double sumZ = 0;
            int count = 0;
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (double[] ring : rings) {
                for (int k = 0; k < ring.length; k += 2) {
                    base = Math.min(base, terrain.surfaceY((int) Math.floor(ring[k]), (int) Math.floor(ring[k + 1])));
                    sumX += ring[k];
                    sumZ += ring[k + 1];
                    count++;
                    minX = Math.min(minX, ring[k]);
                    maxX = Math.max(maxX, ring[k]);
                    minZ = Math.min(minZ, ring[k + 1]);
                    maxZ = Math.max(maxZ, ring[k + 1]);
                }
            }
            double latitude = terrain.latitudeAt(sumZ / count);
            double bpm = terrain.blocksPerMetre(latitude);
            double metres = shape.feature().number("render_height", 9);
            double minMetres = shape.feature().number("render_min_height", 0);
            int floorStep = Math.max(3, (int) Math.round(3.0 * bpm));
            base += (int) Math.round(minMetres * bpm);
            int height = Math.max(4, (int) Math.round((metres - minMetres) * bpm));
            int top = Math.min(EarthTerrain.MAX_SURFACE_Y + 2, base + height);
            // Le schéma OpenMapTiles ne donne pas le type : on le devine (hauteur, emprise).
            double footprint = (maxX - minX) * (maxZ - minZ) / (bpm * bpm);
            String type;
            if (metres <= 8 && footprint < 250) {
                type = "house";
            } else if (metres <= 15 && footprint > 2500) {
                type = "industrial";
            } else {
                type = "apartments";
            }
            JsonObject tags = new JsonObject();
            String colour = shape.get("colour");
            if (!colour.isEmpty()) {
                tags.addProperty("building:colour", colour);
            }
            long seed = Double.doubleToLongBits(sumX * 31 + sumZ);
            cell.buildings.add(BuildingStyles.style(tags, type, seed, base, top, floorStep, latitude));
            int id = cell.buildings.size();
            fillPolygon(rings, i -> cell.building[i] = id);
        }

        /** Distance de chanfrein au contour de chaque bâtiment (deux passes). */
        private void computeInsets() {
            int[] ids = cell.building;
            byte[] d = cell.inset;
            for (int z = 0; z < GRID; z++) {
                for (int x = 0; x < GRID; x++) {
                    int i = z * GRID + x;
                    if (ids[i] == 0) {
                        continue;
                    }
                    boolean edge = x == 0 || z == 0 || x == GRID - 1 || z == GRID - 1
                            || ids[i - 1] != ids[i] || ids[i + 1] != ids[i]
                            || ids[i - GRID] != ids[i] || ids[i + GRID] != ids[i];
                    d[i] = edge ? 0 : (byte) Math.min(120, Math.min(d[i - 1], d[i - GRID]) + 1);
                }
            }
            for (int z = GRID - 2; z >= 1; z--) {
                for (int x = GRID - 2; x >= 1; x--) {
                    int i = z * GRID + x;
                    if (ids[i] != 0 && d[i] > 0) {
                        d[i] = (byte) Math.min(d[i], Math.min(d[i + 1], d[i + GRID]) + 1);
                    }
                }
            }
        }

        private short waterLevel(List<double[]> rings) {
            // Niveau d'eau = sol le plus bas parmi les sommets proches de la cellule, moins 1 :
            // constant pour toute la surface, et identique d'une cellule voisine à l'autre.
            int level = Integer.MAX_VALUE;
            int range = 160;
            for (double[] ring : rings) {
                for (int k = 0; k < ring.length; k += 2) {
                    double x = ring[k];
                    double z = ring[k + 1];
                    if (x >= gridX - range && x < gridX + GRID + range && z >= gridZ - range && z < gridZ + GRID + range) {
                        level = Math.min(level, terrain.surfaceY((int) Math.floor(x), (int) Math.floor(z)) - 1);
                    }
                }
            }
            if (level == Integer.MAX_VALUE) {
                level = terrain.surfaceY(gridX + GRID / 2, gridZ + GRID / 2) - 1;
            }
            return (short) Math.max(EarthTerrain.SEA_LEVEL - 1, level);
        }

        // --- Géométrie ---

        private interface PixelAction {
            void apply(int index);
        }

        /** Remplissage pair-impair : fonctionne aussi pour les multipolygones découpés en plusieurs ways. */
        private void fillPolygon(List<double[]> rings, PixelAction action) {
            double minZ = Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            for (double[] ring : rings) {
                for (int k = 1; k < ring.length; k += 2) {
                    minZ = Math.min(minZ, ring[k]);
                    maxZ = Math.max(maxZ, ring[k]);
                }
            }
            int fromRow = Math.max(0, (int) Math.floor(minZ - gridZ));
            int toRow = Math.min(GRID - 1, (int) Math.ceil(maxZ - gridZ));
            double[] crossings = new double[64];
            for (int row = fromRow; row <= toRow; row++) {
                double z = gridZ + row + 0.5;
                int n = 0;
                for (double[] ring : rings) {
                    for (int k = 0; k + 3 < ring.length; k += 2) {
                        double z1 = ring[k + 1];
                        double z2 = ring[k + 3];
                        if ((z1 <= z) != (z2 <= z)) {
                            double x1 = ring[k];
                            double x2 = ring[k + 2];
                            if (n == crossings.length) {
                                crossings = Arrays.copyOf(crossings, n * 2);
                            }
                            crossings[n++] = x1 + (z - z1) / (z2 - z1) * (x2 - x1);
                        }
                    }
                }
                Arrays.sort(crossings, 0, n);
                for (int c = 0; c + 1 < n; c += 2) {
                    int from = Math.max(0, (int) Math.ceil(crossings[c] - gridX - 0.5));
                    int to = Math.min(GRID - 1, (int) Math.floor(crossings[c + 1] - gridX - 0.5));
                    for (int col = from; col <= to; col++) {
                        action.apply(row * GRID + col);
                    }
                }
            }
        }

        /** Trace une ligne épaisse : chaque bloc à moins de width/2 d'un segment. */
        private void strokeLine(double[] line, double width, PixelAction action) {
            double half = Math.max(0.5, width / 2);
            for (int k = 0; k + 3 < line.length; k += 2) {
                double x1 = line[k] - gridX;
                double z1 = line[k + 1] - gridZ;
                double x2 = line[k + 2] - gridX;
                double z2 = line[k + 3] - gridZ;
                int minX = Math.max(0, (int) Math.floor(Math.min(x1, x2) - half));
                int maxX = Math.min(GRID - 1, (int) Math.ceil(Math.max(x1, x2) + half));
                int minZ = Math.max(0, (int) Math.floor(Math.min(z1, z2) - half));
                int maxZ = Math.min(GRID - 1, (int) Math.ceil(Math.max(z1, z2) + half));
                double dx = x2 - x1;
                double dz = z2 - z1;
                double length2 = dx * dx + dz * dz;
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        double px = x + 0.5 - x1;
                        double pz = z + 0.5 - z1;
                        double t = length2 == 0 ? 0 : Math.max(0, Math.min(1, (px * dx + pz * dz) / length2));
                        double ex = px - t * dx;
                        double ez = pz - t * dz;
                        if (ex * ex + ez * ez <= half * half) {
                            action.apply(z * GRID + x);
                        }
                    }
                }
            }
        }

        private double width(double metres) {
            double latitude = terrain.latitudeAt(gridZ + GRID / 2.0);
            return Math.max(1, metres * terrain.blocksPerMetre(latitude));
        }
    }

    // --- Classes OpenMapTiles -------------------------------------------------------------

    static byte landCode(Shape shape) {
        String type = shape.get("class");
        if ("landcover".equals(shape.layer())) {
            String subclass = shape.get("subclass");
            return switch (type) {
                case "wood" -> LAND_FOREST;
                case "grass" -> switch (subclass) {
                    case "park", "garden", "village_green", "recreation_ground" -> LAND_PARK;
                    case "allotments" -> LAND_FARMLAND;
                    default -> LAND_GRASS;
                };
                case "farmland" -> LAND_FARMLAND;
                case "sand" -> LAND_SAND;
                case "rock" -> LAND_ROCK;
                case "ice" -> LAND_GLACIER;
                case "wetland" -> LAND_WETLAND;
                default -> LAND_NONE;
            };
        }
        return switch (type) {
            case "residential", "commercial", "industrial", "retail", "railway", "garages", "quarry", "suburb",
                 "neighbourhood", "quarter" -> LAND_URBAN;
            case "park", "zoo", "cemetery", "garden" -> LAND_PARK;
            case "pitch", "playground", "stadium", "track" -> LAND_PITCH;
            default -> LAND_NONE;
        };
    }
}
