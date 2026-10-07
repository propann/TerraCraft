package com.terracraft.geo;

import com.terracraft.geo.content.Plane;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Instruments de bord de l'avion : gaz, vitesse, altitude, carburant et alarme de décrochage. */
final class PlaneHud implements HudElement {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof Plane plane)) {
            return;
        }
        int left = 6;
        int top = g.guiHeight() / 2 - 30;
        g.fill(left - 2, top - 2, left + 112, top + 50, 0xA0101820);
        g.outline(left - 2, top - 2, 114, 52, SuitScreen.BORDER);
        boolean pilot = plane.getControllingPassenger() == minecraft.player;
        int throttle = Math.round(plane.throttle() * 100);
        // Déplacement réel entre deux ticks : valable pour le pilote comme pour le passager.
        int kmh = (int) Math.round(Math.hypot(plane.getX() - plane.xo, plane.getZ() - plane.zo) * 20 * 3.6);
        int fuel = Math.round(100f * plane.fuel() / Plane.MAX_FUEL);
        g.text(minecraft.font, Component.literal(pilot ? "Gaz " + throttle + " %" : "Passager"), left + 2, top + 2, SuitScreen.TEXT, false);
        g.text(minecraft.font, Component.literal("Vitesse " + kmh + " km/h"), left + 2, top + 13, SuitScreen.TEXT, false);
        g.text(minecraft.font, Component.literal("Altitude " + Math.round(plane.getY()) + " m"), left + 2, top + 24, SuitScreen.TEXT, false);
        g.text(minecraft.font, Component.literal("Carburant " + fuel + " %"), left + 2, top + 35,
                SuitScreen.gaugeColor(fuel), false);
        if (pilot && !plane.onGround() && plane.speed() < Plane.LIFT_SPEED) {
            g.centeredText(minecraft.font, Component.literal("⚠ DÉCROCHAGE — remets les gaz (Z)"), g.guiWidth() / 2,
                    g.guiHeight() / 2 + 20, SuitScreen.RED);
        }
    }
}
