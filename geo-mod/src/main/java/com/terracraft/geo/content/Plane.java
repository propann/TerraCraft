package com.terracraft.geo.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Avion léger : 2 places, carburant au bidon d'essence. Pilotage simple : Z/S règlent les gaz,
 * le regard du pilote donne le cap et l'assiette, Q/D accentuent le virage. Au-delà de la vitesse
 * de portance, regarder vers le haut fait décoller ; en dessous, l'avion décroche et pique.
 * Comme les bateaux vanilla, la physique tourne chez le pilote ; le serveur décompte le carburant
 * et détecte les crashs (forte décélération).
 */
public class Plane extends VehicleEntity {
    public static final int MAX_FUEL = 2400;
    private static final int FUEL_PER_CAN = 300;
    /** Vitesses en blocs/tick : 1,5 ≈ 108 km/h. */
    public static final float MAX_SPEED = 1.5f;
    public static final float LIFT_SPEED = 0.6f;
    private static final float GROUND_SPEED = 0.5f;

    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Plane.class, EntityDataSerializers.INT);

    private UUID owner;
    private boolean inputUp;
    private boolean inputDown;
    private boolean inputLeft;
    private boolean inputRight;
    /** Côté pilote : gaz (0 à 1) et vitesse le long du cap. */
    private float throttle;
    private float speed;
    /** Côté serveur : vitesse du tick précédent, pour détecter les chocs. */
    private double lastSpeed;
    /** Dernière position vue par le serveur (xo/yo/zo y sont remis à jour avant le tick : inutilisables). */
    private @Nullable Vec3 lastServerPos;
    private int fuelTicks;

    public Plane(EntityType<? extends Plane> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FUEL, 0);
    }

    public int fuel() {
        return entityData.get(DATA_FUEL);
    }

    public float throttle() {
        return throttle;
    }

    public float speed() {
        return speed;
    }

    public void setInput(boolean up, boolean down, boolean left, boolean right) {
        this.inputUp = up;
        this.inputDown = down;
        this.inputLeft = left;
        this.inputRight = right;
    }

    // --- Interaction ---------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        if (owner != null && !owner.equals(player.getUUID())) {
            // Un autre joueur peut monter comme passager quand le propriétaire est aux commandes.
            boolean ownerFlying = getControllingPassenger() != null && owner.equals(getControllingPassenger().getUUID());
            if (!level().isClientSide()) {
                if (ownerFlying && !player.isSecondaryUseActive() && player.startRiding(this)) {
                    player.sendSystemMessage(Component.literal("Tu embarques comme passager.").withStyle(ChatFormatting.GREEN));
                } else {
                    player.sendOverlayMessage(Component.literal("Cet avion appartient à un autre joueur : monte quand il est aux commandes.")
                            .withStyle(ChatFormatting.RED));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModContent.FUEL_CAN)) {
            if (!level().isClientSide()) {
                if (fuel() + FUEL_PER_CAN > MAX_FUEL) {
                    player.sendOverlayMessage(Component.literal("Réservoir plein.").withStyle(ChatFormatting.GRAY));
                } else {
                    entityData.set(DATA_FUEL, fuel() + FUEL_PER_CAN);
                    stack.consume(1, player);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.7f, 1f);
                    player.sendOverlayMessage(status());
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(status());
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide() && player.startRiding(this) && owner == null) {
            owner = player.getUUID();
            player.sendSystemMessage(Component.literal("Avion enregistré à ton nom. Z/S : gaz · regard : cap et assiette · "
                    + "Q/D : virage · Maj : descendre.").withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    public Component status() {
        int percent = Math.round(100f * fuel() / MAX_FUEL);
        return Component.literal("Avion — carburant " + percent + " %" + (fuel() == 0 ? " (bidon d'essence : clic droit)" : ""))
                .withStyle(fuel() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // --- Vol -----------------------------------------------------------------------------------

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
            if (level().isClientSide() && getControllingPassenger() instanceof Player pilot) {
                fly(pilot);
            } else {
                // Sans pilote : l'avion freine et retombe.
                throttle = 0;
                speed *= 0.9f;
                Vec3 forward = forward(0);
                double vertical = onGround() ? -0.04 : Math.max(-1.5, getDeltaMovement().y - 0.06);
                setDeltaMovement(forward.x * speed, vertical, forward.z * speed);
                move(MoverType.SELF, getDeltaMovement());
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (!level().isClientSide() && level() instanceof ServerLevel server) {
            serverTick(server);
        }
    }

    private Vec3 forward(float pitchDegrees) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        float pitch = pitchDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    private void fly(Player pilot) {
        boolean hasFuel = fuel() > 0;
        if (inputUp && hasFuel) {
            throttle = Math.min(1f, throttle + 0.02f);
        } else if (inputDown || !hasFuel) {
            throttle = Math.max(0f, throttle - 0.03f);
        }
        float target = throttle * MAX_SPEED;
        speed += Mth.clamp(target - speed, -0.02f, 0.015f);
        if (onGround()) {
            speed = Math.min(speed, inputDown ? speed * 0.9f : speed);
        }

        // Cap : l'avion suit le regard du pilote, Q/D accentuent le virage.
        float turnRate = (onGround() ? 3f : 2.2f) * Math.min(1f, speed / 0.3f);
        float yawDelta = Mth.wrapDegrees(pilot.getYRot() - getYRot());
        float steer = (inputLeft ? -1.5f : 0f) + (inputRight ? 1.5f : 0f);
        setYRot(getYRot() + Mth.clamp(yawDelta, -turnRate, turnRate) + steer * Math.min(1f, speed));

        float pitch;
        double vertical;
        if (speed >= LIFT_SPEED) {
            // Portance : l'assiette suit le regard (vers le haut = nez levé = xRot négatif).
            float wanted = Mth.clamp(pilot.getXRot(), -30f, 35f);
            if (onGround() && wanted > 0) {
                wanted = 0; // On ne pique pas dans le sol.
            }
            pitch = Mth.approachDegrees(getXRot(), wanted, 1.5f);
            vertical = -Mth.sin(pitch * Mth.DEG_TO_RAD) * speed;
        } else {
            // Décrochage : le nez tombe, la gravité reprend la main.
            pitch = onGround() ? Mth.approachDegrees(getXRot(), 0, 3f) : Mth.approachDegrees(getXRot(), 25f, 1f);
            vertical = onGround() ? -0.04 : Math.max(-1.2, getDeltaMovement().y - 0.05);
            if (onGround()) {
                speed = Math.min(speed, GROUND_SPEED + throttle * LIFT_SPEED);
            }
        }
        setXRot(pitch);
        Vec3 flat = forward(0).scale(speed * Mth.cos(pitch * Mth.DEG_TO_RAD));
        setDeltaMovement(flat.x, vertical, flat.z);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) {
            speed *= 0.2f;
            throttle *= 0.5f;
        }
        resetFallDistance();
    }

    private void serverTick(ServerLevel level) {
        Vec3 position = position();
        double current = lastServerPos == null ? 0 : position.distanceTo(lastServerPos);
        lastServerPos = position;
        if (current > 8) {
            current = 0; // Téléportation, changement de dimension : pas un déplacement.
        }
        LivingEntity pilot = getControllingPassenger();
        if (pilot != null && current > 0.05 && fuel() > 0 && ++fuelTicks % 2 == 0) {
            entityData.set(DATA_FUEL, fuel() - 1);
        }
        // Crash : la vitesse s'effondre d'un coup (mur, sol, montagne).
        if (lastSpeed > 0.9 && current < lastSpeed * 0.3 && pilot != null) {
            crash(level, lastSpeed);
        }
        lastSpeed = current;
        resetFallDistance();
        for (Entity passenger : getPassengers()) {
            passenger.resetFallDistance();
        }
        if (pilot instanceof ServerPlayer player && tickCount % 10 == 0 && !onGround()) {
            player.sendOverlayMessage(Component.literal("Altitude " + Math.round(getY()) + " · carburant "
                    + Math.round(100f * fuel() / MAX_FUEL) + " %").withStyle(fuel() > MAX_FUEL / 10 ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
        if (pilot != null && current > 0.3 && tickCount % 4 == 0) {
            Vec3 back = forward(0).scale(-1.6);
            level.sendParticles(ParticleTypes.SMOKE, getX() + back.x, getY() + 0.8, getZ() + back.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
        if (pilot != null && fuel() > 0 && tickCount % 20 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.5f, 1.6f);
        }
    }

    private void crash(ServerLevel level, double impactSpeed) {
        float damage = (float) Math.min(30, impactSpeed * 14);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 0.5, 0.3, 0.5, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1f, 1.2f);
        for (Entity passenger : getPassengers()) {
            passenger.hurtServer(level, damageSources().flyIntoWall(), damage);
            if (passenger instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Crash ! L'avion est endommagé.").withStyle(ChatFormatting.RED));
            }
        }
        setDamage(getDamage() + damage * 15);
        if (getDamage() >= 600) {
            destroy(level, damageSources().flyIntoWall());
        }
    }

    // --- Passagers -----------------------------------------------------------------------------

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int index = Math.max(0, getPassengers().indexOf(passenger));
        return new Vec3(0, dimensions.height() * 0.3, index == 0 ? 0.35 : -0.55).yRot(-getYRot() * Mth.DEG_TO_RAD);
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

    @Override
    public float maxUpStep() {
        return 1.05f;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (owner != null && source.getEntity() instanceof Player attacker && !owner.equals(attacker.getUUID())) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && getDamage() >= 600f) {
            destroy(level, source);
        }
        return hurt;
    }

    // --- Destruction et sauvegarde ---------------------------------------------------------------

    @Override
    protected Item getDropItem() {
        return ModContent.PLANE_KIT;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModContent.PLANE_KIT);
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        super.destroy(level, source);
        int cans = fuel() / FUEL_PER_CAN;
        if (cans > 0) {
            spawnAtLocation(level, new ItemStack(ModContent.FUEL_CAN, Math.min(cans, 8)));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Fuel", fuel());
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(DATA_FUEL, Mth.clamp(input.getIntOr("Fuel", 0), 0, MAX_FUEL));
        owner = input.getString("Owner").flatMap(Plane::parseUuid).orElse(null);
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
