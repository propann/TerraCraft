package com.terracraft.geo.content;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Grenade : explose à l'impact ; blesse et repousse, mais ne détruit aucun bloc (villes préservées). */
public class Grenade extends ThrowableItemProjectile {
    public Grenade(EntityType<? extends Grenade> type, Level level) {
        super(type, level);
    }

    public Grenade(Level level, LivingEntity owner, ItemStack stack) {
        super(ModContent.GRENADE_ENTITY, owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModContent.GRENADE;
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!level().isClientSide()) {
            level().explode(this, getX(), getY(), getZ(), 3.0f, Level.ExplosionInteraction.NONE);
            discard();
        }
    }
}
