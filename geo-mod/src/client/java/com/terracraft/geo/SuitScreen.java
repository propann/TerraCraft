package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fenêtre de combinaison spatiale (touche J) : casque, combinaison, bottes magnétiques et deux
 * réserves d'oxygène, avec l'aperçu du joueur équipé, la jauge d'oxygène et l'autonomie.
 */
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

    private static final String[] LABELS = {"Casque", "Combinaison", "Bottes"};
    private static final int GAUGE_X = 112;
    private static final int GAUGE_Y = 95;
    private static final int GAUGE_WIDTH = 56;

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

        // Silhouette du joueur équipé, sur fond de hublot.
        g.fillGradient(x + 8, y + 20, x + 62, y + 98, 0xFF06090E, 0xFF14222D);
        g.outline(x + 8, y + 20, 54, 78, SLOT_EDGE);
        if (minecraft.player != null) {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 8, y + 20, x + 62, y + 98, 30, 0.0625f,
                    mouseX, mouseY, minecraft.player);
        }

        // Cadre « réserves d'oxygène ».
        g.outline(x + 65, y + 81, 42, 22, SLOT_EDGE);

        for (Slot slot : menu.slots) {
            boolean suitSlot = slot.index < SpaceSuit.SIZE;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, suitSlot ? 0x804FD6FF : SLOT_EDGE);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
            if (suitSlot && !slot.hasItem()) {
                // Silhouette grisée de l'objet attendu.
                g.item(ghost(slot.index), x + slot.x, y + slot.y);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xB00B1118);
            }
        }

        // Jauge d'oxygène du casque.
        int percent = helmetPercent();
        g.fill(x + GAUGE_X - 1, y + GAUGE_Y - 1, x + GAUGE_X + GAUGE_WIDTH + 1, y + GAUGE_Y + 6, SLOT_EDGE);
        g.fill(x + GAUGE_X, y + GAUGE_Y, x + GAUGE_X + GAUGE_WIDTH, y + GAUGE_Y + 5, SLOT_BG);
        if (percent > 0) {
            g.fill(x + GAUGE_X, y + GAUGE_Y, x + GAUGE_X + GAUGE_WIDTH * percent / 100, y + GAUGE_Y + 5, gaugeColor(percent));
        }
    }

    private static ItemStack ghost(int slot) {
        return new ItemStack(switch (slot) {
            case SpaceSuit.HELMET -> ModContent.SPACE_HELMET;
            case SpaceSuit.SUIT -> ModContent.SPACE_SUIT;
            case SpaceSuit.BOOTS -> ModContent.MAGNETIC_BOOTS;
            default -> ModContent.OXYGEN_TANK;
        });
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.literal("COMBINAISON SPATIALE"), 8, 5, GOLD, false);
        Player player = minecraft.player;
        for (int i = 0; i < LABELS.length; i++) {
            int[] pos = SpaceSuit.Menu.POSITIONS[i];
            boolean worn = player != null && !SpaceSuit.get(player, i).isEmpty();
            g.text(font, Component.literal(LABELS[i]), pos[0] + 20, pos[1] + 4, worn ? CYAN : GREY, false);
        }

        int percent = helmetPercent();
        g.text(font, Component.literal(percent < 0 ? "O₂ —" : "O₂ " + percent + " %"), GAUGE_X, GAUGE_Y - 11,
                percent < 0 ? GREY : gaugeColor(percent), false);
        int seconds = player == null ? 0 : SpaceSuit.autonomySeconds(player);
        Component autonomy = Component.literal("≈" + (seconds + 59) / 60 + " min");
        g.text(font, autonomy, GAUGE_X + GAUGE_WIDTH - font.width(autonomy), GAUGE_Y + 8, TEXT, false);

        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GREY, false);
    }

    /** Pourcentage d'oxygène du casque spatial porté, ou -1 sans casque. */
    private int helmetPercent() {
        return minecraft.player == null ? -1 : helmetPercent(SpaceSuit.helmet(minecraft.player));
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
