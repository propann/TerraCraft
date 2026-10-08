package com.terracraft.geo;

import com.terracraft.geo.content.Vehicle;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Tableau de bord des véhicules terrestres (voiture, camion, moto, rover) : vitesse, jauge de carburant ou de
 * batterie avec l'autonomie restante, état du véhicule, et avertissement s'il manque des pièces.
 */
final class VehicleHud implements HudElement {
    private static final int WIDTH = 120;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null || !(minecraft.player.getVehicle() instanceof Vehicle vehicle)) {
            return;
        }
        boolean rover = vehicle.kind() == Vehicle.Kind.ROVER;
        int left = 6;
        int top = g.guiHeight() / 2 - 30;
        g.fill(left - 2, top - 2, left + WIDTH, top + 52, 0xA0101820);
        g.outline(left - 2, top - 2, WIDTH + 2, 54, SuitScreen.BORDER);
        // Déplacement réel entre deux ticks : valable pour le conducteur comme pour le passager.
        int kmh = (int) Math.round(Math.hypot(vehicle.getX() - vehicle.xo, vehicle.getZ() - vehicle.zo) * 20 * 3.6);
        int percent = Math.round(100f * vehicle.fuel() / Vehicle.MAX_FUEL);
        int minutes = vehicle.fuel() / (20 * 60);
        g.text(minecraft.font, Component.literal(kmh + " km/h"), left + 2, top + 2, SuitScreen.TEXT, false);
        g.text(minecraft.font, Component.literal((rover ? "Batterie " : "Carburant ") + percent + " %"), left + 2, top + 13,
                SuitScreen.gaugeColor(percent), false);
        Ui.bar(g, left + 2, top + 24, WIDTH - 6, 4, percent / 100.0, SuitScreen.gaugeColor(percent));
        g.text(minecraft.font, Component.literal("Autonomie ~" + minutes + " min"), left + 2, top + 31, SuitScreen.GREY, false);
        int health = Math.max(0, Math.round(100 - vehicle.getDamage() / 6f));
        g.text(minecraft.font, Component.literal("État " + health + " %"), left + 2, top + 42,
                health > 60 ? 0xFF7EE08A : health > 30 ? SuitScreen.GOLD : SuitScreen.RED, false);
        if (!vehicle.isComplete()) {
            g.centeredText(minecraft.font, Component.literal("Véhicule incomplet : clic droit avec les pièces manquantes"),
                    g.guiWidth() / 2, g.guiHeight() - 84, SuitScreen.RED);
        } else if (vehicle.fuel() == 0) {
            g.centeredText(minecraft.font, Component.literal(rover ? "Batterie vide : laisse le rover au soleil, à l'arrêt"
                    : "Réservoir vide : clic droit avec un bidon d'essence (pompe à essence)"), g.guiWidth() / 2, g.guiHeight() - 84, SuitScreen.RED);
        }
    }
}
