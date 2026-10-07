package com.terracraft.geo;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Jauge de carburant du jetpack, à droite de la barre rapide, quand le joueur est en l'air. */
final class JetpackHud implements HudElement {
    private static final int WIDTH = 56;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || player.isSpectator() || player.onGround()) {
            return;
        }
        ItemStack jetpack = SpaceSuit.jetpack(player);
        if (jetpack.isEmpty()) {
            return;
        }
        int percent = 100 - 100 * jetpack.getDamageValue() / jetpack.getMaxDamage();
        int left = g.guiWidth() / 2 + 96;
        int top = g.guiHeight() - 20;
        int color = SuitScreen.gaugeColor(percent);
        g.text(minecraft.font, Component.literal("Jetpack " + percent + " %"), left, top - 10, color, true);
        g.fill(left, top, left + WIDTH, top + 4, SuitScreen.SLOT_BG);
        g.fill(left, top, left + WIDTH * percent / 100, top + 4, color);
    }
}
