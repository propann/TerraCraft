package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Fiche de personnage : niveau et progression vers le palier suivant, améliorations
 * débloquées, statistiques de survie et découvertes (cochées ou à faire).
 */
public final class CharacterSheetScreen extends Screen {
    private static final int GOLD = SuitScreen.GOLD;
    private static final int WHITE = SuitScreen.TEXT;
    private static final int GREY = SuitScreen.GREY;
    private static final int GREEN = 0xFF7EE08A;
    private final JsonObject sheet;

    public CharacterSheetScreen(String json) {
        super(Component.literal("Fiche de personnage"));
        this.sheet = JsonParser.parseString(json).getAsJsonObject();
    }

    /** Hauteur utile : la plus longue des trois colonnes, sous l'en-tête et les compétences. */
    private int contentHeight() {
        int perks = 12 + sheet.getAsJsonArray("perks").size() * 21;
        int stats = 12 + sheet.getAsJsonArray("stats").size() * 12;
        int discoveries = 12 + sheet.getAsJsonArray("discoveries").size() * 11;
        return 104 + Math.max(perks, Math.max(stats, discoveries)) + 16;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(g);
        int w = Math.min(this.width - 20, 520);
        int h = Math.min(this.height - 16, contentHeight());
        int left = (this.width - w) / 2;
        int top = Math.max(8, (this.height - h) / 2);
        int bottom = top + h;
        Ui.frame(g, left, top, w, h, 30);

        int level = sheet.get("level").getAsInt();
        int points = sheet.get("points").getAsInt();
        int previous = sheet.get("previous").getAsInt();
        int next = sheet.get("next").getAsInt();
        g.text(this.font, sheet.get("name").getAsString(), left + 10, top + 6, GOLD, true);
        String palier = "Palier " + level + "/10";
        g.text(this.font, palier, left + w - 10 - this.font.width(palier), top + 6, SuitScreen.CYAN, false);
        g.text(this.font, "❤ " + sheet.get("health").getAsInt() / 2 + " cœurs   ⛨ " + sheet.get("armor").getAsInt() + " armure   ✦ "
                + points + " pts", left + 10, top + 18, WHITE, false);

        // Barre de progression vers le palier suivant.
        int barLeft = left + 10;
        int barWidth = w - 20;
        int barTop = top + 40;
        double ratio = next < 0 ? 1 : Math.min(1, (points - previous) / (double) Math.max(1, next - previous));
        Ui.bar(g, barLeft, barTop, barWidth, 6, ratio, GOLD);
        g.text(this.font, next < 0 ? "Tous les paliers atteints" : "Prochain palier : " + next + " pts",
                barLeft, barTop + 10, GREY, false);

        // Compétences : quatre jauges (niveau et progression vers le niveau suivant).
        int skillTop = barTop + 26;
        if (sheet.has("skills")) {
            JsonArray skills = sheet.getAsJsonArray("skills");
            int cell = (w - 20) / Math.max(1, skills.size());
            for (int i = 0; i < skills.size(); i++) {
                JsonObject sk = skills.get(i).getAsJsonObject();
                int sx = left + 10 + i * cell;
                int skillLevel = sk.get("level").getAsInt();
                long xp = sk.get("xp").getAsLong();
                long from = sk.get("from").getAsLong();
                long to = sk.get("to").getAsLong();
                g.text(this.font, sk.get("name").getAsString() + " " + skillLevel, sx, skillTop, WHITE, true);
                double r = to < 0 ? 1 : Math.min(1, (xp - from) / (double) Math.max(1, to - from));
                Ui.bar(g, sx, skillTop + 12, cell - 10, 3, r, GREEN);
                if (mouseX >= sx && mouseX < sx + cell - 8 && mouseY >= skillTop && mouseY < skillTop + 16) {
                    g.setTooltipForNextFrame(this.font, Component.literal(sk.get("bonus").getAsString() + " par niveau ("
                            + (to < 0 ? "max" : (xp - from) + "/" + (to - from) + " XP") + ")"), mouseX, mouseY);
                }
            }
        }

        int column = (w - 30) / 3;
        int y0 = skillTop + 26;
        // Colonne 1 : améliorations.
        int x = left + 10;
        Ui.section(g, this.font, "Améliorations", x, y0, column - 4);
        int y = y0 + 13;
        for (JsonElement e : sheet.getAsJsonArray("perks")) {
            if (y > bottom - 30) {
                break;
            }
            JsonObject p = e.getAsJsonObject();
            boolean unlocked = p.get("unlocked").getAsBoolean();
            g.text(this.font, this.font.plainSubstrByWidth((unlocked ? "★ " : "☆ ") + p.get("name").getAsString(), column),
                    x, y, unlocked ? GOLD : GREY, false);
            g.text(this.font, this.font.plainSubstrByWidth("  " + p.get("description").getAsString()
                    + (unlocked ? "" : " (" + p.get("threshold").getAsInt() + ")"), column), x, y + 9, unlocked ? WHITE : GREY, false);
            y += 21;
        }
        // Colonne 2 : statistiques (lignes alternées).
        x = left + 15 + column;
        Ui.section(g, this.font, "Statistiques", x, y0, column - 4);
        y = y0 + 13;
        int row = 0;
        for (JsonElement e : sheet.getAsJsonArray("stats")) {
            if (y > bottom - 22) {
                break;
            }
            JsonObject s = e.getAsJsonObject();
            String value = Long.toString(s.get("value").getAsLong());
            if (row++ % 2 == 0) {
                g.fill(x - 2, y - 2, x + column - 2, y + 9, 0x18FFFFFF);
            }
            g.text(this.font, this.font.plainSubstrByWidth(s.get("label").getAsString(), column - 30), x, y, WHITE, false);
            g.text(this.font, value, x + column - this.font.width(value) - 4, y, GREEN, false);
            y += 12;
        }
        // Colonne 3 : découvertes.
        x = left + 20 + 2 * column;
        JsonArray discoveries = sheet.getAsJsonArray("discoveries");
        long done = discoveries.asList().stream().filter(d -> d.getAsJsonObject().get("done").getAsBoolean()).count();
        Ui.section(g, this.font, "Découvertes " + done + "/" + discoveries.size(), x, y0, column - 4);
        y = y0 + 13;
        for (JsonElement e : discoveries) {
            if (y > bottom - 22) {
                break;
            }
            JsonObject d = e.getAsJsonObject();
            boolean ok = d.get("done").getAsBoolean();
            String name = d.get("name").getAsString();
            String shortName = name.contains(" — ") ? name.substring(0, name.indexOf(" — ")) : name;
            g.text(this.font, this.font.plainSubstrByWidth((ok ? "✔ " : "○ ") + shortName, column), x, y, ok ? GREEN : GREY, false);
            if (mouseX >= x && mouseX < x + column && mouseY >= y && mouseY < y + 10) {
                g.setTooltipForNextFrame(this.font, Component.literal(name + " (+" + d.get("points").getAsInt() + " pts)"), mouseX, mouseY);
            }
            y += 11;
        }
        g.text(this.font, "K : ouvrir la fiche · Échap : fermer", left + w - 10 - this.font.width("K : ouvrir la fiche · Échap : fermer"),
                bottom - 12, GREY, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.mouseHandler.grabMouse();
        }
    }
}
