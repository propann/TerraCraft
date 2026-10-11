package com.terracraft.geo;

import com.terracraft.geo.content.Sights;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Visée côté client : touche « Viser » maintenue (bouton du milieu par défaut) avec une arme équipée d'un viseur,
 * ou arc et arbalète bandés avec un viseur. Le zoom passe par le modificateur de champ de vision du joueur (comme la
 * longue-vue vanilla : le réglage FOV du joueur n'est jamais modifié) ; la souris ralentit d'autant ; le réticule est
 * dessiné par-dessus l'écran. Le serveur est prévenu pour réduire dispersion et recul.
 */
public final class SightClient implements HudElement {
    private static Sights.@Nullable Sight current;
    private static boolean sentAiming;

    /** Viseur en cours d'utilisation, ou null. */
    public static Sights.@Nullable Sight current() {
        return current;
    }

    /** Grossissement à appliquer (1 sans visée). */
    public static float zoom() {
        return current == null ? 1f : current.zoom;
    }

    static void tick(Minecraft client, KeyMapping aimKey, KeyMapping reloadKey) {
        while (reloadKey.consumeClick()) {
            if (client.player != null && ClientPlayNetworking.canSend(ReloadPayload.TYPE)) {
                ClientPlayNetworking.send(ReloadPayload.INSTANCE);
            }
        }
        Sights.Sight sight = null;
        boolean keyAim = false;
        if (client.player != null && client.gui.screen() == null && client.options.getCameraType().isFirstPerson()) {
            ItemStack weapon = client.player.getMainHandItem();
            Sights.Sight mounted = Sights.of(weapon);
            boolean drawing = client.player.isUsingItem() && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW));
            keyAim = mounted != null && aimKey.isDown();
            if (keyAim || mounted != null && drawing) {
                sight = mounted;
            }
        }
        current = sight;
        Sights.setClientAiming(keyAim);
        if (keyAim != sentAiming && ClientPlayNetworking.canSend(AimPayload.TYPE)) {
            ClientPlayNetworking.send(new AimPayload(keyAim));
            sentAiming = keyAim;
        }
    }

    /** Lunette : disque de vision cerclé de noir, croisée fine ; point rouge : un point au centre. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Sights.Sight sight = current;
        if (sight == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        int cx = w / 2;
        int cy = h / 2;
        if (!sight.scope) {
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFF2020);
            g.fill(cx, cy - 2, cx + 1, cy + 3, 0x80FF2020);
            g.fill(cx - 2, cy, cx + 3, cy + 1, 0x80FF2020);
            return;
        }
        int r = (int) (Math.min(w, h) * 0.46);
        for (int y = 0; y < h; y++) {
            int dy = y - cy;
            int half = Math.abs(dy) >= r ? -1 : (int) Math.sqrt((double) r * r - (double) dy * dy);
            if (half < 0) {
                g.fill(0, y, w, y + 1, 0xFF000000);
            } else {
                g.fill(0, y, cx - half, y + 1, 0xFF000000);
                g.fill(cx + half, y, w, y + 1, 0xFF000000);
                g.fill(cx - half, y, cx - half + 2, y + 1, 0xFF1A1A1A); // Bord du tube.
                g.fill(cx + half - 2, y, cx + half, y + 1, 0xFF1A1A1A);
            }
        }
        int gap = 6;
        g.fill(cx - r, cy, cx - gap, cy + 1, 0xE0000000);
        g.fill(cx + gap, cy, cx + r, cy + 1, 0xE0000000);
        g.fill(cx, cy - r, cx + 1, cy - gap, 0xE0000000);
        g.fill(cx, cy + gap, cx + 1, cy + r, 0xE0000000);
        g.fill(cx - r / 3, cy - 1, cx - gap, cy + 2, 0xE0000000); // Traits épais vers le centre.
        g.fill(cx + gap, cy - 1, cx + r / 3, cy + 2, 0xE0000000);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFF3030);
        g.text(client.font, "x" + (int) sight.zoom, cx + r - 24, cy + r - 16, 0xFFDDE6EC, true);
    }
}
