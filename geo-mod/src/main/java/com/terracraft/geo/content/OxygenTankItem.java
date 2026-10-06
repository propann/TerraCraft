package com.terracraft.geo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Bouteille d'oxygène : clic droit pour recharger le casque-combinaison porté (5 minutes d'air). */
public class OxygenTankItem extends Item {
    public static final int REFILL = 300;

    public OxygenTankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(ModContent.SPACE_HELMET)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal("Porte d'abord le casque-combinaison spatial").withStyle(ChatFormatting.GOLD));
            }
            return InteractionResult.FAIL;
        }
        if (helmet.getDamageValue() == 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            helmet.setDamageValue(Math.max(0, helmet.getDamageValue() - REFILL));
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8f, 1.6f);
            player.sendOverlayMessage(Component.literal("Oxygène rechargé").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}
