package com.terracraft.geo.mixin.client;

import com.terracraft.geo.SpaceSuitSlot;
import com.terracraft.geo.SuitScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Panneau « combinaison spatiale » à droite de l'inventaire (E) : fond et silhouettes des emplacements. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$drawSuitPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        int x = accessor.terracraft$leftPos();
        int y = accessor.terracraft$topPos();
        g.fill(x + 178, y + 4, x + 204, y + 126, SuitScreen.PANEL);
        g.outline(x + 178, y + 4, 26, 122, SuitScreen.BORDER);
        g.fill(x + 181, y + 84, x + 201, y + 85, SuitScreen.SLOT_EDGE);
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof SpaceSuitSlot suit) {
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0x804FD6FF);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SuitScreen.SLOT_BG);
                if (!slot.hasItem()) {
                    g.item(SuitScreen.ghost(suit.suitSlot()), x + slot.x, y + slot.y);
                    g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xB00B1118);
                }
            }
        }
    }
}
