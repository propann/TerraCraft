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
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Véhicule assemblé pièce par pièce : on pose un châssis, on clique dessus avec 4 roues, un
 * moteur, un radiateur et une batterie (le turbo est optionnel), on ajoute de l'essence, puis
 * on monte dedans. Physique côté client du conducteur, comme les bateaux vanilla.
 */
public class Vehicle extends VehicleEntity implements Container {
    public enum Kind {
        CAR(0.62f, 2, 4, 4.5f),
        TRUCK(0.46f, 4, 4, 3.2f),
        MOTORCYCLE(0.82f, 1, 2, 5.2f);

        final float maxSpeed;
        final int seats;
        final int requiredWheels;
        final float turnRate;

        Kind(float maxSpeed, int seats, int requiredWheels, float turnRate) {
            this.maxSpeed = maxSpeed;
            this.seats = seats;
            this.requiredWheels = requiredWheels;
            this.turnRate = turnRate;
        }

        public int requiredWheels() {
            return requiredWheels;
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
    private UUID owner;
    private final Set<UUID> trusted = new HashSet<>();
    private final SimpleContainer storage;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputUp;
    private boolean inputDown;
    private float speed;
    private double odometer;

    public Vehicle(EntityType<? extends Vehicle> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.storage = new SimpleContainer(kind == Kind.TRUCK ? 54 : 27);
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
        return wheels() == kind.requiredWheels && (parts() & REQUIRED) == REQUIRED;
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
        if (owner != null && !canAccess(player)) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(Component.literal("Ce véhicule est verrouillé par un autre joueur.")
                        .withStyle(ChatFormatting.RED));
            }
            return InteractionResult.SUCCESS;
        }
        int part = ModContent.partFlag(item);
        if (part != 0 || item == ModContent.WHEEL || item == ModContent.FUEL_CAN) {
            if (!level().isClientSide()) {
                install(player, stack, item, part);
            }
            return InteractionResult.SUCCESS;
        }
        // Démontage rapide : main vide + Shift-clic droit rend tout le véhicule
        // remontable immédiatement, y compris le carburant restant.
        if (player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, menuPlayer) ->
                                kind == Kind.TRUCK ? ChestMenu.sixRows(id, inventory, this)
                                        : ChestMenu.threeRows(id, inventory, this),
                        Component.literal(kind == Kind.TRUCK ? "Coffre du camion" : "Coffre de la voiture")));
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && isMatchingChassis(item)) {
            if (!level().isClientSide() && level() instanceof ServerLevel server) {
                dismantle(server, player);
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
        if (!level().isClientSide() && owner == null) {
            owner = player.getUUID();
            player.sendSystemMessage(Component.literal("Véhicule enregistré à ton nom. Il est maintenant verrouillé.")
                    .withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    private void install(Player player, ItemStack stack, Item item, int part) {
        boolean used = false;
        if (item == ModContent.WHEEL && wheels() < kind.requiredWheels) {
            entityData.set(DATA_WHEELS, (byte) (wheels() + 1));
            used = true;
        } else if (item == ModContent.FUEL_CAN && fuel() + FUEL_PER_CAN <= MAX_FUEL) {
            // Un bidon est consommé uniquement s'il peut être entièrement stocké.
            entityData.set(DATA_FUEL, fuel() + FUEL_PER_CAN);
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

    private void dismantle(ServerLevel level, Player player) {
        int cans = (fuel() + FUEL_PER_CAN - 1) / FUEL_PER_CAN;
        // Le démontage est une récupération volontaire : on essaie d'abord
        // d'ajouter chaque élément à l'inventaire, puis on ne jette au sol que
        // le surplus si l'inventaire est plein.
        recover(level, player, new ItemStack(getDropItem()));
        if (wheels() > 0) {
            recover(level, player, new ItemStack(ModContent.WHEEL, wheels()));
        }
        for (int flag : new int[]{ENGINE, RADIATOR, BATTERY, TURBO}) {
            if (has(flag)) {
                recover(level, player, new ItemStack(ModContent.partItem(flag)));
            }
        }
        for (ItemStack item : storage.removeAllItems()) {
            if (!item.isEmpty()) {
                recover(level, player, item);
            }
        }
        if (cans > 0) {
            recover(level, player, new ItemStack(ModContent.FUEL_CAN, Math.min(cans, 8)));
        }
        discard();
        player.sendSystemMessage(Component.literal("Véhicule démonté : toutes les pièces sont récupérables.")
                .withStyle(ChatFormatting.GREEN));
    }

    private void recover(ServerLevel level, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            spawnAtLocation(level, stack);
        }
    }

    private boolean isMatchingChassis(Item item) {
        return kind == Kind.CAR && item == ModContent.CAR_CHASSIS
                || kind == Kind.TRUCK && item == ModContent.TRUCK_CHASSIS
                || kind == Kind.MOTORCYCLE && item == ModContent.MOTORCYCLE_CHASSIS;
    }

    public boolean isOwnedBy(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public boolean canAccess(Player player) {
        return owner == null || owner.equals(player.getUUID()) || trusted.contains(player.getUUID());
    }

    public void setTrusted(UUID player, boolean allowed) {
        if (allowed) {
            trusted.add(player);
        } else {
            trusted.remove(player);
        }
    }

    public void clearOwnership(Player player) {
        if (isOwnedBy(player)) {
            owner = null;
            trusted.clear();
        }
    }

    // --- Coffre du véhicule -----------------------------------------------------------------

    @Override
    public int getContainerSize() {
        return storage.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return storage.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return storage.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return storage.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        storage.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        storage.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && owner != null && canAccess(player)
                && player.distanceToSqr(this) <= 64;
    }

    @Override
    public void clearContent() {
        storage.clearContent();
    }

    /** « Il manque : 2 roues, radiateur » ou « Prêt — essence 75 % ». */
    public Component status() {
        List<String> missing = new ArrayList<>();
        if (wheels() < 4) {
            missing.add((kind.requiredWheels - wheels()) + (kind.requiredWheels - wheels() > 1 ? " roues" : " roue"));
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

    /**
     * Les véhicules vanilla accumulent les dégâts, mais notre entité ne demandait
     * jamais leur destruction. Les tirs peuvent maintenant réellement les casser
     * après environ 60 points de dégâts cumulés.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (owner != null && source.getEntity() instanceof Player attacker && !canAccess(attacker)) {
            attacker.sendSystemMessage(Component.literal("Ce véhicule est verrouillé par un autre joueur.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && getDamage() >= 600f) {
            destroy(level, source);
        }
        return hurt;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // --- Destruction et sauvegarde -----------------------------------------------------------

    @Override
    protected Item getDropItem() {
        return switch (kind) {
            case TRUCK -> ModContent.TRUCK_CHASSIS;
            case MOTORCYCLE -> ModContent.MOTORCYCLE_CHASSIS;
            default -> ModContent.CAR_CHASSIS;
        };
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
        for (ItemStack item : storage.removeAllItems()) {
            if (!item.isEmpty()) {
                spawnAtLocation(level, item);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putByte("Parts", (byte) parts());
        output.putByte("Wheels", (byte) wheels());
        output.putInt("Fuel", fuel());
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
        if (!trusted.isEmpty()) {
            output.putString("Trusted", trusted.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(""));
        }
        storage.storeAsItemList(output.list("Items", ItemStack.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setParts(input.getByteOr("Parts", (byte) 0), input.getByteOr("Wheels", (byte) 0), input.getIntOr("Fuel", 0));
        owner = input.getString("Owner").flatMap(Vehicle::parseUuid).orElse(null);
        trusted.clear();
        for (String value : input.getStringOr("Trusted", "").split(",")) {
            if (!value.isBlank()) {
                parseUuid(value).ifPresent(trusted::add);
            }
        }
        storage.fromItemList(input.listOrEmpty("Items", ItemStack.CODEC));
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }
}
