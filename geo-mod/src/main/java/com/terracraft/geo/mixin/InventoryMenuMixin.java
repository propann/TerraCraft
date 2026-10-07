package com.terracraft.geo.mixin;

import com.terracraft.geo.SpaceSuit;
import com.terracraft.geo.SpaceSuitSlot;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ajoute les cinq emplacements de la combinaison spatiale à l'inventaire du joueur (touche E),
 * dans un panneau à droite. Ils sont ajoutés après les 46 emplacements vanilla : les numéros
 * existants ne changent pas. Shift-clic range une pièce spatiale directement dans la combinaison.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    /** Position du panneau, relative à l'inventaire vanilla (176 × 166). */
    @Unique
    private static final int[][] TERRACRAFT_POSITIONS = {{183, 10}, {183, 28}, {183, 46}, {183, 70}, {183, 88}};
    /** Fin des emplacements « inventaire + barre rapide » de l'inventaire vanilla (exclue). */
    @Unique
    private static final int TERRACRAFT_INVENTORY_END = 45;

    @Unique
    private int terracraft$firstSuitSlot = -1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terracraft$addSuitSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        Container equipment = SpaceSuit.container(owner);
        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker) this;
        for (int i = 0; i < SpaceSuit.SIZE; i++) {
            Slot slot = menu.terracraft$addSlot(new SpaceSuitSlot(equipment, i, TERRACRAFT_POSITIONS[i][0], TERRACRAFT_POSITIONS[i][1]));
            if (i == 0) {
                terracraft$firstSuitSlot = slot.index;
            }
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void terracraft$quickMoveSuit(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu self = (InventoryMenu) (Object) this;
        if (terracraft$firstSuitSlot < 0 || index < 0 || index >= self.slots.size()) {
            return;
        }
        Slot slot = self.slots.get(index);
        if (!slot.hasItem()) {
            return;
        }
        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker) this;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (slot instanceof SpaceSuitSlot) {
            moved = menu.terracraft$moveItemStackTo(stack, 9, TERRACRAFT_INVENTORY_END, false);
        } else if (index >= 9 && index < TERRACRAFT_INVENTORY_END && SpaceSuit.slotFor(stack) >= 0) {
            int target = SpaceSuit.slotFor(stack);
            int first = terracraft$firstSuitSlot;
            if (target >= SpaceSuit.TANK_A) {
                moved = menu.terracraft$moveItemStackTo(stack, first + SpaceSuit.TANK_A, first + SpaceSuit.TANK_B + 1, false);
            } else {
                moved = !self.slots.get(first + target).hasItem()
                        && menu.terracraft$moveItemStackTo(stack, first + target, first + target + 1, false);
            }
            if (!moved) {
                return; // Combinaison déjà équipée : comportement vanilla (inventaire ↔ barre rapide).
            }
        } else {
            return;
        }
        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        cir.setReturnValue(original);
    }
}
