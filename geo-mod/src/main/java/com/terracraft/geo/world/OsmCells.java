package com.terracraft.geo.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.terracraft.geo.GeoMod;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
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
                           RoofShape shape, boolean shopFront, boolean curtain, long seed) {
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
    private final Path cacheDir;
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
        this.cacheDir = cacheDir;
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
            Path overture = cacheDir.resolve("overture-buildings.geojson");
            boolean useOvertureBuildings = Files.isRegularFile(overture);
            for (int ty = minTy; ty <= maxTy; ty++) {
                for (int tx = minTx; tx <= maxTx; tx++) {
                    List<MvtDecoder.Feature> features = vectorTiles.tile(Math.floorMod(tx, tiles), ty);
                    for (MvtDecoder.Feature feature : features) {
                        if (useOvertureBuildings && "building".equals(feature.layer())) {
                            continue;
                        }
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
            if (useOvertureBuildings) {
                shapes.addAll(overtureShapes(overture, cx * CELL_SIZE - MARGIN, (cx + 1) * CELL_SIZE + MARGIN,
                        cz * CELL_SIZE - MARGIN, (cz + 1) * CELL_SIZE + MARGIN));
            }
            return new Rasterizer(cx * CELL_SIZE, cz * CELL_SIZE).run(shapes);
        } catch (RuntimeException e) {
            GeoMod.LOGGER.error("Cellule OSM {},{} illisible : générée sans routes ni bâtiments", cx, cz, e);
            return Cell.EMPTY;
        }
    }

    /** Lit le cache GeoJSON produit par tools/fetch_overture_buildings.py. */
    private List<Shape> overtureShapes(Path file, double minX, double maxX, double minZ, double maxZ) {
        List<Shape> result = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("features")) {
                JsonObject feature = element.getAsJsonObject();
                JsonObject geometry = feature.getAsJsonObject("geometry");
                JsonObject properties = feature.has("properties") && feature.get("properties").isJsonObject()
                        ? feature.getAsJsonObject("properties") : new JsonObject();
                List<double[]> parts = overtureParts(geometry, minX, maxX, minZ, maxZ);
                if (!parts.isEmpty()) {
                    long id = feature.has("id") ? stableOvertureId(feature.get("id").getAsString()) : -result.size() - 1L;
                    Map<String, Object> tags = new java.util.HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value.isJsonPrimitive()) {
                            if (value.getAsJsonPrimitive().isNumber()) {
                                tags.put(entry.getKey(), value.getAsDouble());
                            } else if (value.getAsJsonPrimitive().isBoolean()) {
                                tags.put(entry.getKey(), value.getAsBoolean());
                            } else {
                                tags.put(entry.getKey(), value.getAsString());
                            }
                        }
                    }
                    result.add(new Shape(new MvtDecoder.Feature("building", id, MvtDecoder.POLYGON, tags, parts), parts));
                }
            }
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.warn("Cache Overture illisible : {}", file, e);
        }
        return result;
    }

    private List<double[]> overtureParts(JsonObject geometry, double minX, double maxX, double minZ, double maxZ) {
        List<double[]> parts = new ArrayList<>();
        if (geometry == null || !geometry.has("coordinates")) {
            return parts;
        }
        String type = geometry.get("type").getAsString();
        JsonArray coordinates = geometry.getAsJsonArray("coordinates");
        if ("Polygon".equals(type)) {
            addOvertureRings(parts, coordinates, minX, maxX, minZ, maxZ);
        } else if ("MultiPolygon".equals(type)) {
            for (JsonElement polygon : coordinates) {
                addOvertureRings(parts, polygon.getAsJsonArray(), minX, maxX, minZ, maxZ);
            }
        }
        return parts;
    }

    private void addOvertureRings(List<double[]> parts, JsonArray rings, double minX, double maxX,
                                  double minZ, double maxZ) {
        for (JsonElement ringElement : rings) {
            JsonArray ring = ringElement.getAsJsonArray();
            double[] points = new double[ring.size() * 2];
            double ringMinX = Double.MAX_VALUE, ringMaxX = -Double.MAX_VALUE;
            double ringMinZ = Double.MAX_VALUE, ringMaxZ = -Double.MAX_VALUE;
            for (int i = 0; i < ring.size(); i++) {
                JsonArray point = ring.get(i).getAsJsonArray();
                double x = WebMercator.blockX(point.get(0).getAsDouble(), terrain.scale());
                double z = WebMercator.blockZ(point.get(1).getAsDouble(), terrain.scale());
                points[i * 2] = x;
                points[i * 2 + 1] = z;
                ringMinX = Math.min(ringMinX, x);
                ringMaxX = Math.max(ringMaxX, x);
                ringMinZ = Math.min(ringMinZ, z);
                ringMaxZ = Math.max(ringMaxZ, z);
            }
            boolean touches = ringMaxX >= minX && ringMinX <= maxX && ringMaxZ >= minZ && ringMinZ <= maxZ;
            if (touches && points.length >= 6) {
                parts.add(points);
            }
        }
    }

    private static long stableOvertureId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return id.hashCode();
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
            buildings(buildings);
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

        /**
         * Un bâtiment OSM peut arriver en plusieurs morceaux (découpe des tuiles) : on les
         * regroupe par identifiant pour leur donner une base, une hauteur et un style uniques.
         */
        private void buildings(List<Shape> shapes) {
            Map<Long, List<Shape>> groups = new java.util.LinkedHashMap<>();
            long anonymous = -1;
            for (Shape shape : shapes) {
                long id = shape.feature().id();
                groups.computeIfAbsent(id != 0 ? id : anonymous--, k -> new ArrayList<>()).add(shape);
            }
            groups.forEach(this::building);
            cleanFootprints();
        }

        private void building(long osmId, List<Shape> parts) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            List<double[]> rings = new ArrayList<>();
            for (Shape part : parts) {
                for (double[] ring : part.parts()) {
                    rings.add(ring);
                    for (int k = 0; k < ring.length; k += 2) {
                        minX = Math.min(minX, ring[k]);
                        maxX = Math.max(maxX, ring[k]);
                        minZ = Math.min(minZ, ring[k + 1]);
                        maxZ = Math.max(maxZ, ring[k + 1]);
                    }
                }
            }
            // Trop petit pour un bâtiment lisible (abri, kiosque mal tracé) : ignoré.
            if ((maxX - minX) * (maxZ - minZ) < 6) {
                return;
            }
            double latitude = terrain.latitudeAt((minZ + maxZ) / 2);
            double longitude = terrain.longitudeAt((minX + maxX) / 2);
            double bpm = terrain.blocksPerMetre(latitude);
            double footprint = (maxX - minX) * (maxZ - minZ) / (bpm * bpm);
            int ground = groundLevel(rings, minX, maxX, minZ, maxZ);
            MvtDecoder.Feature feature = parts.get(0).feature();
            String rawType = firstString(feature, "building", "building:type", "class", "subclass", "type");
            String type = rawType.isEmpty()
                    ? BuildingStyles.guessType(firstNumber(feature, 9, "render_height"), footprint, latitude, longitude)
                    : rawType;
            String styleType = BuildingStyles.normaliseType(type, firstNumber(feature, 9, "render_height"),
                    footprint, latitude, longitude);
            double minMetres = firstNumber(feature, 0, "render_min_height", "min_height", "building:min_height");
            double metres = buildingHeight(feature, styleType, minMetres, footprint);
            // Les valeurs issues de sources différentes ne doivent jamais permettre à une
            // donnée aberrante de faire monter une maison jusqu'au plafond du monde.
            double safeMinMetres = Math.max(0, Math.min(minMetres, metres - 3));
            int floorStep = Math.max(3, (int) Math.round(3.0 * bpm));
            int base = ground + (int) Math.round(safeMinMetres * bpm);
            int height = Math.max(4, (int) Math.round((metres - safeMinMetres) * bpm));
            int top = Math.min(EarthTerrain.MAX_SURFACE_Y + 2, base + height);
            JsonObject tags = buildingTags(feature);
            cell.buildings.add(BuildingStyles.style(tags, styleType, osmId, base, top, floorStep, latitude));
            int id = cell.buildings.size();
            for (Shape part : parts) {
                fillPolygon(part.parts(), i -> cell.building[i] = id);
            }
        }

        /**
         * OpenFreeMap fournit parfois les valeurs calculées render_height, parfois les
         * balises OSM brutes. On privilégie les données brutes et les niveaux pour éviter
         * que toutes les maisons deviennent des blocs de 9 m identiques.
         */
        private double buildingHeight(MvtDecoder.Feature feature, String type, double minMetres, double footprint) {
            double explicit = firstNumber(feature, Double.NaN, "height", "building:height", "render_height");
            double levels = firstNumber(feature, Double.NaN, "building:levels", "levels", "building:levels:aboveground", "num_floors");
            boolean measured = Double.isFinite(explicit) || Double.isFinite(levels);
            double fallback = switch (type.toLowerCase(java.util.Locale.ROOT)) {
                case "house", "detached", "semidetached_house", "terrace", "bungalow", "farm", "cabin", "hut" -> 6.5;
                case "garage", "garages", "shed", "kiosk", "greenhouse" -> 3.5;
                case "warehouse", "factory", "hangar", "industrial" -> 8.0;
                case "tower", "skyscraper" -> 45.0;
                default -> 10.0;
            };
            double metres = Double.isFinite(explicit) ? explicit
                    : Double.isFinite(levels) ? Math.max(1, levels) * 3.1 : fallback;
            if (Double.isFinite(levels) && !Double.isFinite(explicit)) {
                metres = Math.max(metres, levels * 3.1);
            }
            // Les tuiles OpenMapTiles fournissent une hauteur de rendu approximative. Sans
            // cette garde, une valeur de tour/partie de bâtiment peut transformer une maison
            // en immeuble vide. Les plafonds restent volontairement conservateurs tant que
            // la source Overture n'a pas fourni les vraies parties et niveaux.
            double cap = switch (type) {
                case "house", "detached", "semidetached_house", "terrace", "bungalow", "farm", "cabin", "hut" -> 8.5;
                case "garage", "garages", "shed", "kiosk", "greenhouse" -> 4.5;
                case "industrial", "warehouse", "factory", "hangar", "manufacture" -> 18.0;
                case "tower", "skyscraper" -> 60.0;
                // Un type générique sans hauteur/niveaux fiables doit rester un bâtiment
                // urbain crédible, pas une tour vide issue d'une bbox trop large.
                default -> measured ? 18.0 : footprint < 250 ? 8.5 : footprint < 1200 ? 12.0 : 16.0;
            };
            double safeMin = Math.max(0, Math.min(minMetres, cap - 3));
            return Math.max(safeMin + 3, Math.min(cap, metres));
        }

        private static double firstNumber(MvtDecoder.Feature feature, double fallback, String... keys) {
            for (String key : keys) {
                double value = feature.number(key, Double.NaN);
                if (Double.isFinite(value) && value > 0) {
                    return value;
                }
            }
            return fallback;
        }

        private static String firstString(MvtDecoder.Feature feature, String... keys) {
            for (String key : keys) {
                String value = feature.string(key).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return "";
        }

        private static JsonObject buildingTags(MvtDecoder.Feature feature) {
            JsonObject tags = new JsonObject();
            String[] keys = {"building", "building:type", "building:colour", "building:material",
                    "colour", "material", "roof:shape", "roof:colour", "roof:material",
                    "facade_color", "facade_material", "roof_color", "roof_material", "roof_height"};
            for (String key : keys) {
                String value = feature.string(key).trim();
                if (!value.isEmpty()) {
                    tags.addProperty(key, value);
                }
            }
            if (!feature.string("facade_color").isEmpty() && !tags.has("building:colour")) {
                tags.addProperty("building:colour", feature.string("facade_color"));
            }
            if (!feature.string("facade_material").isEmpty() && !tags.has("building:material")) {
                tags.addProperty("building:material", feature.string("facade_material"));
            }
            if (!feature.string("roof_color").isEmpty() && !tags.has("roof:colour")) {
                tags.addProperty("roof:colour", feature.string("roof_color"));
            }
            if (!feature.string("roof_material").isEmpty() && !tags.has("roof:material")) {
                tags.addProperty("roof:material", feature.string("roof_material"));
            }
            return tags;
        }

        /**
         * Niveau du sol au pied du bâtiment : médiane du relief en de nombreux points (sommets,
         * milieux d'arêtes, grille intérieure). Le minimum enfonçait les bâtiments dès qu'un
         * sommet touchait un quai ou un creux du relief ; la médiane les pose au niveau de la rue.
         */
        private int groundLevel(List<double[]> rings, double minX, double maxX, double minZ, double maxZ) {
            List<Integer> samples = new ArrayList<>();
            for (double[] ring : rings) {
                int stride = Math.max(2, (ring.length / 2 / 24) * 2);
                for (int k = 0; k + 1 < ring.length; k += stride) {
                    samples.add(terrain.surfaceY((int) Math.floor(ring[k]), (int) Math.floor(ring[k + 1])));
                    if (k + 3 < ring.length) {
                        samples.add(terrain.surfaceY((int) Math.floor((ring[k] + ring[k + 2]) / 2),
                                (int) Math.floor((ring[k + 1] + ring[k + 3]) / 2)));
                    }
                }
            }
            for (int i = 1; i <= 3; i++) {
                for (int j = 1; j <= 3; j++) {
                    samples.add(terrain.surfaceY((int) Math.floor(minX + (maxX - minX) * i / 4),
                            (int) Math.floor(minZ + (maxZ - minZ) * j / 4)));
                }
            }
            samples.sort(Integer::compare);
            return samples.get(samples.size() / 2);
        }

        /**
         * Contours propres : retire les excroissances d'un bloc (moins de deux voisins du même
         * bâtiment) et bouche les encoches d'un bloc (trois voisins du même bâtiment).
         */
        private void cleanFootprints() {
            int[] ids = cell.building;
            int[] copy = ids.clone();
            int[] areas = new int[cell.buildings.size() + 1];
            for (int id : copy) {
                if (id > 0 && id < areas.length) {
                    areas[id]++;
                }
            }
            for (int z = 1; z < GRID - 1; z++) {
                for (int x = 1; x < GRID - 1; x++) {
                    int i = z * GRID + x;
                    int[] around = {copy[i - 1], copy[i + 1], copy[i - GRID], copy[i + GRID]};
                    if (copy[i] != 0) {
                        int same = 0;
                        for (int n : around) {
                            same += n == copy[i] ? 1 : 0;
                        }
                        // Ne pas éroder les petites maisons ou les annexes étroites : leurs
                        // contours peuvent légitimement ne faire qu'un bloc de large.
                        if (areas[copy[i]] >= 8 && same <= 1) {
                            ids[i] = 0;
                        }
                    } else {
                        for (int candidate : around) {
                            if (candidate == 0) {
                                continue;
                            }
                            int same = 0;
                            for (int n : around) {
                                same += n == candidate ? 1 : 0;
                            }
                            if (same >= 3 && cell.surface[i] != WATER) {
                                ids[i] = candidate;
                                break;
                            }
                        }
                    }
                }
            }
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
