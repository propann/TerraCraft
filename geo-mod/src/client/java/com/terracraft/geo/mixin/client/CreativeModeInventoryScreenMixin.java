package com.terracraft.geo.mixin.client;

import com.terracraft.geo.SpaceSuitSlot;
import com.terracraft.geo.SuitScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Inventaire du mode créatif (onglet « Inventaire ») : même panneau de combinaison spatiale qu'en
 * survie, à droite de la fenêtre. Le serveur accepte ces emplacements (ServerCreativeSlotMixin).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    /** Positions dans l'onglet inventaire (195 × 136), par emplacement de la combinaison. */
    @Unique
    private static final int[][] TERRACRAFT_POSITIONS = {{202, 6}, {202, 24}, {202, 42}, {202, 84}, {202, 102}, {202, 60}};

    @Shadow
    private boolean hasClickedOutside;

    @Shadow
    public abstract boolean isInventoryOpen();

    @ModifyArgs(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private void terracraft$placeSuitSlots(Args args) {
        if (args.get(0) instanceof SpaceSuitSlot suit) {
            args.set(2, TERRACRAFT_POSITIONS[suit.suitSlot()][0]);
            args.set(3, TERRACRAFT_POSITIONS[suit.suitSlot()][1]);
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void terracraft$drawSuitPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isInventoryOpen()) {
            return;
        }
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        int x = accessor.terracraft$leftPos();
        int y = accessor.terracraft$topPos();
        g.fill(x + 197, y + 2, x + 223, y + 122, SuitScreen.PANEL);
        g.outline(x + 197, y + 2, 26, 120, SuitScreen.BORDER);
        g.fill(x + 200, y + 79, x + 220, y + 80, SuitScreen.SLOT_EDGE);
        // Les emplacements de la combinaison sont les seuls placés à x = 202 dans cet onglet.
        for (Slot slot : ((CreativeModeInventoryScreen) (Object) this).getMenu().slots) {
            for (int i = 0; i < TERRACRAFT_POSITIONS.length; i++) {
                if (slot.x == TERRACRAFT_POSITIONS[i][0] && slot.y == TERRACRAFT_POSITIONS[i][1]) {
                    g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0x804FD6FF);
                    g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SuitScreen.SLOT_BG);
                    if (!slot.hasItem()) {
                        g.item(SuitScreen.ghost(i), x + slot.x, y + slot.y);
                        g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xB00B1118);
                    }
                }
            }
        }
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void terracraft$suitPanelIsInside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
        if (isInventoryOpen() && mx >= xo + 195 && mx < xo + 225 && my >= yo && my < yo + 124) {
            hasClickedOutside = false;
            cir.setReturnValue(false);
        }
    }
}
