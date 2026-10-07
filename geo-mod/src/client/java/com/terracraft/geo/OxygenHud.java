package com.terracraft.geo;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Bandeau d'oxygène en haut de l'écran, affiché uniquement hors de la Terre (Lune, orbite, Mars). */
final class OxygenHud implements HudElement {
    private static final int WIDTH = 150;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.isSpectator() || player.isCreative()
                || !player.level().dimension().identifier().getNamespace().equals(GeoMod.MOD_ID)) {
            return;
        }
        int percent = SuitScreen.helmetPercent(player.getItemBySlot(EquipmentSlot.HEAD));
        int tanks = SpaceSuit.reserve(player).getCount();
        int left = (g.guiWidth() - WIDTH) / 2;
        int top = 4;
        g.fill(left, top, left + WIDTH, top + 22, 0xB0101820);
        g.outline(left, top, WIDTH, 22, SuitScreen.BORDER);

        if (percent < 0) {
            g.centeredText(minecraft.font, Component.literal("⚠ Pas de casque spatial — touche J"), g.guiWidth() / 2, top + 7,
                    SuitScreen.RED);
            return;
        }
        int color = SuitScreen.gaugeColor(percent);
        g.text(minecraft.font, Component.literal("O₂ " + percent + " %"), left + 5, top + 3, color, false);
        Component info = Component.literal(tanks + " bout. · ≈ " + (SpaceSuit.autonomySeconds(player) + 59) / 60 + " min");
        g.text(minecraft.font, info, left + WIDTH - 5 - minecraft.font.width(info), top + 3, SuitScreen.TEXT, false);
        int barWidth = WIDTH - 10;
        g.fill(left + 5, top + 14, left + 5 + barWidth, top + 18, SuitScreen.SLOT_BG);
        g.fill(left + 5, top + 14, left + 5 + barWidth * percent / 100, top + 18, color);
    }
}
