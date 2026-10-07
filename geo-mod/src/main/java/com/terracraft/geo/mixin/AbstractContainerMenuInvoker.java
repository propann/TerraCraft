package com.terracraft.geo.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accès aux méthodes protégées des menus, pour ajouter les emplacements de la combinaison. */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {
    @Invoker("addSlot")
    Slot terracraft$addSlot(Slot slot);

    @Invoker("moveItemStackTo")
    boolean terracraft$moveItemStackTo(ItemStack stack, int startSlot, int endSlot, boolean backwards);
}
