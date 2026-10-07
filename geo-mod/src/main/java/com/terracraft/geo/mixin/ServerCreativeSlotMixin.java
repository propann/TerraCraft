package com.terracraft.geo.mixin;

import com.terracraft.geo.SpaceSuitSlot;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En mode créatif, le client envoie chaque emplacement modifié de l'inventaire ; le serveur
 * n'accepte que les numéros 1 à 45. On accepte aussi ceux de la combinaison spatiale (46 et plus),
 * avec la même vérification d'objet que les emplacements eux-mêmes.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerCreativeSlotMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleSetCreativeModeSlot", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER))
    private void terracraft$acceptSuitSlots(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        InventoryMenu menu = player.inventoryMenu;
        int index = packet.slotNum();
        if (!player.hasInfiniteMaterials() || index < 0 || index >= menu.slots.size()
                || !(menu.getSlot(index) instanceof SpaceSuitSlot slot)) {
            return;
        }
        ItemStack stack = packet.itemStack();
        if (stack.isEmpty() || slot.mayPlace(stack) && stack.getCount() <= slot.getMaxStackSize()) {
            slot.setByPlayer(stack);
            menu.setRemoteSlot(index, stack);
            menu.broadcastChanges();
        }
        ci.cancel();
    }
}
