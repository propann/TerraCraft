package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Journal des missions : métier, contrats du jour, missions en cours et missions terminées. */
public final class MissionsScreen extends Screen {
    private static final int ROW = 24;
    private static final int GREEN = 0xFF7EE08A;

    private final String job;
    private final List<JsonObject> contracts = new ArrayList<>();
    private final List<JsonObject> missions = new ArrayList<>();
    private int done;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int missionRows;

    public MissionsScreen(String json) {
        super(Component.literal("Missions TerraCraft"));
        JsonElement root = JsonParser.parseString(json);
        JsonArray allMissions;
        if (root.isJsonArray()) {
            allMissions = root.getAsJsonArray();
            job = "";
        } else {
            JsonObject object = root.getAsJsonObject();
            allMissions = object.getAsJsonArray("missions");
            object.getAsJsonArray("contracts").forEach(e -> contracts.add(e.getAsJsonObject()));
            job = object.has("job") ? object.get("job").getAsString() : "";
        }
        for (JsonElement element : allMissions) {
            JsonObject mission = element.getAsJsonObject();
            if (mission.get("claimed").getAsBoolean()) {
                done++;
            } else {
                missions.add(mission);
            }
        }
        // Les missions prêtes à réclamer d'abord.
        missions.sort((a, b) -> Boolean.compare(ready(b), ready(a)));
    }

    private static boolean ready(JsonObject mission) {
        return !mission.get("claimed").getAsBoolean() && mission.get("progress").getAsLong() >= mission.get("target").getAsLong();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, width - 16);
        panelHeight = Math.min(height - 16, 300);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int contractsTop = top + 40;
        int missionsTop = contractsTop + contracts.size() * ROW + 16;
        missionRows = Math.max(0, Math.min(missions.size(), (top + panelHeight - 40 - missionsTop) / ROW));
        for (int i = 0; i < contracts.size(); i++) {
            claimButton(contracts.get(i), contractsTop + i * ROW);
        }
        for (int i = 0; i < missionRows; i++) {
            claimButton(missions.get(i), missionsTop + i * ROW);
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 - 42, top + panelHeight - 26, 84, 20).build());
    }

    private void claimButton(JsonObject mission, int y) {
        if (ready(mission)) {
            String id = mission.get("id").getAsString();
            addRenderableWidget(Button.builder(Component.literal("Réclamer"), b -> {
                if (ClientPlayNetworking.canSend(ClaimMissionPayload.TYPE)) {
                    ClientPlayNetworking.send(new ClaimMissionPayload(id));
                }
            }).bounds(left + panelWidth - 82, y, 74, 18).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        g.fill(left, top, left + panelWidth, top + panelHeight, SuitScreen.PANEL);
        g.fillGradient(left, top, left + panelWidth, top + 18, SuitScreen.HEADER_TOP, SuitScreen.HEADER_BOTTOM);
        g.outline(left, top, panelWidth, panelHeight, SuitScreen.BORDER);
        g.text(font, Component.literal("MISSIONS"), left + 8, top + 5, SuitScreen.GOLD, false);
        if (!job.isEmpty()) {
            Component jobLine = Component.literal("Métier : " + job);
            g.text(font, font.plainSubstrByWidth(jobLine.getString(), panelWidth - 90), left + panelWidth - 8
                    - Math.min(font.width(jobLine), panelWidth - 90), top + 5, SuitScreen.CYAN, false);
        }
        int contractsTop = top + 40;
        g.text(font, Component.literal("Contrats du jour (changent à minuit)"), left + 8, contractsTop - 12, SuitScreen.GOLD, false);
        for (int i = 0; i < contracts.size(); i++) {
            row(g, contracts.get(i), contractsTop + i * ROW);
        }
        int missionsTop = contractsTop + contracts.size() * ROW + 16;
        g.text(font, Component.literal("Missions" + (done > 0 ? "  ·  " + done + " terminée(s)" : "")), left + 8,
                missionsTop - 12, SuitScreen.GOLD, false);
        for (int i = 0; i < missionRows; i++) {
            row(g, missions.get(i), missionsTop + i * ROW);
        }
        if (missions.size() > missionRows) {
            g.text(font, Component.literal("… et " + (missions.size() - missionRows) + " autre(s) : /missions dans le chat"),
                    left + 8, missionsTop + missionRows * ROW, SuitScreen.GREY, false);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void row(GuiGraphicsExtractor g, JsonObject mission, int y) {
        long progress = mission.get("progress").getAsLong();
        long target = mission.get("target").getAsLong();
        boolean claimed = mission.get("claimed").getAsBoolean();
        int color = claimed ? 0xFF6E8A76 : progress >= target ? GREEN : SuitScreen.TEXT;
        int textWidth = panelWidth - 100;
        String line = (claimed ? "✓ " : "") + mission.get("title").getAsString() + " — " + mission.get("description").getAsString();
        g.text(font, font.plainSubstrByWidth(line, textWidth), left + 8, y, color, false);
        // Barre de progression et récompense.
        int bar = 120;
        g.fill(left + 8, y + 11, left + 8 + bar, y + 14, SuitScreen.SLOT_BG);
        g.fill(left + 8, y + 11, left + 8 + (int) (bar * Math.min(1.0, (double) progress / Math.max(1, target))), y + 14,
                progress >= target ? GREEN : SuitScreen.CYAN);
        g.text(font, progress + "/" + target + "  ·  " + mission.get("reward").getAsLong() + " crédits", left + 134, y + 9,
                SuitScreen.GREY, false);
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
