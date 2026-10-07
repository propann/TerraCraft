package com.terracraft.geo;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Encart « Premiers pas » à droite de l'écran : objectif en cours et comment le réussir. */
final class TutorialHud implements HudElement {
    private static final int WIDTH = 150;
    private static TutorialPayload current;

    static void update(TutorialPayload payload) {
        current = payload.step() >= payload.total() ? null : payload;
    }

    static void clear() {
        current = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        TutorialPayload tutorial = current;
        Minecraft minecraft = Minecraft.getInstance();
        if (tutorial == null || minecraft.player == null || minecraft.gui.screen() != null) {
            return;
        }
        List<FormattedCharSequence> hint = minecraft.font.split(Component.literal(tutorial.hint()), WIDTH - 10);
        int height = 28 + hint.size() * 9;
        int left = g.guiWidth() - WIDTH - 4;
        int top = g.guiHeight() / 2 - height / 2 - 20;
        g.fill(left, top, left + WIDTH, top + height, 0xA0101820);
        g.fill(left, top, left + 2, top + height, SuitScreen.GOLD);
        g.text(minecraft.font, Component.literal("PREMIERS PAS " + (tutorial.step() + 1) + "/" + tutorial.total()),
                left + 6, top + 4, SuitScreen.GOLD, false);
        g.text(minecraft.font, Component.literal(tutorial.title()), left + 6, top + 15, SuitScreen.TEXT, false);
        int y = top + 26;
        for (FormattedCharSequence line : hint) {
            g.text(minecraft.font, line, left + 6, y, SuitScreen.GREY, false);
            y += 9;
        }
    }
}
