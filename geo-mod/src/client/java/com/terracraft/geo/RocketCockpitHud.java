package com.terracraft.geo;

import com.terracraft.geo.content.Rocket;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Cockpit de la fusée, en vue à la première personne : on regarde dehors par un hublot rond cerclé et boulonné, le
 * tableau de bord est en bas (altitude, vitesse verticale, carburant, cap, phase de vol) et les accoudoirs du siège
 * encadrent l'écran. F5 donne la vue extérieure ; elle s'active d'elle-même au décollage et rend la main au retour
 * au sol, sauf si le joueur a changé de vue entre-temps.
 */
final class RocketCockpitHud implements HudElement {
    private static final int HULL = 0xFF1B2128;
    private static final int HULL_LIGHT = 0xFF2A323B;
    private static final int RIM = 0xFF6E7682;
    private static final int RIM_DARK = 0xFF3E454F;
    private static final int BOLT = 0xFFB8BEC8;
    private static final int SEAT = 0xFF5A2A22;
    private static final int SEAT_EDGE = 0xFF7A3A2E;

    /** Vue extérieure activée par le décollage (et non par le joueur) : à rendre à l'atterrissage. */
    private static boolean autoExterior;
    private static byte lastPhase = -1;

    /** Bascule automatique de caméra, appelée à chaque tick client. */
    static void tick(Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof Rocket rocket)) {
            if (autoExterior && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON); // Sorti de la fusée en vol : on rend la vue.
            }
            autoExterior = false;
            lastPhase = -1;
            return;
        }
        byte phase = rocket.phase();
        if (phase == Rocket.ASCENT && lastPhase != Rocket.ASCENT && minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            autoExterior = true;
        } else if (phase == Rocket.IDLE && lastPhase != Rocket.IDLE && lastPhase != -1 && autoExterior) {
            if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            }
            autoExterior = false;
        } else if (autoExterior && minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            autoExterior = false; // Le joueur a choisi sa vue : on n'y touche plus.
        }
        lastPhase = phase;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof Rocket rocket)) {
            return;
        }
        int width = g.guiWidth();
        int height = g.guiHeight();
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            g.centeredText(minecraft.font, "F5 : retour au cockpit", width / 2, height - 60, SuitScreen.GREY);
            return;
        }
        // Tableau de bord au-dessus de la barre d'objets, socle sombre dessous : la barre vanilla s'y pose.
        int panelTop = height - 96;
        int panelBottom = height - 42;
        int cx = width / 2;
        int cy = panelTop / 2 + 4;
        int radius = Math.min(cx - 40, cy - 8);
        porthole(g, width, panelTop, cx, cy, radius);
        g.fill(0, panelBottom, width, height, HULL);
        seat(g, width, height, panelTop);
        panel(g, minecraft, rocket, width, panelBottom, panelTop);
    }

    /** Paroi de la cabine percée d'un hublot rond : dessinée ligne par ligne autour du cercle. */
    private static void porthole(GuiGraphicsExtractor g, int width, int panelTop, int cx, int cy, int radius) {
        int rim = Math.max(4, radius / 12);
        for (int y = 0; y < panelTop; y++) {
            int dy = y - cy;
            int color = (y / 6) % 2 == 0 ? HULL : HULL_LIGHT; // Plaques de coque.
            int outer = halfChord(radius + rim, dy);
            if (outer < 0) {
                g.fill(0, y, width, y + 1, color);
                continue;
            }
            g.fill(0, y, cx - outer, y + 1, color);
            g.fill(cx + outer, y, width, y + 1, color);
            int inner = halfChord(radius, dy);
            int ring = dy < 0 ? RIM : RIM_DARK; // Lumière venue d'en haut.
            if (inner < 0) {
                g.fill(cx - outer, y, cx + outer, y + 1, ring);
            } else {
                g.fill(cx - outer, y, cx - inner, y + 1, ring);
                g.fill(cx + inner, y, cx + outer, y + 1, ring);
            }
        }
        // Boulons du cerclage.
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            int bx = cx + (int) Math.round(Math.cos(angle) * (radius + rim / 2.0));
            int by = cy + (int) Math.round(Math.sin(angle) * (radius + rim / 2.0));
            g.fill(bx - 1, by - 1, bx + 1, by + 1, BOLT);
        }
        // Reflet sur la vitre.
        int rx = cx - radius * 6 / 10;
        int ry = cy - radius * 6 / 10;
        g.fill(rx, ry, rx + radius / 5, ry + 1, 0x30FFFFFF);
        g.fill(rx, ry + 2, rx + radius / 8, ry + 3, 0x20FFFFFF);
    }

    /** Demi-largeur du cercle de rayon r à la hauteur dy, ou -1 hors du cercle. */
    private static int halfChord(int r, int dy) {
        int sq = r * r - dy * dy;
        return sq < 0 ? -1 : (int) Math.sqrt(sq);
    }

    /** Accoudoirs du siège de pilote, de part et d'autre du tableau de bord. */
    private static void seat(GuiGraphicsExtractor g, int width, int height, int panelTop) {
        g.fill(0, panelTop - 20, 26, height, SEAT);
        g.fill(width - 26, panelTop - 20, width, height, SEAT);
        g.fill(0, panelTop - 20, 26, panelTop - 18, SEAT_EDGE);
        g.fill(width - 26, panelTop - 20, width, panelTop - 18, SEAT_EDGE);
        // Sangles du harnais.
        g.fill(10, panelTop - 18, 14, height, 0xFF20242A);
        g.fill(width - 14, panelTop - 18, width - 10, height, 0xFF20242A);
    }

    private static void panel(GuiGraphicsExtractor g, Minecraft minecraft, Rocket rocket, int width, int height, int top) {
        int left = 30;
        int right = width - 30;
        g.fill(left, top, right, height, 0xFF141A21);
        g.fill(left, top, right, top + 2, RIM);
        g.outline(left, top, right - left, height - top, SuitScreen.BORDER);

        byte phase = rocket.phase();
        int vertical = (int) Math.round((rocket.getY() - rocket.yo) * 20);
        int fuel = Math.round(100f * rocket.fuel() / Math.max(1, rocket.maxFuel()));
        byte target = rocket.target();
        String heading = target >= 0 ? Space.name(target) : Space.name(Space.id(minecraft.level));

        // Colonne gauche : altitude et vitesse.
        int x = left + 8;
        int y = top + 7;
        g.text(minecraft.font, "ALT " + Math.round(rocket.getY()) + " m", x, y, SuitScreen.TEXT, false);
        g.text(minecraft.font, "V/S " + (vertical > 0 ? "+" : "") + vertical + " m/s", x, y + 12,
                vertical < -15 ? SuitScreen.GOLD : SuitScreen.TEXT, false);
        g.text(minecraft.font, "CAP " + heading, x, y + 24, SuitScreen.CYAN, false);

        // Colonne droite : jauge de carburant.
        int gaugeWidth = Math.min(90, (right - left) / 3);
        int gx = right - gaugeWidth - 8;
        g.text(minecraft.font, "CARBURANT " + rocket.fuel() + "/" + rocket.maxFuel(), gx, y, SuitScreen.TEXT, false);
        g.fill(gx, y + 11, gx + gaugeWidth, y + 17, 0xFF0B1118);
        g.fill(gx, y + 11, gx + gaugeWidth * Math.min(100, fuel) / 100, y + 17, SuitScreen.gaugeColor(fuel));
        if (phase == Rocket.IDLE && target >= 0) {
            boolean enough = rocket.fuel() >= rocket.cost();
            g.text(minecraft.font, "Trajet : " + rocket.cost() + " dose(s)", gx, y + 24, enough ? SuitScreen.GREY : SuitScreen.RED, false);
        }

        // Centre : voyant de phase.
        String state;
        int color;
        boolean blink = (minecraft.player.tickCount / 10) % 2 == 0;
        switch (phase) {
            case Rocket.COUNTDOWN -> {
                state = "COMPTE À REBOURS";
                color = blink ? SuitScreen.GOLD : 0xFF8A6A20;
            }
            case Rocket.ASCENT -> {
                state = "ASCENSION";
                color = SuitScreen.RED;
            }
            case Rocket.DESCENT -> {
                state = "DESCENTE";
                color = SuitScreen.CYAN;
            }
            default -> {
                state = rocket.onGround() ? "AU SOL" : "EN ATTENTE";
                color = 0xFF55DD77;
            }
        }
        int mid = (left + right) / 2;
        g.fill(mid - 3, y + 1, mid + 3, y + 7, color);
        g.centeredText(minecraft.font, state, mid, y + 10, color);
        g.centeredText(minecraft.font, "F5 : vue extérieure", mid, y + 24, SuitScreen.GREY);
    }
}
