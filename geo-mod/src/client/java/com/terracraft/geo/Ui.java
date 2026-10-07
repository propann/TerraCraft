package com.terracraft.geo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Style commun des fenêtres TerraCraft (palette de la combinaison) : cadre avec ombre portée, bandeau dégradé et
 * liseré doré, titres de section soulignés, jauges encadrées. Toutes les fenêtres passent par ici pour rester
 * cohérentes.
 */
public final class Ui {
    private Ui() {
    }

    /** Cadre d'une fenêtre : ombre, panneau, bandeau de {@code header} pixels, liseré doré, bordure. */
    public static void frame(GuiGraphicsExtractor g, int left, int top, int width, int height, int header) {
        g.fill(left + 3, top + 3, left + width + 3, top + height + 3, 0x60000000);
        g.fill(left, top, left + width, top + height, SuitScreen.PANEL);
        g.fillGradient(left, top, left + width, top + header, SuitScreen.HEADER_TOP, SuitScreen.HEADER_BOTTOM);
        g.fill(left, top + header, left + width, top + header + 1, SuitScreen.GOLD);
        g.outline(left, top, width, height, SuitScreen.BORDER);
    }

    /** Titre de section : texte doré et filet discret jusqu'à la fin de la colonne. */
    public static void section(GuiGraphicsExtractor g, Font font, String label, int x, int y, int width) {
        g.text(font, label, x, y, SuitScreen.GOLD, false);
        int end = x + font.width(label) + 4;
        if (end < x + width) {
            g.fill(end, y + 4, x + width, y + 5, SuitScreen.SLOT_EDGE);
        }
    }

    /** Jauge encadrée remplie à {@code ratio} (0 à 1), avec un reflet clair sur le haut du remplissage. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int width, int height, double ratio, int colour) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, SuitScreen.SLOT_EDGE);
        g.fill(x, y, x + width, y + height, SuitScreen.SLOT_BG);
        int filled = (int) Math.round(width * Math.max(0, Math.min(1, ratio)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + height, colour);
            g.fill(x, y, x + filled, y + 1, 0x50FFFFFF);
        }
    }
}
