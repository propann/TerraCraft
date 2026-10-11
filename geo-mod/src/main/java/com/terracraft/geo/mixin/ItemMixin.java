package com.terracraft.geo.mixin;

import com.terracraft.geo.content.Sights;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Arc et arbalète vanilla : clic droit main vide dans l'inventaire rend leur viseur (comme pour nos armes à feu). */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void terracraft$detachSight(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player,
                                        SlotAccess carried, CallbackInfoReturnable<Boolean> cir) {
        if ((self.is(Items.BOW) || self.is(Items.CROSSBOW)) && Sights.detach(self, other, action, player, carried)) {
            cir.setReturnValue(true);
        }
    }
}
