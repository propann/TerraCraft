package com.terracraft.geo;

import com.mojang.blaze3d.platform.InputConstants;
import com.terracraft.geo.world.WebMercator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Carte du monde en jeu : glisser pour se déplacer, molette pour zoomer, clic pour choisir
 * le point d'arrivée, recherche de ville ou de rue, puis « Atterrir ici ».
 */
public final class WorldMapScreen extends Screen {
    private static final int TOP_BAR = 28;
    private static final int BOTTOM_BAR = 30;
    private static final int RESULT_HEIGHT = 20;
    private static final int OCEAN = 0xFFAAD3DF;
    private static final double MAX_ZOOM = MapTileCache.MAX_ZOOM;
    /** Distance (en unités GUI) sous laquelle un appui-relâché compte comme un clic. */
    private static final double CLICK_TOLERANCE = 4;

    private final boolean required;
    // État de la vue : centre en coordonnées Mercator normalisées et zoom continu.
    private static double centerU = 0.5;
    private static double centerV = WebMercator.v(30);
    private static double zoom = 2;

    private EditBox search;
    private Button confirm;
    private final List<Button> resultButtons = new ArrayList<>();
    private String status = "";

    private boolean hasSelection;
    private double selectedLatitude;
    private double selectedLongitude;
    private String selectedLabel = "";

    private boolean draggingMap;
    private double dragDistance;

    public WorldMapScreen(boolean required) {
        super(Component.literal("Choisir son point de départ"));
        this.required = required;
    }

    @Override
    protected void init() {
        int searchWidth = Math.min(240, this.width - 110);
        String previous = search != null ? search.getValue() : "";
        search = new EditBox(this.font, 8, 5, searchWidth, 18, Component.literal("Rechercher un lieu"));
        search.setHint(Component.literal("Ville, rue, monument…"));
        search.setMaxLength(120);
        search.setValue(previous);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Rechercher"), b -> runSearch())
                .bounds(12 + searchWidth, 4, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoomAt(mapCenterX(), mapCenterY(), 1))
                .bounds(this.width - 26, TOP_BAR + 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> zoomAt(mapCenterX(), mapCenterY(), -1))
                .bounds(this.width - 26, TOP_BAR + 28, 20, 20).build());

        confirm = addRenderableWidget(Button.builder(Component.literal("Atterrir ici"), b -> land())
                .bounds(this.width - 128, this.height - BOTTOM_BAR + 5, 120, 20).build());
        confirm.active = hasSelection;
        if (required) {
            // Échap est bloqué tant que le premier choix n'est pas fait : on garde une sortie.
            addRenderableWidget(Button.builder(Component.literal("Quitter"), b ->
                            this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE))
                    .bounds(this.width - 200, this.height - BOTTOM_BAR + 5, 66, 20).build());
        }
        resultButtons.clear();
        clampView();
    }

    // --- Géométrie de la vue -------------------------------------------------------------

    private int mapTop() {
        return TOP_BAR;
    }

    private int mapBottom() {
        return this.height - BOTTOM_BAR;
    }

    private double mapCenterX() {
        return this.width / 2.0;
    }

    private double mapCenterY() {
        return (mapTop() + mapBottom()) / 2.0;
    }

    /** Taille d'une tuile en unités GUI au zoom entier, pour un rendu net (1 texel = 1 pixel). */
    private double baseTileSize() {
        return 256.0 / Math.max(1, this.minecraft.getWindow().getGuiScale());
    }

    /** Largeur du monde entier, en unités GUI, au zoom courant. */
    private double worldSize() {
        return baseTileSize() * Math.pow(2, zoom);
    }

    private double minZoom() {
        double needed = (mapBottom() - mapTop()) / baseTileSize();
        return Math.max(0, Math.log(needed) / Math.log(2));
    }

    private void clampView() {
        zoom = Math.max(minZoom(), Math.min(MAX_ZOOM, zoom));
        double halfHeight = (mapBottom() - mapTop()) / 2.0 / worldSize();
        centerV = Math.max(halfHeight, Math.min(1 - halfHeight, centerV));
        centerU = centerU - Math.floor(centerU);
    }

    private double screenToU(double x) {
        return centerU + (x - mapCenterX()) / worldSize();
    }

    private double screenToV(double y) {
        return centerV + (y - mapCenterY()) / worldSize();
    }

    private boolean inMap(double x, double y) {
        return y >= mapTop() && y < mapBottom();
    }

    private void zoomAt(double x, double y, double delta) {
        double u = screenToU(x);
        double v = screenToV(y);
        zoom = Math.max(minZoom(), Math.min(MAX_ZOOM, zoom + delta));
        centerU = u - (x - mapCenterX()) / worldSize();
        centerV = v - (y - mapCenterY()) / worldSize();
        clampView();
    }

    private void flyTo(double latitude, double longitude, double targetZoom) {
        centerU = WebMercator.u(longitude);
        centerV = WebMercator.v(latitude);
        zoom = targetZoom;
        clampView();
    }

    // --- Rendu ---------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int top = mapTop();
        int bottom = mapBottom();
        graphics.fill(0, top, this.width, bottom, OCEAN);
        graphics.enableScissor(0, top, this.width, bottom);
        drawTiles(graphics);
        graphics.disableScissor();
    }

    private void drawTiles(GuiGraphicsExtractor graphics) {
        MapTileCache tiles = MapTileCache.get();
        int tileZoom = (int) Math.max(0, Math.min(MapTileCache.MAX_ZOOM, Math.round(zoom)));
        int count = 1 << tileZoom;
        double tileSize = worldSize() / count;
        double left = screenToU(0) * count;
        double topTile = screenToV(mapTop()) * count;
        int firstX = (int) Math.floor(left);
        int firstY = Math.max(0, (int) Math.floor(topTile));
        int lastX = (int) Math.floor(screenToU(this.width) * count);
        int lastY = Math.min(count - 1, (int) Math.floor(screenToV(mapBottom()) * count));
        double originX = mapCenterX() - (centerU * count) * tileSize;
        double originY = mapCenterY() - (centerV * count) * tileSize;
        for (int ty = firstY; ty <= lastY; ty++) {
            for (int tx = firstX; tx <= lastX; tx++) {
                int x0 = (int) Math.floor(originX + tx * tileSize);
                int x1 = (int) Math.floor(originX + (tx + 1) * tileSize);
                int y0 = (int) Math.floor(originY + ty * tileSize);
                int y1 = (int) Math.floor(originY + (ty + 1) * tileSize);
                drawTile(graphics, tiles, tileZoom, Math.floorMod(tx, count), ty, x0, y0, x1, y1);
            }
        }
    }

    /** Dessine la tuile, ou à défaut un morceau agrandi d'une tuile parente déjà chargée. */
    private static void drawTile(GuiGraphicsExtractor graphics, MapTileCache tiles, int z, int x, int y,
                                 int x0, int y0, int x1, int y1) {
        Identifier texture = tiles.request(z, x, y);
        if (texture != null) {
            graphics.blit(texture, x0, y0, x1, y1, 0, 1, 0, 1);
            return;
        }
        for (int up = 1; up <= Math.min(z, 6); up++) {
            Identifier parent = tiles.peek(z - up, x >> up, y >> up);
            if (parent != null) {
                float part = 1f / (1 << up);
                float u0 = (x & ((1 << up) - 1)) * part;
                float v0 = (y & ((1 << up) - 1)) * part;
                graphics.blit(parent, x0, y0, x1, y1, u0, u0 + part, v0, v0 + part);
                return;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, TOP_BAR, 0xE0101820);
        graphics.fill(0, mapBottom(), this.width, this.height, 0xE0101820);

        if (hasSelection) {
            drawMarker(graphics);
        }

        String attribution = "© OpenStreetMap contributors";
        int attributionWidth = this.font.width(attribution);
        graphics.fill(this.width - attributionWidth - 6, mapBottom() - 12, this.width, mapBottom(), 0xB0FFFFFF);
        graphics.text(this.font, attribution, this.width - attributionWidth - 3, mapBottom() - 10, 0xFF333333, false);

        int infoX = 8;
        int infoY = this.height - BOTTOM_BAR + 6;
        if (hasSelection) {
            graphics.text(this.font, selectedLabel, infoX, infoY, 0xFFFFFFFF, true);
            graphics.text(this.font, String.format(Locale.ROOT, "%.5f, %.5f", selectedLatitude, selectedLongitude),
                    infoX, infoY + 11, 0xFFA0C4D0, false);
        } else {
            graphics.text(this.font, required ? "Choisis ton point de départ pour commencer à jouer."
                    : "Choisis un nouveau point de départ.", infoX, infoY, 0xFFFFFFFF, true);
            graphics.text(this.font, "Glisser : déplacer · Molette : zoom · Clic : choisir",
                    infoX, infoY + 11, 0xFFA0C4D0, false);
        }

        if (!status.isEmpty()) {
            int statusX = 104 + Math.min(240, this.width - 110);
            graphics.text(this.font, status, statusX, 10, 0xFFFFD27F, true);
        }
        if (!resultButtons.isEmpty()) {
            Button first = resultButtons.getFirst();
            graphics.fill(first.getX() - 2, first.getY() - 2, first.getX() + first.getWidth() + 2,
                    first.getY() + resultButtons.size() * RESULT_HEIGHT, 0xD0101820);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawMarker(GuiGraphicsExtractor graphics) {
        double u = WebMercator.u(selectedLongitude);
        double v = WebMercator.v(selectedLatitude);
        double du = u - centerU;
        du -= Math.round(du);
        int x = (int) Math.round(mapCenterX() + du * worldSize());
        int y = (int) Math.round(mapCenterY() + (v - centerV) * worldSize());
        if (y < mapTop() || y >= mapBottom()) {
            return;
        }
        graphics.fill(x - 1, y - 9, x + 2, y + 10, 0xFF000000);
        graphics.fill(x - 9, y - 1, x + 10, y + 2, 0xFF000000);
        graphics.fill(x, y - 8, x + 1, y + 9, 0xFFE53935);
        graphics.fill(x - 8, y, x + 9, y + 1, 0xFFE53935);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFFE53935);
        graphics.outline(x - 4, y - 4, 9, 9, 0xFFFFFFFF);
    }

    // --- Entrées -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && inMap(event.x(), event.y())) {
            search.setFocused(false);
            setFocused(null);
            draggingMap = true;
            dragDistance = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingMap) {
            centerU -= dx / worldSize();
            centerV -= dy / worldSize();
            dragDistance += Math.abs(dx) + Math.abs(dy);
            clampView();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingMap && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingMap = false;
            if (dragDistance < CLICK_TOLERANCE) {
                double u = screenToU(event.x());
                double v = screenToV(event.y());
                if (v > 0 && v < 1) {
                    select(WebMercator.latitude(v), WebMercator.longitude(u - Math.floor(u)), "Point choisi sur la carte");
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inMap(x, y) && scrollY != 0) {
            zoomAt(x, y, Math.signum(scrollY) * 0.5);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && event.isConfirmation()) {
            runSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !required;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Actions -------------------------------------------------------------------------

    private void select(double latitude, double longitude, String label) {
        hasSelection = true;
        selectedLatitude = latitude;
        selectedLongitude = longitude;
        selectedLabel = label;
        confirm.active = true;
    }

    private void runSearch() {
        String query = search.getValue().strip();
        if (query.length() < 2) {
            return;
        }
        status = "Recherche…";
        clearResults();
        Geocoder.search(query).whenComplete((places, error) -> this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() != this) {
                return;
            }
            if (error != null) {
                status = "Recherche indisponible (" + Geocoder.describe(error) + ")";
                return;
            }
            status = places.isEmpty() ? "Aucun résultat" : "";
            showResults(places);
        }));
    }

    private void clearResults() {
        resultButtons.forEach(this::removeWidget);
        resultButtons.clear();
    }

    private void showResults(List<Geocoder.Place> places) {
        clearResults();
        int width = Math.min(320, this.width - 16);
        int y = TOP_BAR + 4;
        for (Geocoder.Place place : places) {
            String text = place.detail().isEmpty() ? place.name() : place.name() + " — " + place.detail();
            Button button = Button.builder(Component.literal(this.font.plainSubstrByWidth(text, width - 10)), b -> {
                clearResults();
                double span = Math.max(place.north() - place.south(), (place.east() - place.west()) * 0.5);
                double targetZoom = span > 0 ? Math.log(180.0 / span) / Math.log(2) + 1 : 14;
                flyTo(place.latitude(), place.longitude(), Math.max(3, Math.min(16, targetZoom)));
                select(place.latitude(), place.longitude(), place.name());
            }).bounds(8, y, width, RESULT_HEIGHT - 2).build();
            resultButtons.add(addRenderableWidget(button));
            y += RESULT_HEIGHT;
        }
    }

    private void land() {
        if (!hasSelection) {
            return;
        }
        GeoModClient.sendStartPoint(selectedLatitude, selectedLongitude, selectedLabel);
    }
}
