package com.terracraft.geo.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Le panneau de la combinaison dépasse à droite de l'inventaire : sans ce correctif, un clic
 * dessus compterait comme un clic « hors de la fenêtre » et jetterait l'objet tenu.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void terracraft$suitPanelIsInside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof InventoryScreen && mx >= xo + 176 && mx < xo + 206 && my >= yo + 2 && my < yo + 128) {
            cir.setReturnValue(false);
        }
    }
}
