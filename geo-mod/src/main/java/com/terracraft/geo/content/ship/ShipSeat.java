package com.terracraft.geo.content.ship;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Place d'un membre d'équipage pendant le vol d'un vaisseau : invisible, elle chevauche le {@link ShipCarrier} à la
 * position du joueur dans le vaisseau (décalage synchronisé, pour que le client place le joueur au même endroit).
 */
public class ShipSeat extends Entity {
    private static final EntityDataAccessor<Vector3fc> DATA_OFFSET = SynchedEntityData.defineId(ShipSeat.class, EntityDataSerializers.VECTOR3);

    private @Nullable UUID crew;

    public ShipSeat(EntityType<? extends ShipSeat> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    void assign(UUID crew, Vec3 offset) {
        this.crew = crew;
        entityData.set(DATA_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
    }

    @Nullable UUID crew() {
        return crew;
    }

    public Vec3 offset() {
        Vector3fc offset = entityData.get(DATA_OFFSET);
        return new Vec3(offset.x(), offset.y(), offset.z());
    }

    void setOffset(Vec3 offset) {
        entityData.set(DATA_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OFFSET, new Vector3f());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !(getVehicle() instanceof ShipCarrier)) {
            ejectPassengers();
            discard(); // Vaisseau posé ou disparu : la place n'a plus de raison d'être.
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        crew = input.read("Crew", UUIDUtil.CODEC).orElse(null);
        input.read("Offset", Vec3.CODEC).ifPresent(this::setOffset);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (crew != null) {
            output.store("Crew", UUIDUtil.CODEC, crew);
        }
        output.store("Offset", Vec3.CODEC, offset());
    }
}
