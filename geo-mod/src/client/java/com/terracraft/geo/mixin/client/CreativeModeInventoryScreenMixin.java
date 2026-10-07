package com.terracraft.geo.mixin.client;

import com.terracraft.geo.SpaceSuitSlot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * L'onglet inventaire du mode créatif replace tous les emplacements du joueur ; ceux de la
 * combinaison tomberaient sur la barre rapide. On les écarte (la touche J reste disponible).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @ModifyArgs(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private void terracraft$hideSuitSlots(Args args) {
        if (args.get(0) instanceof SpaceSuitSlot) {
            args.set(2, -2000);
            args.set(3, -2000);
        }
    }
}
