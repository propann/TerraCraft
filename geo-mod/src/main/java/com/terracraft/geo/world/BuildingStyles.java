package com.terracraft.geo.world;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/**
 * Apparence des bâtiments à partir des étiquettes OSM.
 *
 * <p>Les couleurs et matériaux sont rarement renseignés (moins de 2 % à Paris) : on part
 * donc d'un style par type de bâtiment et par climat, puis on applique {@code building:colour},
 * {@code building:material}, {@code roof:colour}, {@code roof:material} et {@code roof:shape}
 * quand ils existent. Les couleurs sont converties vers le bloc Minecraft le plus proche.
 */
final class BuildingStyles {
    private BuildingStyles() {
    }

    /** Blocs de façade candidats avec leur couleur moyenne (RVB). */
    private static final Map<String, Integer> WALL_COLOURS = Map.ofEntries(
            Map.entry("white_concrete", 0xCFD5D6), Map.entry("light_gray_concrete", 0x7D7D73),
            Map.entry("gray_concrete", 0x36393D), Map.entry("black_concrete", 0x080A0F),
            Map.entry("red_concrete", 0x8E2020), Map.entry("orange_concrete", 0xE06100),
            Map.entry("yellow_concrete", 0xF0AF15), Map.entry("lime_concrete", 0x5EA818),
            Map.entry("green_concrete", 0x495B24), Map.entry("cyan_concrete", 0x157788),
            Map.entry("light_blue_concrete", 0x2389C6), Map.entry("blue_concrete", 0x2C2E8F),
            Map.entry("purple_concrete", 0x641F9C), Map.entry("magenta_concrete", 0xA9309F),
            Map.entry("pink_concrete", 0xD5658E), Map.entry("brown_concrete", 0x603B1F),
            Map.entry("white_terracotta", 0xD1B2A1), Map.entry("orange_terracotta", 0xA15325),
            Map.entry("yellow_terracotta", 0xBA8523), Map.entry("red_terracotta", 0x8F3D2E),
            Map.entry("brown_terracotta", 0x4D3323), Map.entry("light_gray_terracotta", 0x876B62),
            Map.entry("pink_terracotta", 0xA14E4E), Map.entry("terracotta", 0x985E43),
            Map.entry("bricks", 0x966153), Map.entry("calcite", 0xDFE0DC),
            Map.entry("smooth_sandstone", 0xDFD6AA), Map.entry("quartz_block", 0xEBE5DE),
            Map.entry("stone_bricks", 0x7A797A), Map.entry("mud_bricks", 0x89674F),
            Map.entry("deepslate_tiles", 0x363637), Map.entry("polished_andesite", 0x848685));

    /** Couleurs nommées courantes dans OSM (sous-ensemble CSS). */
    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("white", 0xFFFFFF), Map.entry("black", 0x000000), Map.entry("grey", 0x808080),
            Map.entry("gray", 0x808080), Map.entry("lightgrey", 0xD3D3D3), Map.entry("lightgray", 0xD3D3D3),
            Map.entry("darkgrey", 0xA9A9A9), Map.entry("darkgray", 0xA9A9A9), Map.entry("silver", 0xC0C0C0),
            Map.entry("red", 0xC03020), Map.entry("darkred", 0x8B0000), Map.entry("maroon", 0x800000),
            Map.entry("brown", 0x8B4513), Map.entry("sienna", 0xA0522D), Map.entry("tan", 0xD2B48C),
            Map.entry("beige", 0xF5F5DC), Map.entry("wheat", 0xF5DEB3), Map.entry("ivory", 0xFFFFF0),
            Map.entry("cream", 0xFFFDD0), Map.entry("yellow", 0xFFD700), Map.entry("gold", 0xFFD700),
            Map.entry("orange", 0xFF8C00), Map.entry("pink", 0xFFC0CB), Map.entry("palevioletred", 0xDB7093),
            Map.entry("green", 0x2E8B57), Map.entry("darkgreen", 0x006400), Map.entry("olive", 0x808000),
            Map.entry("blue", 0x3050C0), Map.entry("lightblue", 0xADD8E6), Map.entry("navy", 0x000080),
            Map.entry("cyan", 0x00B0C0), Map.entry("purple", 0x800080), Map.entry("terracotta", 0xC0603C));

    private static final Map<String, String> MATERIALS = Map.ofEntries(
            Map.entry("brick", "bricks"), Map.entry("bricks", "bricks"),
            Map.entry("stone", "stone_bricks"), Map.entry("limestone", "smooth_sandstone"),
            Map.entry("sandstone", "smooth_sandstone"), Map.entry("granite", "polished_granite"),
            Map.entry("marble", "quartz_block"), Map.entry("concrete", "light_gray_concrete"),
            Map.entry("plaster", "white_concrete"), Map.entry("render", "white_concrete"),
            Map.entry("glass", "light_blue_stained_glass"), Map.entry("wood", "spruce_planks"),
            Map.entry("timber_framing", "stripped_spruce_log"), Map.entry("metal", "iron_block"),
            Map.entry("steel", "iron_block"), Map.entry("mud", "mud_bricks"), Map.entry("adobe", "mud_bricks"),
            Map.entry("tiles", "terracotta"), Map.entry("roof_tiles", "terracotta"),
            Map.entry("slate", "deepslate_tiles"), Map.entry("zinc", "polished_andesite"),
            Map.entry("copper", "oxidized_cut_copper"), Map.entry("thatch", "hay_block"));

    static OsmCells.Building style(JsonObject tags, String type, long osmId, int base, int top, int floorStep,
                                   double latitude) {
        int hash = (int) ((osmId * 0x9E3779B97F4A7C15L) >>> 40);
        double absLat = Math.abs(latitude);
        boolean tropical = absLat < 30;
        String wall;
        String roof;
        OsmCells.RoofShape shape;
        boolean shop = false;
        int floors = Math.max(1, (top - base) / floorStep);
        switch (type) {
            case "house", "detached", "semidetached_house", "terrace", "bungalow", "farm", "cabin", "hut" -> {
                wall = pick(hash, tropical
                        ? new String[]{"white_concrete", "white_terracotta", "yellow_terracotta", "smooth_sandstone"}
                        : new String[]{"bricks", "white_terracotta", "smooth_sandstone", "white_concrete", "calcite"});
                roof = pick(hash >> 3, tropical
                        ? new String[]{"terracotta", "white_concrete"}
                        : new String[]{"terracotta", "deepslate_tiles", "brown_terracotta", "red_terracotta"});
                shape = tropical ? OsmCells.RoofShape.FLAT : OsmCells.RoofShape.HIPPED;
            }
            case "church", "cathedral", "chapel", "mosque", "temple", "synagogue" -> {
                wall = pick(hash, new String[]{"stone_bricks", "calcite", "smooth_sandstone"});
                roof = "deepslate_tiles";
                shape = OsmCells.RoofShape.STEEP;
            }
            case "industrial", "warehouse", "factory", "hangar", "manufacture" -> {
                wall = pick(hash, new String[]{"light_gray_concrete", "white_concrete", "gray_concrete", "iron_block"});
                roof = pick(hash >> 3, new String[]{"gray_concrete", "smooth_stone"});
                shape = OsmCells.RoofShape.FLAT;
            }
            case "retail", "supermarket", "commercial", "office", "school", "university", "hospital", "public",
                 "civic", "government", "train_station", "transportation" -> {
                wall = pick(hash, new String[]{"white_concrete", "light_gray_concrete", "quartz_block", "smooth_sandstone",
                        "light_blue_stained_glass"});
                roof = pick(hash >> 3, new String[]{"gray_concrete", "smooth_stone"});
                shape = OsmCells.RoofShape.FLAT;
                shop = type.matches("retail|supermarket|commercial");
            }
            case "garage", "garages", "shed", "kiosk", "toilets", "service", "greenhouse" -> {
                wall = "greenhouse".equals(type) ? "glass" : pick(hash, new String[]{"spruce_planks", "cobblestone", "light_gray_concrete"});
                roof = "smooth_stone";
                shape = OsmCells.RoofShape.FLAT;
            }
            default -> {
                // Immeubles (apartments, residential, yes…) : pierre claire et mansarde en ville tempérée.
                wall = pick(hash, tropical
                        ? new String[]{"white_concrete", "white_terracotta", "smooth_sandstone", "light_gray_concrete"}
                        : new String[]{"smooth_sandstone", "calcite", "white_terracotta", "smooth_sandstone", "bricks"});
                roof = tropical ? "light_gray_concrete" : pick(hash >> 3, new String[]{"polished_deepslate", "deepslate_tiles", "polished_andesite"});
                shape = tropical || floors < 3 ? OsmCells.RoofShape.FLAT : OsmCells.RoofShape.MANSARD;
                shop = floors >= 4;
            }
        }

        String material = tag(tags, "building:material");
        if (MATERIALS.containsKey(material)) {
            wall = MATERIALS.get(material);
        }
        Integer colour = colour(tag(tags, "building:colour"));
        if (colour != null) {
            wall = nearest(colour);
        }
        String roofMaterial = tag(tags, "roof:material");
        if (MATERIALS.containsKey(roofMaterial)) {
            roof = MATERIALS.get(roofMaterial);
        }
        Integer roofColour = colour(tag(tags, "roof:colour"));
        if (roofColour != null) {
            roof = nearest(roofColour);
        }
        switch (tag(tags, "roof:shape")) {
            case "flat" -> shape = OsmCells.RoofShape.FLAT;
            case "gabled", "hipped", "half-hipped", "pyramidal", "saltbox", "skillion" -> shape = OsmCells.RoofShape.HIPPED;
            case "mansard", "gambrel" -> shape = OsmCells.RoofShape.MANSARD;
            case "dome", "onion", "cone" -> shape = OsmCells.RoofShape.STEEP;
            default -> {
            }
        }
        String window = wall.contains("glass") ? "light_blue_stained_glass" : "glass";
        return new OsmCells.Building(base, top, floorStep, "minecraft:" + wall, "minecraft:" + roof,
                "minecraft:" + window, shape, shop, osmId);
    }

    private static String pick(int hash, String[] options) {
        return options[Math.floorMod(hash, options.length)];
    }

    private static String tag(JsonObject tags, String key) {
        return tags.has(key) ? tags.get(key).getAsString().trim().toLowerCase(Locale.ROOT) : "";
    }

    static Integer colour(String value) {
        if (value.isEmpty()) {
            return null;
        }
        String v = value.replace(" ", "").replace("_", "");
        if (v.startsWith("#")) {
            v = v.substring(1);
            if (v.length() == 3) {
                v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
            }
            try {
                return v.length() == 6 ? Integer.parseInt(v, 16) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return NAMED.get(v);
    }

    /** Bloc de façade dont la couleur est la plus proche (distance pondérée perceptuelle). */
    static String nearest(int rgb) {
        String best = "white_concrete";
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : WALL_COLOURS.entrySet()) {
            int c = entry.getValue();
            double dr = ((rgb >> 16) & 0xFF) - ((c >> 16) & 0xFF);
            double dg = ((rgb >> 8) & 0xFF) - ((c >> 8) & 0xFF);
            double db = (rgb & 0xFF) - (c & 0xFF);
            double distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }
}
