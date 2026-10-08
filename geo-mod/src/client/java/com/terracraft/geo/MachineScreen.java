package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Écran d'une machine de l'industrie du carburant : une jauge par liquide, énergie (panneaux, batteries), charge,
 * gisement et conseil. Rafraîchi chaque seconde tant qu'il est ouvert (le serveur renvoie l'état).
 */
public final class MachineScreen extends Screen {
    private static final int WIDTH = 260;

    private JsonObject data;
    private final BlockPos pos;
    private int ticks;
    private int left;
    private int top;
    private int panelHeight;

    public MachineScreen(String json) {
        super(Component.literal("Machine"));
        data = JsonParser.parseString(json).getAsJsonObject();
        JsonArray p = data.getAsJsonArray("pos");
        pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
    }

    public BlockPos pos() {
        return pos;
    }

    /** Nouvel état reçu pendant que l'écran est ouvert. */
    public void update(String json) {
        data = JsonParser.parseString(json).getAsJsonObject();
    }

    private int contentHeight() {
        int lines = data.getAsJsonArray("gauges").size() * 22 + 14;
        if (data.has("panels")) {
            lines += 12;
        }
        if (data.has("charge")) {
            lines += 22;
        }
        if (data.has("richness")) {
            lines += 12;
        }
        if (data.has("growth")) {
            lines += 22;
        }
        return 30 + lines + 30 + 26;
    }

    @Override
    protected void init() {
        panelHeight = contentHeight();
        left = (width - WIDTH) / 2;
        top = Math.max(4, (height - panelHeight) / 2);
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose())
                .bounds(left + WIDTH / 2 - 40, top + panelHeight - 24, 80, 18).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 20 == 0 && ClientPlayNetworking.canSend(RequestMachinePayload.TYPE)) {
            ClientPlayNetworking.send(new RequestMachinePayload(pos));
        }
    }

    private static int colour(String fluid) {
        return switch (fluid) {
            case "CRUDE" -> 0xFF6B4A2B;
            case "GASOLINE" -> 0xFFE8B23A;
            default -> 0xFF4FA3E0;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        Ui.frame(g, left, top, WIDTH, panelHeight, 18);
        g.text(font, Component.literal(data.get("label").getAsString().toUpperCase()), left + 8, top + 5, SuitScreen.GOLD, false);
        int y = top + 28;
        int barWidth = WIDTH - 16;
        for (JsonElement element : data.getAsJsonArray("gauges")) {
            JsonObject gauge = element.getAsJsonObject();
            int amount = gauge.get("amount").getAsInt();
            int capacity = Math.max(1, gauge.get("capacity").getAsInt());
            g.text(font, gauge.get("label").getAsString(), left + 8, y, SuitScreen.TEXT, false);
            String value = amount + " / " + capacity + " mB";
            g.text(font, value, left + WIDTH - 8 - font.width(value), y, SuitScreen.GREY, false);
            Ui.bar(g, left + 8, y + 11, barWidth, 6, (double) amount / capacity, colour(gauge.get("fluid").getAsString()));
            y += 22;
        }
        if (data.has("panels")) {
            int panels = data.get("panels").getAsInt();
            int power = data.get("power").getAsInt();
            g.text(font, "⚡ Énergie : " + power + " unité(s) · " + panels + " panneau(x) au soleil"
                    + (panels == 0 ? " (nuit ou câbles ?)" : ""), left + 8, y, power > 0 ? 0xFF7EE08A : SuitScreen.RED, false);
            y += 12;
        }
        if (data.has("charge")) {
            int charge = data.get("charge").getAsInt();
            int max = Math.max(1, data.get("chargeMax").getAsInt());
            g.text(font, "Charge : " + charge * 100 / max + " % (" + charge + " / " + max + ")", left + 8, y, SuitScreen.TEXT, false);
            Ui.bar(g, left + 8, y + 11, barWidth, 6, (double) charge / max, 0xFF7EE08A);
            y += 22;
        }
        if (data.has("growth")) {
            int growth = data.get("growth").getAsInt();
            int max = Math.max(1, data.get("growthMax").getAsInt());
            g.text(font, "Prochaine récolte : " + growth * 100 / max + " %", left + 8, y, SuitScreen.TEXT, false);
            Ui.bar(g, left + 8, y + 11, barWidth, 6, (double) growth / max, 0xFF6FCF5A);
            y += 22;
        }
        if (data.has("richness")) {
            int richness = data.get("richness").getAsInt();
            g.text(font, richness == 0 ? "Aucun gisement sous la pompe (détecteur de pétrole)" : "Gisement : " + "●".repeat(richness)
                    + "○".repeat(3 - richness), left + 8, y, richness == 0 ? SuitScreen.RED : SuitScreen.GOLD, false);
            y += 12;
        }
        y += 4;
        for (var line : font.split(Component.literal(data.get("hint").getAsString()), barWidth)) {
            g.text(font, line, left + 8, y, SuitScreen.GREY, false);
            y += 10;
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
