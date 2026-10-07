package com.terracraft.geo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Rover lunaire en caisse : clic droit sur le sol pour le déballer, complet et batterie pleine. */
public class RoverKitItem extends Item {
    public RoverKitItem(Properties properties) {
        super(properties);
    }

    /** Déballe un rover complet à cet endroit ; renvoie null si la place manque. */
    public static Vehicle unpack(ServerLevel level, Vec3 at, float yaw) {
        Vehicle rover = ModContent.ROVER.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (rover == null) {
            return null;
        }
        rover.snapTo(at.x, at.y, at.z, yaw, 0);
        if (!level.noCollision(rover, rover.getBoundingBox())) {
            return null;
        }
        rover.setParts(Vehicle.ENGINE | Vehicle.RADIATOR | Vehicle.BATTERY, 4, Vehicle.MAX_FUEL);
        rover.markAssembled();
        level.addFreshEntity(rover);
        level.playSound(null, BlockPos.containing(at), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1f, 0.7f);
        return rover;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 at = context.getClickLocation();
        if (unpack(level, at, player.getYRot()) == null) {
            player.sendOverlayMessage(Component.literal("Pas assez de place pour déballer le rover.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        player.sendOverlayMessage(Component.literal("Rover déballé : clic droit pour monter, il se recharge à l'arrêt.")
                .withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }
}
