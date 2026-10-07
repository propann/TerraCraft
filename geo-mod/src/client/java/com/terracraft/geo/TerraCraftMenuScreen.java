package com.terracraft.geo;

import com.mojang.blaze3d.platform.InputConstants;
import com.terracraft.geo.content.ModContent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.util.List;

/**
 * Menu central TerraCraft (touche O) : une tuile par système, avec son icône, une ligne d'explication et sa touche.
 * Même palette que la combinaison spatiale ; la tuile survolée s'éclaire.
 */
public final class TerraCraftMenuScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int TILE_W = 132;
    private static final int TILE_H = 34;
    private static final int GAP = 6;
    private static final int TILE = 0xC0182430;
    private static final int TILE_HOVER = 0xE0233847;

    private record Tile(String title, String subtitle, String key, ItemStack icon, Runnable action) {
    }

    private List<Tile> tiles = List.of();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int columns;

    public TerraCraftMenuScreen() {
        super(Component.literal("Menu TerraCraft"));
    }

    @Override
    protected void init() {
        tiles = List.of(
                new Tile("Carte du monde", "Ta position réelle", "", new ItemStack(Items.FILLED_MAP),
                        () -> minecraft.setScreenAndShow(new WorldMapScreen(false))),
                new Tile("Missions", "Contrats du jour, récompenses", "", new ItemStack(Items.COMPASS),
                        () -> request(RequestMissionsPayload.TYPE, RequestMissionsPayload.INSTANCE)),
                new Tile("Hôtel des ventes", "Acheter, vendre, comptoir", "", new ItemStack(Items.EMERALD),
                        () -> request(RequestMarketPayload.TYPE, RequestMarketPayload.INSTANCE)),
                new Tile("Fiche joueur", "Niveaux, découvertes", "K", new ItemStack(Items.WRITABLE_BOOK),
                        () -> request(RequestSheetPayload.TYPE, RequestSheetPayload.INSTANCE)),
                new Tile("Mon argent", "Solde en crédits", "", new ItemStack(Items.GOLD_INGOT), () -> runCommand("argent")),
                new Tile("Ma ville", "Habitants, trésorerie", "", new ItemStack(Items.BELL), () -> runCommand("ville")),
                new Tile("Combinaison", "Casque, oxygène, jetpack", "J", new ItemStack(ModContent.SPACE_HELMET), () -> {
                    onClose();
                    GeoModClient.openSuit(minecraft);
                }),
                new Tile("Coffre du véhicule", "Voiture, camion, fusée", "V", new ItemStack(ModContent.CAR_CHASSIS), () -> {
                    onClose();
                    GeoModClient.openVehicleStorage(minecraft);
                }),
                new Tile("Carte des étoiles", "Fusée à portée", "", new ItemStack(ModContent.ROCKET_HULL),
                        () -> request(StarMapActionPayload.TYPE, new StarMapActionPayload(-1, (byte) -1, false))),
                new Tile("Plans de fusée", "Améliorations débloquées", "", new ItemStack(ModContent.ROCKET_ENGINE),
                        () -> runCommand("plans")),
                new Tile("Claims", "Protéger son terrain", "M / '", new ItemStack(Items.SHIELD), () -> closeWithMessage(
                        "Claims : ouvre M pour la carte Xaero, puis clic droit sur les chunks. La touche ' ouvre le menu des claims.")),
                new Tile("Guide", "Commandes et touches", "", new ItemStack(Items.BOOK), () -> runCommand("aide")));
        columns = width >= COLUMNS * (TILE_W + GAP) + 24 ? COLUMNS : 2;
        int rows = (tiles.size() + columns - 1) / columns;
        panelWidth = columns * TILE_W + (columns - 1) * GAP + 20;
        panelHeight = 40 + rows * (TILE_H + GAP) + 16;
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
    }

    private int tileX(int index) {
        return left + 10 + (index % columns) * (TILE_W + GAP);
    }

    private int tileY(int index) {
        return top + 40 + (index / columns) * (TILE_H + GAP);
    }

    private int tileAt(double mouseX, double mouseY) {
        for (int i = 0; i < tiles.size(); i++) {
            int x = tileX(i);
            int y = tileY(i);
            if (mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H) {
                return i;
            }
        }
        return -1;
    }

    private <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void request(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type, T payload) {
        if (ClientPlayNetworking.canSend(type)) {
            ClientPlayNetworking.send(payload);
            onClose();
        }
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
        // Ombre portée, panneau, bandeau de titre.
        Ui.frame(g, left, top, panelWidth, panelHeight, 30);
        g.centeredText(font, Component.literal("TERRACRAFT"), width / 2, top + 6, SuitScreen.GOLD);
        g.centeredText(font, Component.literal("Terre réelle · Survie · Villes · Espace"), width / 2, top + 18, SuitScreen.GREY);

        int hovered = tileAt(mouseX, mouseY);
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int x = tileX(i);
            int y = tileY(i);
            boolean hover = i == hovered;
            g.fill(x, y, x + TILE_W, y + TILE_H, hover ? TILE_HOVER : TILE);
            g.outline(x, y, TILE_W, TILE_H, hover ? SuitScreen.CYAN : SuitScreen.SLOT_EDGE);
            if (hover) {
                g.fill(x, y, x + 2, y + TILE_H, SuitScreen.CYAN);
            }
            g.fill(x + 6, y + 7, x + 26, y + 27, SuitScreen.SLOT_BG);
            g.item(tile.icon(), x + 8, y + 9);
            int textWidth = TILE_W - 36 - (tile.key().isEmpty() ? 0 : font.width(tile.key()) + 8);
            g.text(font, font.plainSubstrByWidth(tile.title(), textWidth), x + 32, y + 7, hover ? SuitScreen.GOLD : SuitScreen.TEXT, false);
            g.text(font, font.plainSubstrByWidth(tile.subtitle(), TILE_W - 36), x + 32, y + 19, SuitScreen.GREY, false);
            if (!tile.key().isEmpty()) {
                int kw = font.width(tile.key()) + 6;
                int kx = x + TILE_W - kw - 4;
                g.fill(kx, y + 5, kx + kw, y + 16, SuitScreen.SLOT_BG);
                g.outline(kx, y + 5, kw, 11, SuitScreen.SLOT_EDGE);
                g.text(font, tile.key(), kx + 3, y + 7, SuitScreen.CYAN, false);
            }
        }
        g.centeredText(font, Component.literal("O : ouvrir ce menu · Échap : fermer"), width / 2, top + panelHeight - 12, SuitScreen.GREY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int index = tileAt(event.x(), event.y());
            if (index >= 0) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                tiles.get(index).action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
