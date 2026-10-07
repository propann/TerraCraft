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

/** Atelier de station : plans de fusée connus, matériaux et installation sur la fusée garée à côté. */
public final class WorkshopScreen extends Screen {
    private static final int ROW = 46;
    private static final int GREEN = 0xFF7EE08A;

    private final JsonObject data;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public WorkshopScreen(String json) {
        super(Component.literal("Atelier de station"));
        data = JsonParser.parseString(json).getAsJsonObject();
    }

    private JsonArray plans() {
        return data.getAsJsonArray("plans");
    }

    @Override
    protected void init() {
        panelWidth = Math.min(460, width - 16);
        panelHeight = Math.min(height - 16, 44 + plans().size() * ROW + 30);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        for (int i = 0; i < plans().size(); i++) {
            JsonObject plan = plans().get(i).getAsJsonObject();
            if (plan.get("installed").getAsBoolean() || !plan.get("known").getAsBoolean()) {
                continue;
            }
            String id = plan.get("id").getAsString();
            Button button = Button.builder(Component.literal("Installer"), b -> {
                if (ClientPlayNetworking.canSend(InstallUpgradePayload.TYPE)) {
                    ClientPlayNetworking.send(new InstallUpgradePayload(id));
                }
            }).bounds(left + panelWidth - 82, top + 40 + i * ROW, 74, 18).build();
            button.active = plan.get("canInstall").getAsBoolean();
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(width / 2 - 42, top + panelHeight - 24, 84, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        g.fill(left, top, left + panelWidth, top + panelHeight, SuitScreen.PANEL);
        g.fillGradient(left, top, left + panelWidth, top + 18, SuitScreen.HEADER_TOP, SuitScreen.HEADER_BOTTOM);
        g.outline(left, top, panelWidth, panelHeight, SuitScreen.BORDER);
        g.text(font, Component.literal("ATELIER DE STATION · PLANS DE FUSÉE"), left + 8, top + 5, SuitScreen.GOLD, false);
        boolean rocket = data.get("rocket").getAsBoolean();
        g.text(font, font.plainSubstrByWidth((rocket ? "Fusée : " : "") + data.get("status").getAsString(), panelWidth - 16),
                left + 8, top + 23, rocket ? SuitScreen.CYAN : SuitScreen.RED, false);
        for (int i = 0; i < plans().size(); i++) {
            JsonObject plan = plans().get(i).getAsJsonObject();
            int y = top + 40 + i * ROW;
            boolean known = plan.get("known").getAsBoolean();
            boolean installed = plan.get("installed").getAsBoolean();
            g.fill(left + 4, y - 3, left + panelWidth - 4, y + ROW - 7, i % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            String title = (installed ? "✓ " : known ? "" : "🔒 ") + plan.get("label").getAsString();
            g.text(font, Component.literal(title), left + 8, y, installed ? GREEN : known ? SuitScreen.TEXT : SuitScreen.GREY, false);
            g.text(font, font.plainSubstrByWidth(plan.get("effect").getAsString(), panelWidth - 100), left + 8, y + 11,
                    SuitScreen.GREY, false);
            if (installed) {
                g.text(font, Component.literal("Installé"), left + panelWidth - 70, y + 4, GREEN, false);
            } else if (!known) {
                g.text(font, font.plainSubstrByWidth("Pour débloquer : " + plan.get("unlock").getAsString(), panelWidth - 16),
                        left + 8, y + 22, SuitScreen.GOLD, false);
            } else {
                int x = left + 8;
                for (JsonElement element : plan.getAsJsonArray("materials")) {
                    JsonObject material = element.getAsJsonObject();
                    boolean enough = material.get("have").getAsInt() >= material.get("need").getAsInt();
                    String text = material.get("label").getAsString() + " (" + material.get("have").getAsInt() + ")";
                    g.text(font, Component.literal(text), x, y + 22, enough ? GREEN : SuitScreen.RED, false);
                    x += font.width(text) + 12;
                }
            }
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
}
