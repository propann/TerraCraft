package com.terracraft.geo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carte des étoiles : la Terre, la Lune et Mars avec leurs orbites, les routes et leur coût en doses. Chaque
 * destination est verte (accessible), rouge (bloquée : la raison s'affiche) ou dorée (ici). Les règles viennent du
 * serveur ; l'écran ne fait que les montrer et envoyer le choix.
 */
public final class StarMapScreen extends Screen {
    private static final int GREEN = 0xFF7EE08A;
    private static final int SPACE = 0xFF05070F;
    private static final int SIDE = 150;

    /** Corps célestes : centre (fraction de la carte), rayon du disque, rayon de l'orbite, angle du point d'orbite. */
    private record Body(byte surface, byte orbit, double fx, double fy, int radius, int ring, double angle, int colour, int shade) {
    }

    private static final Body[] BODIES = {
            new Body(Space.EARTH, Space.ORBIT_ID, 0.20, 0.64, 15, 28, -55, 0xFF2F6FD0, 0xFF1C3F7A),
            new Body(Space.MOON_ID, Space.MOON_ORBIT_ID, 0.50, 0.28, 8, 17, 150, 0xFFB8B8C0, 0xFF6E6E78),
            new Body(Space.MARS_ID, Space.MARS_ORBIT_ID, 0.83, 0.66, 11, 21, 215, 0xFFC0603A, 0xFF7A3520),
    };
    private static final Map<Byte, String> SHORT = Map.of(Space.EARTH, "Terre", Space.ORBIT_ID, "Orbite",
            Space.MOON_ORBIT_ID, "Orbite lunaire", Space.MOON_ID, "Lune", Space.MARS_ORBIT_ID, "Orbite de Mars",
            Space.MARS_ID, "Mars");

    private final JsonObject data;
    private final Map<Byte, JsonObject> destinations = new HashMap<>();
    private final Map<Byte, int[]> nodes = new HashMap<>();
    private final byte here;
    private byte selected;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private Button course;
    private Button launch;

    public StarMapScreen(String json) {
        super(Component.literal("Carte des étoiles"));
        data = JsonParser.parseString(json).getAsJsonObject();
        here = data.get("here").getAsByte();
        for (JsonElement element : data.getAsJsonArray("destinations")) {
            JsonObject destination = element.getAsJsonObject();
            destinations.put(destination.get("id").getAsByte(), destination);
        }
        byte target = data.get("target").getAsByte();
        selected = target != here && destinations.containsKey(target) ? target : -1;
        if (selected < 0) {
            for (Map.Entry<Byte, JsonObject> entry : destinations.entrySet()) {
                if (entry.getKey() != here && !entry.getValue().has("problem")) {
                    selected = entry.getKey();
                    break;
                }
            }
        }
    }

    private int mapLeft() {
        return left + 6;
    }

    private int mapTop() {
        return top + 22;
    }

    private int mapWidth() {
        return panelWidth - SIDE - 12;
    }

    private int mapHeight() {
        return panelHeight - 28;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 16);
        panelHeight = Math.min(270, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        nodes.clear();
        for (Body body : BODIES) {
            int cx = mapLeft() + (int) (body.fx * mapWidth());
            int cy = mapTop() + (int) (body.fy * mapHeight());
            nodes.put(body.surface, new int[]{cx, cy});
            double a = Math.toRadians(body.angle);
            nodes.put(body.orbit, new int[]{cx + (int) Math.round(body.ring * Math.cos(a)), cy + (int) Math.round(body.ring * Math.sin(a))});
        }
        int sx = left + panelWidth - SIDE;
        int rocket = data.get("rocket").getAsInt();
        course = addRenderableWidget(Button.builder(Component.literal("Mettre le cap"), b -> send(rocket, false))
                .bounds(sx, top + panelHeight - 68, SIDE - 6, 18).build());
        launch = addRenderableWidget(Button.builder(Component.literal("Décoller"), b -> {
            send(rocket, true);
            onClose();
        }).bounds(sx, top + panelHeight - 46, SIDE - 6, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(sx, top + panelHeight - 24, SIDE - 6, 18).build());
    }

    private void send(int rocket, boolean takeOff) {
        if (selected >= 0 && ClientPlayNetworking.canSend(StarMapActionPayload.TYPE)) {
            ClientPlayNetworking.send(new StarMapActionPayload(rocket, selected, takeOff));
        }
    }

    private String problem(byte id) {
        JsonObject destination = destinations.get(id);
        return destination == null || !destination.has("problem") ? null : destination.get("problem").getAsString();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        Ui.frame(g, left, top, panelWidth, panelHeight, 18);
        g.text(font, Component.literal("CARTE DES ÉTOILES · NAVIGATION"), left + 8, top + 5, SuitScreen.GOLD, false);

        int mx = mapLeft();
        int my = mapTop();
        g.fill(mx, my, mx + mapWidth(), my + mapHeight(), SPACE);
        g.outline(mx - 1, my - 1, mapWidth() + 2, mapHeight() + 2, SuitScreen.SLOT_EDGE);
        stars(g, mx, my);
        // Le Soleil, en haut à gauche : la face éclairée des planètes est tournée vers lui.
        disc(g, mx + 4, my + 4, 12, 0xFFFFD34A, 0xFFFFD34A, false);
        for (int[] route : routes()) {
            route(g, (byte) route[0], (byte) route[1], route[2]);
        }
        for (Body body : BODIES) {
            int[] c = nodes.get(body.surface);
            ring(g, c[0], c[1], body.ring, 0x55FFFFFF);
            disc(g, c[0], c[1], body.radius, body.colour, body.shade, true);
            if (body.surface == Space.EARTH) {
                g.fill(c[0] - 6, c[1] - 5, c[0] - 1, c[1] + 2, 0xFF3E9A4E);
                g.fill(c[0] + 2, c[1] + 3, c[0] + 7, c[1] + 7, 0xFF3E9A4E);
            }
        }
        byte hovered = -1;
        for (Map.Entry<Byte, int[]> entry : nodes.entrySet()) {
            byte id = entry.getKey();
            int[] p = entry.getValue();
            node(g, id, p[0], p[1]);
            if (Math.abs(mouseX - p[0]) <= 6 && Math.abs(mouseY - p[1]) <= 6) {
                hovered = id;
            }
        }
        side(g);
        if (course != null) {
            course.active = selected >= 0 && selected != here;
            launch.active = selected >= 0 && problem(selected) == null && data.get("pilot").getAsBoolean()
                    && data.get("complete").getAsBoolean();
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (hovered >= 0) {
            g.setTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
        }
    }

    private List<int[]> routes() {
        List<int[]> routes = new ArrayList<>();
        for (JsonElement element : data.getAsJsonArray("routes")) {
            var r = element.getAsJsonArray();
            routes.add(new int[]{r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt()});
        }
        return routes;
    }

    private void stars(GuiGraphicsExtractor g, int mx, int my) {
        long seed = 0x5DEECE66DL;
        long blink = System.currentTimeMillis() / 700;
        for (int i = 0; i < 150; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) Math.floorMod(seed >>> 17, mapWidth());
            int y = (int) Math.floorMod(seed >>> 41, mapHeight());
            boolean bright = (seed >>> 60) == 0;
            int alpha = (i + blink) % 9 == 0 ? 0x50 : bright ? 0xFF : 0x90;
            g.fill(mx + x, my + y, mx + x + 1, my + y + 1, alpha << 24 | 0xDDE6FF);
        }
    }

    private static void disc(GuiGraphicsExtractor g, int cx, int cy, int r, int lit, int dark, boolean shaded) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            if (!shaded) {
                g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, lit);
                continue;
            }
            // Nuit du côté opposé au Soleil (en bas à droite).
            int split = cx + (int) Math.round(half * 0.25) - dy / 3;
            g.fill(cx - half, cy + dy, Math.min(split, cx + half + 1), cy + dy + 1, lit);
            if (split < cx + half + 1) {
                g.fill(Math.max(split, cx - half), cy + dy, cx + half + 1, cy + dy + 1, dark);
            }
        }
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        int steps = r * 5;
        for (int i = 0; i < steps; i += 2) {
            double a = 2 * Math.PI * i / steps;
            int x = cx + (int) Math.round(r * Math.cos(a));
            int y = cy + (int) Math.round(r * Math.sin(a));
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private void route(GuiGraphicsExtractor g, byte from, byte to, int cost) {
        int[] a = nodes.get(from);
        int[] b = nodes.get(to);
        if (a == null || b == null) {
            return;
        }
        boolean active = from == here || to == here;
        boolean chosen = active && (from == selected || to == selected);
        int colour = chosen ? SuitScreen.CYAN : active ? 0xB0FFFFFF : 0x45FFFFFF;
        double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        long phase = System.currentTimeMillis() / 120;
        for (int i = 0; i < length; i++) {
            if ((i + (chosen ? phase : 0)) % 4 >= 2) {
                continue;
            }
            int x = a[0] + (int) Math.round((b[0] - a[0]) * i / length);
            int y = a[1] + (int) Math.round((b[1] - a[1]) * i / length);
            g.fill(x, y, x + 1, y + 1, colour);
        }
        String label = cost + "";
        int lx = (a[0] + b[0]) / 2 - font.width(label) / 2;
        int ly = (a[1] + b[1]) / 2 - 4;
        g.fill(lx - 2, ly - 1, lx + font.width(label) + 1, ly + 8, 0xC005070F);
        g.text(font, label, lx, ly, active ? SuitScreen.TEXT : SuitScreen.GREY, false);
    }

    private void node(GuiGraphicsExtractor g, byte id, int x, int y) {
        boolean isHere = id == here;
        String problem = problem(id);
        int colour = isHere ? SuitScreen.GOLD : problem == null ? GREEN : SuitScreen.RED;
        if (id == selected) {
            g.outline(x - 6, y - 6, 13, 13, SuitScreen.CYAN);
        }
        if (isHere && System.currentTimeMillis() / 400 % 2 == 0) {
            g.outline(x - 8, y - 8, 17, 17, SuitScreen.GOLD);
        }
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
        g.fill(x - 2, y - 2, x + 3, y + 3, colour);
        JsonObject destination = destinations.get(id);
        String label = SHORT.getOrDefault(id, "?") + (destination != null && destination.get("station").getAsBoolean() ? " ⌂" : "");
        int lx = Math.max(mapLeft() + 1, Math.min(x - font.width(label) / 2, mapLeft() + mapWidth() - font.width(label) - 1));
        g.text(font, label, lx, y + 7, isHere ? SuitScreen.GOLD : SuitScreen.TEXT, true);
    }

    private List<FormattedCharSequence> tooltip(byte id) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        JsonObject destination = destinations.get(id);
        if (destination == null) {
            return lines;
        }
        lines.add(Component.literal(capitalized(destination.get("name").getAsString())).withColor(SuitScreen.GOLD).getVisualOrderText());
        if (id == here) {
            lines.add(Component.literal("Ta fusée est ici.").withColor(SuitScreen.TEXT).getVisualOrderText());
        } else {
            lines.add(Component.literal("Coût : " + destination.get("cost").getAsInt() + " dose(s)").withColor(SuitScreen.TEXT).getVisualOrderText());
            String problem = problem(id);
            lines.addAll(font.split(Component.literal(problem == null ? "Accessible" : problem)
                    .withColor(problem == null ? GREEN : SuitScreen.RED), 200));
        }
        if (destination.get("station").getAsBoolean()) {
            lines.add(Component.literal("⌂ Ta station ou ta base est ici").withColor(SuitScreen.CYAN).getVisualOrderText());
        }
        return lines;
    }

    private void side(GuiGraphicsExtractor g) {
        int x = left + panelWidth - SIDE;
        int y = top + 24;
        int w = SIDE - 8;
        g.text(font, Component.literal("FUSÉE"), x, y, SuitScreen.GOLD, false);
        g.text(font, font.plainSubstrByWidth(data.get("tier").getAsString(), w), x, y + 11, SuitScreen.TEXT, false);
        int fuel = data.get("fuel").getAsInt();
        int max = Math.max(1, data.get("maxFuel").getAsInt());
        g.text(font, Component.literal("Carburant " + fuel + "/" + max), x, y + 24, SuitScreen.GREY, false);
        Ui.bar(g, x, y + 35, w, 5, (double) fuel / max, fuel > 0 ? SuitScreen.CYAN : SuitScreen.RED);
        g.text(font, font.plainSubstrByWidth("Ici : " + SHORT.getOrDefault(here, "?"), w), x, y + 46, SuitScreen.GOLD, false);

        int sy = y + 64;
        g.fill(x - 2, sy - 3, x + w + 2, sy - 2, SuitScreen.SLOT_EDGE);
        if (selected < 0) {
            g.text(font, Component.literal("Choisis une destination"), x, sy, SuitScreen.GREY, false);
            g.text(font, Component.literal("sur la carte."), x, sy + 10, SuitScreen.GREY, false);
            return;
        }
        JsonObject destination = destinations.get(selected);
        g.text(font, Component.literal("DESTINATION"), x, sy, SuitScreen.GOLD, false);
        g.text(font, font.plainSubstrByWidth(capitalized(destination.get("name").getAsString()), w), x, sy + 11, SuitScreen.CYAN, false);
        g.text(font, Component.literal("Coût : " + destination.get("cost").getAsInt() + " dose(s)"), x, sy + 22, SuitScreen.TEXT, false);
        String problem = problem(selected);
        List<FormattedCharSequence> lines = font.split(Component.literal(problem == null
                ? (data.get("pilot").getAsBoolean() ? "Prête au décollage." : "Prête : monte à bord pour décoller.") : problem), w);
        int ly = sy + 35;
        for (FormattedCharSequence line : lines) {
            if (ly > top + panelHeight - 80) {
                break;
            }
            g.text(font, line, x, ly, problem == null ? GREEN : SuitScreen.RED, false);
            ly += 10;
        }
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Map.Entry<Byte, int[]> entry : nodes.entrySet()) {
                int[] p = entry.getValue();
                if (entry.getKey() != here && Math.abs(event.x() - p[0]) <= 6 && Math.abs(event.y() - p[1]) <= 6) {
                    selected = entry.getKey();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.player != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }
}
