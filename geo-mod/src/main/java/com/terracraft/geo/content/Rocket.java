package com.terracraft.geo.content;

import com.terracraft.geo.Space;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fusée : coque posée au sol, puis moteur-fusée, réservoir, cône et ailerons ; 4 doses de
 * carburant de fusée. Le passager appuie sur Espace : compte à rebours, décollage, passage
 * Terre ↔ Lune, puis descente freinée jusqu'au sol. Tout le vol est piloté par le serveur.
 */
public class Rocket extends VehicleEntity {
    public static final int ENGINE = 1;
    public static final int TANK = 2;
    public static final int NOSE = 4;
    public static final int FINS = 8;
    private static final int ALL = ENGINE | TANK | NOSE | FINS;
    public static final int FUEL_NEEDED = 4;

    public static final byte IDLE = 0;
    public static final byte COUNTDOWN = 1;
    public static final byte ASCENT = 2;
    public static final byte DESCENT = 3;

    private static final EntityDataAccessor<Byte> DATA_PARTS = SynchedEntityData.defineId(Rocket.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(Rocket.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(Rocket.class, EntityDataSerializers.BYTE);
    /** Destination : {@link Space#EARTH}, {@link Space#MOON_ID} ou {@link Space#ORBIT_ID}. */
    private static final EntityDataAccessor<Byte> DATA_TARGET = SynchedEntityData.defineId(Rocket.class, EntityDataSerializers.BYTE);

    private int timer;
    private double earthX = Double.NaN;
    private double earthZ;

    public Rocket(EntityType<? extends Rocket> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_PARTS, (byte) 0);
        entityData.define(DATA_FUEL, 0);
        entityData.define(DATA_PHASE, IDLE);
        entityData.define(DATA_TARGET, (byte) -1);
    }

    public int parts() {
        return entityData.get(DATA_PARTS);
    }

    public int fuel() {
        return entityData.get(DATA_FUEL);
    }

    public byte phase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean has(int part) {
        return (parts() & part) != 0;
    }

    /** Destination choisie, ou la destination par défaut depuis ce monde. */
    public byte target() {
        byte target = entityData.get(DATA_TARGET);
        return target < 0 || target == Space.id(level()) ? Space.defaultDestination(level()) : target;
    }

    public boolean isComplete() {
        return (parts() & ALL) == ALL;
    }

    // --- Assemblage ----------------------------------------------------------------------------

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        int part = ModContent.rocketPart(stack.getItem());
        if (phase() != IDLE) {
            return InteractionResult.PASS;
        }
        if (part != 0 || stack.is(ModContent.ROCKET_FUEL)) {
            if (!level().isClientSide()) {
                boolean used = false;
                if (part != 0 && !has(part)) {
                    entityData.set(DATA_PARTS, (byte) (parts() | part));
                    used = true;
                } else if (stack.is(ModContent.ROCKET_FUEL) && fuel() < FUEL_NEEDED) {
                    entityData.set(DATA_FUEL, fuel() + 1);
                    used = true;
                }
                if (used) {
                    stack.consume(1, player);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6f, 0.9f);
                }
                player.sendOverlayMessage(status());
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() && stack.isEmpty()) {
            if (!level().isClientSide()) {
                entityData.set(DATA_TARGET, Space.nextDestination(level(), target()));
                player.sendOverlayMessage(Component.literal("Destination : " + Space.name(target())).withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        if (!isComplete()) {
            if (!level().isClientSide()) {
                player.sendOverlayMessage(status());
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) {
            player.startRiding(this);
            player.sendOverlayMessage(fuel() >= FUEL_NEEDED
                    ? Component.literal("Appuie sur Espace pour décoller").withStyle(ChatFormatting.GREEN)
                    : status());
        }
        return InteractionResult.SUCCESS;
    }

    public Component status() {
        List<String> missing = new ArrayList<>();
        if (!has(ENGINE)) {
            missing.add("moteur-fusée");
        }
        if (!has(TANK)) {
            missing.add("réservoir");
        }
        if (!has(NOSE)) {
            missing.add("cône");
        }
        if (!has(FINS)) {
            missing.add("ailerons");
        }
        if (!missing.isEmpty()) {
            return Component.literal("Il manque : " + String.join(", ", missing)).withStyle(ChatFormatting.GOLD);
        }
        return Component.literal("Carburant " + fuel() + "/" + FUEL_NEEDED + " · destination : " + Space.name(target())
                        + (fuel() >= FUEL_NEEDED ? " — prête" : ""))
                .withStyle(fuel() >= FUEL_NEEDED ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
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
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        switch (phase()) {
            case IDLE -> idle();
            case COUNTDOWN -> countdown(server);
            case ASCENT -> ascent(server);
            case DESCENT -> descent(server);
            default -> {
            }
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    private void idle() {
        setDeltaMovement(0, onGround() ? 0 : Math.max(-1.5, getDeltaMovement().y - 0.06), 0);
        if (getFirstPassenger() instanceof ServerPlayer player && player.getLastClientInput().jump()
                && isComplete() && fuel() >= FUEL_NEEDED) {
            if (!Space.hasLifeSupport(player)) {
                if (level().getGameTime() % 20 == 0) {
                    player.sendOverlayMessage(Component.literal("Décollage impossible : équipe le casque-combinaison et recharge son oxygène.")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
                return;
            }
            entityData.set(DATA_PHASE, COUNTDOWN);
            timer = 60;
        }
    }

    private void countdown(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (timer % 20 == 0) {
            int seconds = timer / 20;
            if (getFirstPassenger() instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.literal(seconds > 0 ? "Décollage dans " + seconds + "…" : "Décollage !")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, seconds > 0 ? 1f : 2f);
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 3, 0.4, 0.1, 0.4, 0.02);
        if (--timer <= 0) {
            entityData.set(DATA_FUEL, 0);
            entityData.set(DATA_PHASE, ASCENT);
            timer = 0;
            if (Space.id(level) == Space.EARTH) {
                earthX = getX();
                earthZ = getZ();
            }
            if (getFirstPassenger() instanceof ServerPlayer pilot) {
                com.terracraft.geo.Progression.get().count(pilot, "launches", 1, 40);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3f, 0.5f);
        }
    }

    private void ascent(ServerLevel level) {
        timer++;
        double speed = Math.min(2.2, 0.05 + timer * 0.02);
        setDeltaMovement(0, speed, 0);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.2, getZ(), 6, 0.15, 0.1, 0.15, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() - 0.8, getZ(), 4, 0.3, 0.2, 0.3, 0.02);
        if (timer % 10 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 2f, 0.4f);
        }
        if (timer > 120 || getY() > level.getMaxY() + 40) {
            travel(level);
        }
    }

    /** Passage vers la destination : la fusée et son passager réapparaissent haut dans le ciel. */
    private void travel(ServerLevel from) {
        byte destination = target();
        ServerLevel target = Space.level(from.getServer(), destination);
        if (target == null) {
            entityData.set(DATA_PHASE, DESCENT);
            return;
        }
        boolean home = destination == Space.EARTH && !Double.isNaN(earthX);
        double x = home ? earthX : getX();
        double z = home ? earthZ : getZ();
        double y;
        if (destination == Space.ORBIT_ID) {
            Space.buildDock(target, (int) Math.floor(x), (int) Math.floor(z));
            y = Space.DOCK_Y + 25;
        } else {
            target.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
            int ground = target.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            y = Math.min(target.getMaxY() - 4, ground + 140);
        }
        entityData.set(DATA_PHASE, DESCENT);
        entityData.set(DATA_TARGET, (byte) -1);
        timer = 0;
        Entity arrived = teleport(new TeleportTransition(target, new Vec3(x, y, z), Vec3.ZERO, getYRot(), 0, TeleportTransition.DO_NOTHING));
        if (arrived instanceof Rocket rocket) {
            for (Entity passenger : rocket.getPassengers()) {
                if (passenger instanceof ServerPlayer player) {
                    Space.announceArrival(player, destination);
                }
            }
        }
    }

    private void descent(ServerLevel level) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double above = getY() - ground;
        double speed = above > 30 ? -1.2 : above > 8 ? -0.5 : -0.15;
        setDeltaMovement(0, speed, 0);
        if (above < 30) {
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() - 0.2, getZ(), 3, 0.1, 0.1, 0.1, 0.02);
        }
        if (onGround()) {
            entityData.set(DATA_PHASE, IDLE);
            setDeltaMovement(Vec3.ZERO);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.8f, 0.6f);
            if (getFirstPassenger() instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.literal(switch (Space.id(level)) {
                            case Space.MOON_ID -> "Alunissage réussi";
                            case Space.ORBIT_ID -> "Amarrage réussi";
                            default -> "Retour sur Terre";
                        })
                        .withStyle(ChatFormatting.AQUA));
            }
        }
    }

    // --- Passagers, collisions, sauvegarde ---------------------------------------------------------

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null; // Le serveur pilote : aucun contrôle client.
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && phase() == IDLE;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, 1.0, 0);
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return phase() == IDLE && super.hurtServer(level, source, damage);
    }

    @Override
    protected Item getDropItem() {
        return ModContent.ROCKET_HULL;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModContent.ROCKET_HULL);
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        super.destroy(level, source);
        for (int flag : new int[]{ENGINE, TANK, NOSE, FINS}) {
            if (has(flag)) {
                spawnAtLocation(level, new ItemStack(ModContent.rocketPartItem(flag)));
            }
        }
        if (fuel() > 0) {
            spawnAtLocation(level, new ItemStack(ModContent.ROCKET_FUEL, fuel()));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putByte("Parts", (byte) parts());
        output.putInt("Fuel", fuel());
        output.putByte("Phase", phase());
        output.putInt("Timer", timer);
        output.putByte("Target", entityData.get(DATA_TARGET));
        if (!Double.isNaN(earthX)) {
            output.putDouble("EarthX", earthX);
            output.putDouble("EarthZ", earthZ);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(DATA_PARTS, input.getByteOr("Parts", (byte) 0));
        entityData.set(DATA_FUEL, input.getIntOr("Fuel", 0));
        entityData.set(DATA_PHASE, input.getByteOr("Phase", IDLE));
        timer = input.getIntOr("Timer", 0);
        entityData.set(DATA_TARGET, input.getByteOr("Target", (byte) -1));
        earthX = input.getDoubleOr("EarthX", Double.NaN);
        earthZ = input.getDoubleOr("EarthZ", 0);
    }
}
