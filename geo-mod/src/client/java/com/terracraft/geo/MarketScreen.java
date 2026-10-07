package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Hôtel des ventes graphique, connecté aux mêmes transactions que /hdv. */
public final class MarketScreen extends Screen {
    private final JsonObject data;

    public MarketScreen(String json) {
        super(Component.literal("Hôtel des ventes"));
        data = JsonParser.parseString(json).getAsJsonObject();
    }

    @Override
    protected void init() {
        int width = Math.min(620, this.width - 24);
        int left = (this.width - width) / 2;
        int top = Math.max(38, (this.height - 300) / 2);
        JsonArray rows = data.getAsJsonArray("listings");
        for (int i = 0; i < rows.size(); i++) {
            JsonObject row = rows.get(i).getAsJsonObject();
            if (!row.get("own").getAsBoolean()) {
                int y = top + 42 + i * 30;
                addRenderableWidget(Button.builder(Component.literal("Acheter"), b -> buy(row.get("id").getAsLong()))
                        .bounds(left + width - 92, y, 82, 20).build());
            } else {
                int y = top + 42 + i * 30;
                addRenderableWidget(Button.builder(Component.literal("Retirer"), b -> remove(row.get("id").getAsLong()))
                        .bounds(left + width - 92, y, 82, 20).build());
            }
        }
        int page = data.get("page").getAsInt();
        int pages = data.get("pages").getAsInt();
        int buttonY = Math.min(this.height - 52, top + 42 + rows.size() * 30);
        if (page > 1) {
            addRenderableWidget(Button.builder(Component.literal("← Précédente"), b -> requestPage(page - 1))
                    .bounds(left + 14, buttonY, 100, 20).build());
        }
        if (page < pages) {
            addRenderableWidget(Button.builder(Component.literal("Suivante →"), b -> requestPage(page + 1))
                    .bounds(left + width - 114, buttonY, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(this.width / 2 - 42, buttonY + 24, 84, 20).build());
    }

    private void buy(long id) {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(BuyListingPayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new BuyListingPayload(id));
        }
    }

    private void requestPage(int page) {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RequestMarketPayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new RequestMarketPayload(page));
        }
    }

    private void remove(long id) {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(RemoveListingPayload.TYPE)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new RemoveListingPayload(id));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int width = Math.min(620, this.width - 24);
        int left = (this.width - width) / 2;
        JsonArray rows = data.getAsJsonArray("listings");
        int height = Math.min(330, 80 + rows.size() * 30);
        int top = Math.max(38, (this.height - height) / 2);
        extractTransparentBackground(g);
        g.fill(left, top, left + width, top + height, 0xE00E1720);
        g.outline(left, top, width, height, 0xFF395363);
        g.centeredText(font, Component.literal("HÔTEL DES VENTES  ·  " + data.get("page").getAsInt()
                + "/" + data.get("pages").getAsInt()), this.width / 2, top + 9, 0xFFFFC94A);
        g.text(font, "Solde : " + data.get("balance").getAsLong() + " crédits", left + 14, top + 25, 0xFF7EE08A, false);
        for (int i = 0; i < rows.size(); i++) {
            JsonObject row = rows.get(i).getAsJsonObject();
            int y = top + 46 + i * 30;
            String text = "#" + row.get("id").getAsLong() + "  " + row.get("item").getAsString()
                    + " x" + row.get("count").getAsInt() + "  —  " + row.get("price").getAsLong()
                    + " crédits  ·  " + row.get("seller").getAsString();
            g.text(font, font.plainSubstrByWidth(text, width - 112), left + 14, y, 0xFFFFFFFF, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        super.removed();
        if (minecraft != null && minecraft.player != null) minecraft.mouseHandler.grabMouse();
    }
}
