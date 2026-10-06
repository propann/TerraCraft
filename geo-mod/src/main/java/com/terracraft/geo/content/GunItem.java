package com.terracraft.geo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arme à feu à tir instantané avec chargeur. La barre de l'objet montre les balles restantes
 * (le « dommage » de l'objet compte les tirs depuis le dernier rechargement). Chargeur vide :
 * rechargement automatique avec les munitions de l'inventaire ; accroupi + clic : recharger.
 * Tirs à la tête ×1,75, dégâts réduits au-delà de la moitié de la portée, recul de la visée.
 */
public class GunItem extends Item {
    /** Caractéristiques d'une arme. {@code fireInterval} : ticks entre deux tirs. */
    public record Stats(float damage, double range, int fireInterval, int magazine, int reloadTicks,
                        int pellets, double spread, float recoil, boolean automatic) {
    }

    private static final float HEADSHOT = 1.75f;
    private final Stats stats;

    public GunItem(Stats stats, Properties properties) {
        super(properties.durability(stats.magazine()));
        this.stats = stats;
    }

    public Stats stats() {
        return stats;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && gun.getDamageValue() > 0) {
            if (level instanceof ServerLevel) {
                reload(player, gun);
            }
            return InteractionResult.SUCCESS;
        }
        if (stats.automatic()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        shoot(level, player, gun);
        return InteractionResult.SUCCESS;
    }

    /** Tir automatique : un tir tous les {@code fireInterval} ticks tant que le clic est maintenu. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack gun, int ticksRemaining) {
        if (entity instanceof Player player && ticksRemaining % stats.fireInterval() == 0) {
            shoot(level, player, gun);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return stats.automatic() ? 72000 : 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    private void shoot(Level level, Player player, ItemStack gun) {
        if (player.getCooldowns().isOnCooldown(gun)) {
            return;
        }
        boolean infinite = player.getAbilities().instabuild;
        if (!infinite && gun.getDamageValue() >= gun.getMaxDamage()) {
            if (level instanceof ServerLevel) {
                reload(player, gun);
            }
            return;
        }
        if (level.isClientSide()) {
            // Recul : la visée remonte un peu à chaque tir (côté client, qui commande la caméra).
            player.setXRot(player.getXRot() - stats.recoil() * (0.7f + level.getRandom().nextFloat() * 0.6f));
            return;
        }
        ServerLevel server = (ServerLevel) level;
        for (int i = 0; i < stats.pellets(); i++) {
            fire(server, player);
        }
        if (!infinite) {
            gun.setDamageValue(gun.getDamageValue() + 1);
        }
        muzzleFlash(server, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, stats.automatic() ? 0.2f : 0.35f, 1.6f + level.getRandom().nextFloat() * 0.4f);
        player.getCooldowns().addCooldown(gun, stats.fireInterval());
        int left = gun.getMaxDamage() - gun.getDamageValue();
        if (player instanceof ServerPlayer sp && (left <= 3 || !stats.automatic())) {
            sp.sendOverlayMessage(Component.literal("Munitions " + left + "/" + gun.getMaxDamage())
                    .withStyle(left == 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
    }

    private void fire(ServerLevel level, Player player) {
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1f).add(
                (level.getRandom().nextDouble() - 0.5) * stats.spread(),
                (level.getRandom().nextDouble() - 0.5) * stats.spread(),
                (level.getRandom().nextDouble() - 0.5) * stats.spread()).normalize();
        Vec3 to = from.add(look.scale(stats.range()));
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
            double distance = from.distanceTo(end);
            float damage = stats.damage();
            if (distance > stats.range() / 2) {
                damage *= (float) (1 - 0.5 * (distance - stats.range() / 2) / (stats.range() / 2));
            }
            boolean headshot = target instanceof LivingEntity living && end.y >= living.getEyeY() - 0.25;
            if (headshot) {
                damage *= HEADSHOT;
                level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 8, 0.1, 0.1, 0.1, 0.2);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER,
                        SoundSource.PLAYERS, 0.5f, 1.4f);
            }
            if (player instanceof ServerPlayer shooter) {
                damage *= (float) com.terracraft.geo.Progression.get().combatMultiplier(shooter);
            }
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

    private static void muzzleFlash(ServerLevel level, Player player) {
        Vec3 look = player.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize().scale(0.35);
        Vec3 muzzle = player.getEyePosition().add(look.scale(0.9)).add(right).add(0, -0.25, 0);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
    }

    /** Recharge le chargeur avec les munitions de l'inventaire (ou indique qu'il n'y en a plus). */
    private void reload(Player player, ItemStack gun) {
        int needed = gun.getDamageValue();
        int taken = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && taken < needed; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModContent.AMMO)) {
                int take = Math.min(stack.getCount(), needed - taken);
                stack.shrink(take);
                taken += take;
            }
        }
        if (taken == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.6f, 1.6f);
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.literal("Plus de munitions").withStyle(ChatFormatting.RED));
            }
            player.getCooldowns().addCooldown(gun, 10);
            return;
        }
        gun.setDamageValue(needed - taken);
        int reloadTicks = player instanceof ServerPlayer sp0
                ? com.terracraft.geo.Progression.get().reloadTicks(sp0, stats.reloadTicks()) : stats.reloadTicks();
        player.getCooldowns().addCooldown(gun, reloadTicks);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                SoundSource.PLAYERS, 0.8f, 1.2f);
        if (player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.literal("Rechargement… " + (gun.getMaxDamage() - gun.getDamageValue()) + "/" + gun.getMaxDamage())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
