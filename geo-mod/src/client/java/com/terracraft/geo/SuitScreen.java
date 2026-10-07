package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fenêtre de combinaison spatiale (touche J) : casque, réserve d'oxygène et autonomie. */
public final class SuitScreen extends AbstractContainerScreen<SpaceSuit.Menu> {
    static final int PANEL = 0xF0121A24;
    static final int HEADER_TOP = 0xFF1E3442;
    static final int HEADER_BOTTOM = 0xFF142430;
    static final int BORDER = 0xFF3D5A6C;
    static final int SLOT_BG = 0xFF0B1118;
    static final int SLOT_EDGE = 0xFF2C4252;
    static final int CYAN = 0xFF4FD6FF;
    static final int GOLD = 0xFFFFC94A;
    static final int RED = 0xFFFF5555;
    static final int TEXT = 0xFFDDE6EC;
    static final int GREY = 0xFF8A9AA5;

    private static final int GAUGE_X = 80;
    private static final int GAUGE_Y = 94;
    private static final int GAUGE_WIDTH = 88;

    public SuitScreen(SpaceSuit.Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 196);
        inventoryLabelY = SpaceSuit.Menu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fillGradient(x, y, x + imageWidth, y + 16, HEADER_TOP, HEADER_BOTTOM);
        g.outline(x, y, imageWidth, imageHeight, BORDER);

        // Silhouette du joueur sur fond « hublot ».
        g.fillGradient(x + 8, y + 20, x + 62, y + 98, 0xFF06090E, 0xFF14222D);
        g.outline(x + 8, y + 20, 54, 78, SLOT_EDGE);
        if (minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 8, y + 20, x + 62, y + 98, 30, 0.0625f,
                    mouseX, mouseY, minecraft.player);
        }

        for (Slot slot : menu.slots) {
            boolean suitSlot = slot.index < 2;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, suitSlot ? CYAN & 0x80FFFFFF : SLOT_EDGE);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
        }

        // Jauge d'oxygène du casque.
        int percent = helmetPercent();
        int fill = GAUGE_WIDTH * Math.max(0, percent) / 100;
        g.fill(x + GAUGE_X - 1, y + GAUGE_Y - 1, x + GAUGE_X + GAUGE_WIDTH + 1, y + GAUGE_Y + 6, SLOT_EDGE);
        g.fill(x + GAUGE_X, y + GAUGE_Y, x + GAUGE_X + GAUGE_WIDTH, y + GAUGE_Y + 5, SLOT_BG);
        if (fill > 0) {
            g.fill(x + GAUGE_X, y + GAUGE_Y, x + GAUGE_X + fill, y + GAUGE_Y + 5, gaugeColor(percent));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("COMBINAISON SPATIALE"), 8, 5, GOLD, false);

        ItemStack helmet = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean suited = helmet.is(ModContent.SPACE_HELMET);
        g.text(font, Component.literal("Casque"), 100, 22, TEXT, false);
        g.text(font, Component.literal(suited ? "porté" : helmet.isEmpty() ? "vide" : "non spatial"), 100, 31,
                suited ? CYAN : GREY, false);

        int tanks = minecraft.player == null ? 0 : SpaceSuit.reserve(minecraft.player).getCount();
        g.text(font, Component.literal("Réserve O₂"), 100, 62, TEXT, false);
        g.text(font, Component.literal(tanks + " / 16"), 100, 71, tanks > 0 ? CYAN : GREY, false);

        int percent = helmetPercent();
        g.text(font, Component.literal(suited ? "O₂ " + percent + " %" : "Pas de casque"), GAUGE_X, GAUGE_Y - 10,
                suited ? gaugeColor(percent) : GREY, false);
        int seconds = minecraft.player == null ? 0 : SpaceSuit.autonomySeconds(minecraft.player);
        Component autonomy = Component.literal("≈ " + (seconds + 59) / 60 + " min");
        g.text(font, autonomy, GAUGE_X + GAUGE_WIDTH - font.width(autonomy), GAUGE_Y - 10, TEXT, false);

        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GREY, false);
    }

    /** Pourcentage d'oxygène du casque porté, ou -1 sans casque spatial. */
    private int helmetPercent() {
        if (minecraft.player == null) {
            return -1;
        }
        return helmetPercent(minecraft.player.getItemBySlot(EquipmentSlot.HEAD));
    }

    static int helmetPercent(ItemStack helmet) {
        if (!helmet.is(ModContent.SPACE_HELMET)) {
            return -1;
        }
        return 100 - 100 * helmet.getDamageValue() / helmet.getMaxDamage();
    }

    static int gaugeColor(int percent) {
        return percent > 50 ? CYAN : percent > 20 ? GOLD : RED;
    }
}
