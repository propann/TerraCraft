package com.terracraft.geo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Véhicule assemblé pièce par pièce : on pose un châssis, on clique dessus avec 4 roues, un
 * moteur, un radiateur et une batterie (le turbo est optionnel), on ajoute de l'essence, puis
 * on monte dedans. Physique côté client du conducteur, comme les bateaux vanilla.
 */
public class Vehicle extends VehicleEntity {
    public enum Kind {
        CAR(0.62f, 2, 4.5f),
        TRUCK(0.46f, 4, 3.2f);

        final float maxSpeed;
        final int seats;
        final float turnRate;

        Kind(float maxSpeed, int seats, float turnRate) {
            this.maxSpeed = maxSpeed;
            this.seats = seats;
            this.turnRate = turnRate;
        }
    }

    public static final int ENGINE = 1;
    public static final int RADIATOR = 2;
    public static final int BATTERY = 4;
    public static final int TURBO = 8;
    private static final int REQUIRED = ENGINE | RADIATOR | BATTERY;
    /** Ticks de conduite apportés par un bidon (≈ 4 minutes). */
    public static final int FUEL_PER_CAN = 20 * 60 * 4;
    public static final int MAX_FUEL = FUEL_PER_CAN * 4;

    private static final EntityDataAccessor<Byte> DATA_PARTS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_WHEELS = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.INT);

    private final Kind kind;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputUp;
    private boolean inputDown;
    private float speed;
    private double odometer;

    public Vehicle(EntityType<? extends Vehicle> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.blocksBuilding = true;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PARTS, (byte) 0);
        entityData.define(DATA_WHEELS, (byte) 0);
        entityData.define(DATA_FUEL, 0);
    }

    public int parts() {
        return entityData.get(DATA_PARTS);
    }

    public int wheels() {
        return entityData.get(DATA_WHEELS);
    }

    public int fuel() {
        return entityData.get(DATA_FUEL);
    }

    public boolean has(int part) {
        return (parts() & part) != 0;
    }

    public void setParts(int parts, int wheels, int fuel) {
        entityData.set(DATA_PARTS, (byte) parts);
        entityData.set(DATA_WHEELS, (byte) Mth.clamp(wheels, 0, 4));
        entityData.set(DATA_FUEL, Mth.clamp(fuel, 0, MAX_FUEL));
    }

    public boolean isComplete() {
        return wheels() == 4 && (parts() & REQUIRED) == REQUIRED;
    }

    public void setInput(boolean left, boolean right, boolean up, boolean down) {
        this.inputLeft = left;
        this.inputRight = right;
        this.inputUp = up;
        this.inputDown = down;
    }

    // --- Assemblage ------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        int part = ModContent.partFlag(item);
        if (part != 0 || item == ModContent.WHEEL || item == ModContent.FUEL_CAN) {
            if (!level().isClientSide()) {
                install(player, stack, item, part);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(status());
            }
            return InteractionResult.SUCCESS;
        }
        if (!isComplete()) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(status());
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private void install(Player player, ItemStack stack, Item item, int part) {
        boolean used = false;
        if (item == ModContent.WHEEL && wheels() < 4) {
            entityData.set(DATA_WHEELS, (byte) (wheels() + 1));
            used = true;
        } else if (item == ModContent.FUEL_CAN && fuel() + FUEL_PER_CAN / 2 <= MAX_FUEL) {
            entityData.set(DATA_FUEL, Math.min(MAX_FUEL, fuel() + FUEL_PER_CAN));
            used = true;
        } else if (part != 0 && !has(part)) {
            entityData.set(DATA_PARTS, (byte) (parts() | part));
            used = true;
        }
        if (used && isComplete() && item != ModContent.FUEL_CAN && player instanceof ServerPlayer builder) {
            // Dernière pièce montée : le véhicule est complet.
            com.terracraft.geo.Progression.get().count(builder, "vehicles", 1, 20);
        }
        if (used) {
            stack.consume(1, player);
            level().playSound(null, getX(), getY(), getZ(),
                    item == ModContent.FUEL_CAN ? SoundEvents.BUCKET_EMPTY : SoundEvents.ANVIL_USE,
                    SoundSource.NEUTRAL, 0.6f, 1.2f);
        }
        player.sendOverlayMessage(status());
    }

    /** « Il manque : 2 roues, radiateur » ou « Prêt — essence 75 % ». */
    public Component status() {
        List<String> missing = new ArrayList<>();
        if (wheels() < 4) {
            missing.add((4 - wheels()) + (4 - wheels() > 1 ? " roues" : " roue"));
        }
        if (!has(ENGINE)) {
            missing.add("moteur");
        }
        if (!has(RADIATOR)) {
            missing.add("radiateur");
        }
        if (!has(BATTERY)) {
            missing.add("batterie");
        }
        int fuelPercent = Math.round(100f * fuel() / MAX_FUEL);
        if (!missing.isEmpty()) {
            return Component.literal("Il manque : " + String.join(", ", missing)).withStyle(ChatFormatting.GOLD);
        }
        String turbo = has(TURBO) ? " · turbo" : "";
        return Component.literal("Prêt" + turbo + " — essence " + fuelPercent + " %")
                .withStyle(fuel() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // --- Conduite --------------------------------------------------------------------------

    @Override
    public void tick() {
        if (getHurtTime() > 0) {
            setHurtTime(getHurtTime() - 1);
        }
        if (getDamage() > 0) {
            setDamage(getDamage() - 1);
        }
        super.tick();
        if (isLocalInstanceAuthoritative()) {
            if (level().isClientSide() && getControllingPassenger() instanceof Player) {
                drive();
            } else {
                speed *= 0.8f;
            }
            Vec3 forward = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            double vertical = onGround() ? -0.04 : getDeltaMovement().y - 0.08;
            setDeltaMovement(forward.x * speed, Math.max(-2.5, vertical), forward.z * speed);
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision) {
                speed *= 0.3f;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (!level().isClientSide() && getControllingPassenger() != null && fuel() > 0) {
            double moved = Math.hypot(getX() - xo, getZ() - zo);
            boolean saved = getControllingPassenger() instanceof ServerPlayer driver && com.terracraft.geo.Progression.get().saveFuel(driver);
            if (moved > 0.02 && !saved) {
                entityData.set(DATA_FUEL, fuel() - 1);
            }
            if (moved > 0.02) {
                odometer += moved;
                if (odometer >= 100 && getControllingPassenger() instanceof ServerPlayer driver) {
                    com.terracraft.geo.Progression.get().count(driver, "driven", 100, 1);
                    odometer -= 100;
                }
            }
        }
    }

    private void drive() {
        boolean canDrive = isComplete() && fuel() > 0;
        float max = kind.maxSpeed * (has(TURBO) ? 1.4f : 1f);
        if (canDrive && inputUp) {
            speed += 0.02f * (has(TURBO) ? 1.5f : 1f);
        } else if (canDrive && inputDown) {
            speed -= speed > 0 ? 0.05f : 0.012f;
        } else {
            speed *= 0.96f;
        }
        speed = Mth.clamp(speed, -max * 0.35f, max);
        if (Math.abs(speed) > 0.01f) {
            float steer = (inputLeft ? -1 : 0) + (inputRight ? 1 : 0);
            float grip = Math.min(1f, Math.abs(speed) / 0.2f) * Math.signum(speed);
            setYRot(getYRot() + steer * kind.turnRate * grip);
        }
    }

    @Override
    public float maxUpStep() {
        return 1.05f; // Monte les trottoirs et les marches d'un bloc.
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < kind.seats;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = Math.max(0, getPassengers().indexOf(passenger));
        double side = (index % 2 == 0 ? -0.35 : 0.35) * (kind == Kind.TRUCK ? 1.3 : 1);
        double along = kind == Kind.TRUCK ? (index < 2 ? 1.0 : -0.6) : 0.1;
        return new Vec3(side, dimensions.height() * 0.35, along).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger == getControllingPassenger()) {
            passenger.setYBodyRot(getYRot());
        }
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // --- Destruction et sauvegarde -----------------------------------------------------------

    @Override
    protected Item getDropItem() {
        return kind == Kind.TRUCK ? ModContent.TRUCK_CHASSIS : ModContent.CAR_CHASSIS;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    /** Casser le véhicule rend le châssis et les pièces montées. */
    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        super.destroy(level, source);
        if (wheels() > 0) {
            spawnAtLocation(level, new ItemStack(ModContent.WHEEL, wheels()));
        }
        for (int flag : new int[]{ENGINE, RADIATOR, BATTERY, TURBO}) {
            if (has(flag)) {
                spawnAtLocation(level, new ItemStack(ModContent.partItem(flag)));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putByte("Parts", (byte) parts());
        output.putByte("Wheels", (byte) wheels());
        output.putInt("Fuel", fuel());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setParts(input.getByteOr("Parts", (byte) 0), input.getByteOr("Wheels", (byte) 0), input.getIntOr("Fuel", 0));
    }
}
