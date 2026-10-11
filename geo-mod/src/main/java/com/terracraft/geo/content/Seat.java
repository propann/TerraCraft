package com.terracraft.geo.content;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Point d'assise invisible d'un siège de pilote : il existe tant qu'on est assis et que le siège est là. Jamais
 * sauvegardé (le joueur se relève à la déconnexion).
 */
public class Seat extends Entity {
    public Seat(EntityType<? extends Seat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (!isVehicle() || !level().getBlockState(blockPosition()).is(ShipBlocks.PILOT_SEAT))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
