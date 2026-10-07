package com.terracraft.geo;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Emplacement de la combinaison spatiale (fenêtre J et panneau de l'inventaire E). */
public final class SpaceSuitSlot extends Slot {
    private final int suitSlot;

    public SpaceSuitSlot(Container equipment, int suitSlot, int x, int y) {
        super(equipment, suitSlot, x, y);
        this.suitSlot = suitSlot;
    }

    public int suitSlot() {
        return suitSlot;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return SpaceSuit.accepts(suitSlot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return suitSlot >= SpaceSuit.TANK_A ? 16 : 1;
    }
}
