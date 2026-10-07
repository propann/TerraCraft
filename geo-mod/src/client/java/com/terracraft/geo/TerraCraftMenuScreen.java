package com.terracraft.geo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menu central TerraCraft : accès rapide aux systèmes déjà disponibles. */
public final class TerraCraftMenuScreen extends Screen {
    private static final int PANEL = 0xE00E1720;
    private static final int BORDER = 0xFF395363;
    private static final int GOLD = 0xFFFFC94A;
    private static final int GREY = 0xFF9BAAB3;

    public TerraCraftMenuScreen() {
        super(Component.literal("Menu TerraCraft"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 24);
        int left = (width - panelWidth) / 2;
        int buttonWidth = (panelWidth - 30) / 2;
        int top = Math.max(44, (height - 130) / 2);
        int right = left + 15 + buttonWidth;

        addRenderableWidget(Button.builder(Component.literal("Carte du monde"), b -> {
            minecraft.setScreenAndShow(new WorldMapScreen(false));
        }).bounds(left + 10, top, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Claims  [M / ']"), b -> closeWithMessage(
                "Claims : ouvre M pour la carte Xaero, puis clic droit sur les chunks. La touche ' ouvre le menu des claims."))
                .bounds(right, top, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Hôtel des ventes"), b -> {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RequestMarketPayload.TYPE)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RequestMarketPayload.INSTANCE);
                onClose();
            }
        })
                .bounds(left + 10, top + 24, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Mon argent"), b -> runCommand("argent"))
                .bounds(right, top + 24, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fiche joueur  [K]"), b -> {
            if (minecraft.player != null && ClientPlayNetworkingBridge.canRequestSheet()) {
                ClientPlayNetworkingBridge.requestSheet();
                onClose();
            }
        }).bounds(left + 10, top + 48, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Missions"), b -> {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RequestMissionsPayload.TYPE)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RequestMissionsPayload.INSTANCE);
                onClose();
            }
        })
                .bounds(right, top + 48, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Combinaison spatiale  [J]"), b -> {
            onClose();
            GeoModClient.openSuit(minecraft);
        }).bounds(left + 10, top + 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Coffre du véhicule  [V]"), b -> {
            onClose();
            GeoModClient.openVehicleStorage(minecraft);
        }).bounds(right, top + 72, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Ma ville"), b -> runCommand("ville"))
                .bounds(left + 10, top + 96, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Guide"), b -> runCommand("aide"))
                .bounds(right, top + 96, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(left + 10 + (buttonWidth + 5) / 2, top + 124, buttonWidth, 20).build());
    }

    private void runCommand(String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
        onClose();
    }

    private void closeWithMessage(String message) {
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        int panelWidth = Math.min(460, width - 24);
        int panelHeight = 166;
        int left = (width - panelWidth) / 2;
        int top = Math.max(44, (height - 130) / 2);
        g.fill(left, top - 38, left + panelWidth, top + panelHeight, PANEL);
        g.outline(left, top - 38, panelWidth, panelHeight + 38, BORDER);
        g.centeredText(font, Component.literal("TERRACRAFT"), width / 2, top - 27, GOLD);
        g.centeredText(font, Component.literal("Terre réelle  ·  Survie  ·  Exploration  ·  Espace"), width / 2,
                top - 14, GREY);
        g.centeredText(font, Component.literal("Touche O pour ouvrir ce menu"), width / 2, top + 152, GREY);
        super.extractRenderState(g, mouseX, mouseY, delta);
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

    /** Petit pont pour garder l'écran client indépendant des détails de l'initialiseur. */
    private static final class ClientPlayNetworkingBridge {
        private static boolean canRequestSheet() {
            return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RequestSheetPayload.TYPE);
        }

        private static void requestSheet() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(RequestSheetPayload.INSTANCE);
        }
    }
}
