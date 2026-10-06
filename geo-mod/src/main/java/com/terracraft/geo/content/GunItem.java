package com.terracraft.geo.content;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arme à feu à tir instantané (« hitscan ») : chaque tir consomme une munition, touche la
 * première entité dans l'axe de visée (portée limitée par les blocs) et laisse une traînée.
 */
public class GunItem extends Item {
    private final float damage;
    private final double range;
    private final int cooldown;
    private final int pellets;
    private final double spread;

    public GunItem(float damage, double range, int cooldown, int pellets, double spread, Properties properties) {
        super(properties);
        this.damage = damage;
        this.range = range;
        this.cooldown = cooldown;
        this.pellets = pellets;
        this.spread = spread;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild && !consumeAmmo(player)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
            player.getCooldowns().addCooldown(gun, 10);
            return InteractionResult.FAIL;
        }
        for (int i = 0; i < pellets; i++) {
            fire(server, player);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 0.35f, 1.8f + level.getRandom().nextFloat() * 0.3f);
        player.getCooldowns().addCooldown(gun, cooldown);
        return InteractionResult.SUCCESS;
    }

    private void fire(ServerLevel level, Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1f).add(
                (level.getRandom().nextDouble() - 0.5) * spread,
                (level.getRandom().nextDouble() - 0.5) * spread,
                (level.getRandom().nextDouble() - 0.5) * spread).normalize();
        Vec3 to = from.add(look.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            to = block.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(to.subtract(from)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, area,
                e -> e.isPickable() && !e.isSpectator() && e != player.getVehicle(), from.distanceToSqr(to));
        Vec3 end = to;
        if (hit != null) {
            end = hit.getLocation();
            Entity target = hit.getEntity();
            // Les dégâts appliquent aussi le recul vanilla.
            target.hurtServer(level, level.damageSources().playerAttack(player), damage);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.01);
        }
        double length = from.distanceTo(end);
        for (double d = 1.2; d < length; d += 1.5) {
            Vec3 p = from.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static boolean consumeAmmo(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModContent.AMMO)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
