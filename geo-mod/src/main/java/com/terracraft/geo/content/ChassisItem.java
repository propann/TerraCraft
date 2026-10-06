package com.terracraft.geo.content;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/** Châssis ou coque de fusée : posé au sol, il devient un véhicule vide à équiper. */
public class ChassisItem extends Item {
    private final Supplier<? extends EntityType<?>> type;

    public ChassisItem(Supplier<? extends EntityType<?>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        Vec3 at = hit.getLocation();
        net.minecraft.world.entity.Entity vehicle = type.get().create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (vehicle == null) {
            return InteractionResult.FAIL;
        }
        vehicle.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        if (!level.noCollision(vehicle, vehicle.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.addFreshEntity(vehicle);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
