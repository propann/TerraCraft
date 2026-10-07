package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Journal graphique des missions TerraCraft. */
public final class MissionsScreen extends Screen {
    private final JsonArray missions;

    public MissionsScreen(String json) {
        super(Component.literal("Missions TerraCraft"));
        this.missions = JsonParser.parseString(json).getAsJsonArray();
    }

    @Override
    protected void init() {
        int width = Math.min(620, this.width - 24);
        int left = (this.width - width) / 2;
        int top = Math.max(38, (this.height - Math.min(300, missions.size() * 31 + 60)) / 2);
        for (int i = 0; i < missions.size(); i++) {
            JsonObject mission = missions.get(i).getAsJsonObject();
            long progress = mission.get("progress").getAsLong();
            long target = mission.get("target").getAsLong();
            boolean claimed = mission.get("claimed").getAsBoolean();
            int y = top + 24 + (i % 8) * 31;
            if (progress >= target && !claimed) {
                addRenderableWidget(Button.builder(Component.literal("Réclamer"), b -> claim(mission.get("id").getAsString()))
                        .bounds(left + width - 94, y, 84, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(this.width / 2 - 42, Math.min(this.height - 28, top + 24 + Math.min(8, missions.size()) * 31), 84, 20).build());
    }

    private void claim(String id) {
        if (ClientPlayNetworkingBridge.canSend()) {
            ClientPlayNetworkingBridge.send(id);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int width = Math.min(620, this.width - 24);
        int left = (this.width - width) / 2;
        int rows = Math.min(8, missions.size());
        int height = rows * 31 + 58;
        int top = Math.max(38, (this.height - height) / 2);
        extractTransparentBackground(g);
        g.fill(left, top, left + width, top + height, 0xE00E1720);
        g.outline(left, top, width, height, 0xFF395363);
        g.centeredText(font, Component.literal("MISSIONS TERRACRAFT"), this.width / 2, top + 8, 0xFFFFC94A);
        for (int i = 0; i < rows; i++) {
            JsonObject mission = missions.get(i).getAsJsonObject();
            long progress = mission.get("progress").getAsLong();
            long target = mission.get("target").getAsLong();
            boolean claimed = mission.get("claimed").getAsBoolean();
            int y = top + 28 + i * 31;
            int color = claimed ? 0xFF6E8A76 : progress >= target ? 0xFF7EE08A : 0xFFFFFFFF;
            String line = mission.get("title").getAsString() + " — " + mission.get("description").getAsString();
            g.text(font, font.plainSubstrByWidth(line, width - 125), left + 12, y, color, false);
            g.text(font, progress + "/" + target + "  ·  " + mission.get("reward").getAsLong() + " crédits",
                    left + 12, y + 11, 0xFF9BAAB3, false);
        }
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

    private static final class ClientPlayNetworkingBridge {
        static boolean canSend() {
            return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(ClaimMissionPayload.TYPE);
        }

        static void send(String id) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ClaimMissionPayload(id));
        }
    }
}
