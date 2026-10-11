package com.terracraft.geo.content.ship;

import com.mojang.math.Transformation;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.Progression;
import com.terracraft.geo.Space;
import com.terracraft.geo.SpaceTravel;
import com.terracraft.geo.Stations;
import com.terracraft.geo.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vaisseau en vol. Au décollage, ses blocs quittent le monde et deviennent des « block displays » vanilla qui
 * chevauchent ce porteur invisible (le client n'a rien à installer) ; l'équipage le chevauche aussi, chacun à sa place
 * ({@link ShipSeat}). Le porteur monte, change de monde, descend sur l'aire de la station (sas face au sas de la
 * station) ou sur le sol, puis repose les blocs à l'identique et disparaît. Tout est piloté par le serveur et
 * sauvegardé : un vaisseau en vol survit à un redémarrage.
 */
public class ShipCarrier extends Entity {
    static final byte COUNTDOWN = 1;
    static final byte ASCENT = 2;
    static final byte DESCENT = 3;
    private static final int COUNTDOWN_TICKS = 60;
    /** Hauteur d'où le vaisseau redescend vers son point d'arrivée. */
    private static final int DESCENT_HEIGHT = 60;

    private final List<ShipStructure.ShipBlock> blocks = new ArrayList<>();
    private final List<UUID> crew = new ArrayList<>();
    private byte phase = COUNTDOWN;
    private byte destination = -1;
    private int timer = COUNTDOWN_TICKS;
    private double earthX = Double.NaN;
    private double earthZ = Double.NaN;
    private @Nullable BlockPos landing;
    private Rotation rotation = Rotation.NONE;
    private boolean docked;
    private @Nullable UUID pilot;

    public ShipCarrier(EntityType<? extends ShipCarrier> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Décollage d'un vaisseau repéré et validé : les blocs quittent le monde (réservoirs vidés du coût du trajet),
     * l'équipage (joueurs à l'intérieur de la coque) prend place, le compte à rebours commence.
     */
    static ShipCarrier launch(ServerLevel level, ShipStructure.Scan scan, ServerPlayer pilot, byte destination, int cost) {
        BlockPos anchor = scan.anchor();
        List<ServerPlayer> aboard = new ArrayList<>();
        net.minecraft.world.phys.AABB hull = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                scan.blocks().stream().reduce(anchor, BlockPos::min), scan.blocks().stream().reduce(anchor, BlockPos::max)).inflate(0.5);
        for (ServerPlayer player : level.players()) {
            if (player == pilot || !player.isSpectator() && hull.contains(player.position())) {
                aboard.add(player);
            }
        }
        for (ServerPlayer player : aboard) {
            player.stopRiding(); // Sièges de pilote : leurs points d'assise disparaissent avec les blocs.
        }
        ShipCarrier carrier = new ShipCarrier(ShipEntities.CARRIER, level);
        carrier.setPos(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        carrier.destination = destination;
        carrier.pilot = pilot.getUUID();
        if (Space.id(level) == Space.EARTH) {
            carrier.earthX = carrier.getX();
            carrier.earthZ = carrier.getZ();
        }
        carrier.blocks.addAll(ShipStructure.capture(level, scan, cost));
        level.addFreshEntity(carrier);
        carrier.spawnVisuals(level);
        for (ServerPlayer player : aboard) {
            carrier.board(level, player, player.position().subtract(carrier.position()));
        }
        GeoMod.LOGGER.info("[VAISSEAU] {} décolle de {} {} vers {} : {} blocs, {} à bord, {} dose(s)", pilot.getName().getString(),
                level.dimension().identifier(), anchor, Space.name(destination), carrier.blocks.size(), aboard.size(), cost);
        return carrier;
    }

    /** Un block display par bloc, décalé à sa place dans le vaisseau. */
    private void spawnVisuals(ServerLevel level) {
        float span = 1;
        float height = 1;
        for (ShipStructure.ShipBlock block : blocks) {
            span = Math.max(span, 2 * Math.max(Math.abs(block.rel().getX()), Math.abs(block.rel().getZ())) + 2);
            height = Math.max(height, block.rel().getY() + 1);
        }
        for (ShipStructure.ShipBlock block : blocks) {
            Display.BlockDisplay display = net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
            if (display == null) {
                continue;
            }
            display.setPos(position());
            display.setBlockState(block.state());
            display.setTransformation(translation(block.rel()));
            // Boîte de visibilité de tout le vaisseau : un bloc éloigné de l'ancre ne disparaît pas à l'écran.
            display.setWidth(span);
            display.setHeight(height);
            level.addFreshEntity(display);
            display.startRiding(this, true, false);
        }
    }

    private static Transformation translation(BlockPos rel) {
        return new Transformation(new Vector3f(rel.getX() - 0.5f, rel.getY(), rel.getZ() - 0.5f), null, null, null);
    }

    private void board(ServerLevel level, ServerPlayer player, Vec3 offset) {
        ShipSeat seat = new ShipSeat(ShipEntities.SEAT, level);
        seat.assign(player.getUUID(), offset);
        seat.setPos(position().add(offset));
        level.addFreshEntity(seat);
        seat.startRiding(this, true, false);
        player.startRiding(seat, true, false);
        if (!crew.contains(player.getUUID())) {
            crew.add(player.getUUID());
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return passenger instanceof ShipSeat seat ? seat.offset() : Vec3.ZERO;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof ShipSeat || passenger instanceof Display.BlockDisplay;
    }

    // --- Vol -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (blocks.isEmpty()) {
            discard();
            return;
        }
        keepCrewAboard(level);
        switch (phase) {
            case COUNTDOWN -> countdown(level);
            case ASCENT -> ascent(level);
            case DESCENT -> descent(level);
            default -> {
            }
        }
    }

    /** Un membre d'équipage qui descend en plein vol (Maj) est remis à sa place : on ne saute pas d'un vaisseau. */
    private void keepCrewAboard(ServerLevel level) {
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof ShipSeat seat && seat.crew() != null && seat.getPassengers().isEmpty()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(seat.crew());
                if (player != null && player.level() == level && player.distanceToSqr(seat) < 64 * 64 && !player.isSpectator()) {
                    player.teleportTo(seat.getX(), seat.getY(), seat.getZ());
                    player.startRiding(seat, true, false);
                }
            }
        }
        for (Entity passenger : getIndirectPassengers()) {
            passenger.resetFallDistance();
        }
    }

    private void countdown(ServerLevel level) {
        if (timer % 20 == 0) {
            int seconds = timer / 20;
            for (ServerPlayer player : crewOnline(level)) {
                player.sendOverlayMessage(Component.literal(seconds > 0 ? "Décollage dans " + seconds + "…" : "Décollage !")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, seconds > 0 ? 1f : 2f);
        }
        thrusterParticles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, 1);
        if (--timer <= 0) {
            phase = ASCENT;
            timer = 0;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3f, 0.5f);
            ServerPlayer captain = pilot != null ? level.getServer().getPlayerList().getPlayer(pilot) : null;
            if (captain != null) {
                Progression.get().count(captain, "launches", 1, 40);
            }
        }
    }

    private void ascent(ServerLevel level) {
        timer++;
        setPos(getX(), getY() + Math.min(1.6, 0.05 + timer * 0.02), getZ());
        thrusterParticles(level, ParticleTypes.FLAME, 3);
        thrusterParticles(level, ParticleTypes.LARGE_SMOKE, 2);
        if (timer % 10 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 2f, 0.4f);
        }
        if (timer > 100 || getY() > level.getMaxY() + 20) {
            travel(level);
        }
    }

    /**
     * Passage vers la destination. Kits du pilote déployés d'abord (station orbitale, base lunaire ou martienne), puis
     * point de pose : sas du vaisseau face au sas de la station si possible, sinon sur l'aire ou le sol.
     */
    private void travel(ServerLevel from) {
        ServerLevel target = Space.level(from.getServer(), destination);
        if (target == null) {
            phase = DESCENT;
            landing = ShipStructure.restingOrigin(from, getBlockX(), getBlockZ(), rotation, blocks);
            return;
        }
        boolean home = destination == Space.EARTH && !Double.isNaN(earthX);
        double x = home ? earthX : getX();
        double z = home ? earthZ : getZ();
        ServerPlayer captain = pilot != null ? from.getServer().getPlayerList().getPlayer(pilot) : null;
        if (captain != null) {
            deployKits(captain, target, x, z);
        }
        SpaceTravel.Arrival arrival = SpaceTravel.arrival(target, destination, captain, x, z);
        planLanding(target, arrival);
        updateVisuals();
        phase = DESCENT;
        timer = 0;
        double startY = Math.min(target.getMaxY() - 2, landing.getY() + DESCENT_HEIGHT);
        Entity arrived = teleport(new TeleportTransition(target, new Vec3(landing.getX() + 0.5, startY, landing.getZ() + 0.5),
                Vec3.ZERO, getYRot(), 0, TeleportTransition.DO_NOTHING));
        if (arrived instanceof ShipCarrier carrier) {
            for (ServerPlayer player : carrier.crewOnline(target)) {
                Space.announceArrival(player, destination);
            }
        }
    }

    private void deployKits(ServerPlayer captain, ServerLevel target, double x, double z) {
        Stations stations = Stations.get();
        if (stations.hasStation(captain, target)) {
            return;
        }
        if (destination == Space.ORBIT_ID && consume(captain, ModContent.ORBITAL_STATION_KIT)) {
            stations.deployOrbital(captain, target, x, z);
        } else if (destination == Space.MOON_ID || destination == Space.MARS_ID) {
            Item kit = destination == Space.MOON_ID ? ModContent.LUNAR_BASE_KIT : ModContent.MARS_BASE_KIT;
            if (captain.getInventory().contains(new net.minecraft.world.item.ItemStack(kit))) {
                SpaceTravel.Arrival ground = SpaceTravel.arrival(target, destination, null, x, z);
                consume(captain, kit);
                stations.deploySurfaceBase(captain, target, x, ground.groundY() + 1, z,
                        destination == Space.MOON_ID ? "Base lunaire" : "Base martienne");
            }
        }
    }

    private static boolean consume(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                player.getInventory().getItem(slot).shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Choisit l'ancre et l'orientation de pose. */
    private void planLanding(ServerLevel target, SpaceTravel.Arrival arrival) {
        docked = false;
        rotation = Rotation.NONE;
        ShipStructure.ShipBlock airlock = blocks.stream()
                .filter(b -> b.state().is(com.terracraft.geo.content.ShipBlocks.SHIP_AIRLOCK) && b.state().getValue(
                        net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                .findFirst().orElse(null);
        Direction outward = airlock == null ? null : outwardOf(airlock);
        if (arrival.beacon() != null && airlock != null && outward != null) {
            // Aire de station : plancher de la station un bloc au-dessus de l'aire, sas de la station en x - 5.
            rotation = ShipStructure.rotationTo(outward, Direction.WEST);
            BlockPos door = arrival.beacon().offset(-4, 2, 0);
            BlockPos origin = door.subtract(airlock.rel().rotate(rotation));
            if (ShipStructure.fits(target, origin, rotation, blocks)) {
                landing = origin;
                docked = true;
                return;
            }
        }
        BlockPos column = arrival.beacon() != null ? arrival.beacon() : BlockPos.containing(arrival.x(), 0, arrival.z());
        landing = ShipStructure.restingOrigin(target, column.getX(), column.getZ(), rotation, blocks);
    }

    /** Côté extérieur du sas : la face (avant ou arrière de la porte) qui ne donne sur aucun bloc du vaisseau. */
    private @Nullable Direction outwardOf(ShipStructure.ShipBlock airlock) {
        Direction facing = airlock.state().getValue(net.minecraft.world.level.block.DoorBlock.FACING);
        for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos front = airlock.rel().relative(side);
            if (blocks.stream().noneMatch(b -> b.rel().equals(front) || b.rel().equals(front.above()))) {
                return side;
            }
        }
        return null;
    }

    /** Après une rotation de pose : blocs et équipage tournés autour de l'ancre. */
    private void updateVisuals() {
        if (rotation == Rotation.NONE) {
            return;
        }
        int index = 0;
        for (Entity passenger : getPassengers()) {
            if (passenger instanceof Display.BlockDisplay display && index < blocks.size()) {
                ShipStructure.ShipBlock turned = blocks.get(index++).rotated(rotation);
                display.setBlockState(turned.state());
                display.setTransformation(translation(turned.rel()));
            } else if (passenger instanceof ShipSeat seat) {
                Vec3 o = seat.offset();
                Vec3 r = switch (rotation) {
                    case CLOCKWISE_90 -> new Vec3(-o.z, o.y, o.x);
                    case CLOCKWISE_180 -> new Vec3(-o.x, o.y, -o.z);
                    case COUNTERCLOCKWISE_90 -> new Vec3(o.z, o.y, -o.x);
                    default -> o;
                };
                seat.setOffset(r);
            }
        }
    }

    private void descent(ServerLevel level) {
        if (landing == null) {
            landing = ShipStructure.restingOrigin(level, getBlockX(), getBlockZ(), rotation, blocks);
        }
        double above = getY() - landing.getY();
        double speed = above > 30 ? 1.2 : above > 8 ? 0.5 : 0.15;
        setPos(landing.getX() + 0.5, Math.max(landing.getY(), getY() - speed), landing.getZ() + 0.5);
        if (above < 30) {
            thrusterParticles(level, ParticleTypes.FLAME, 2);
        }
        if (getY() <= landing.getY() + 0.001) {
            land(level);
        }
    }

    /** Pose : blocs reposés, équipage déposé à sa place, porteur et displays retirés. */
    private void land(ServerLevel level) {
        BlockPos origin = landing;
        if (!ShipStructure.fits(level, origin, rotation, blocks)) {
            origin = ShipStructure.restingOrigin(level, origin.getX(), origin.getZ(), rotation, blocks);
            docked = false;
        }
        List<Entity> passengers = new ArrayList<>(getPassengers());
        ejectPassengers();
        ShipStructure.place(level, origin, rotation, blocks);
        for (Entity passenger : passengers) {
            if (passenger instanceof ShipSeat seat) {
                Vec3 at = Vec3.atBottomCenterOf(origin).add(seat.offset());
                for (Entity rider : new ArrayList<>(seat.getPassengers())) {
                    rider.stopRiding();
                    rider.teleportTo(at.x, at.y, at.z);
                    rider.resetFallDistance();
                }
            }
            passenger.discard();
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.8f, 0.6f);
        String message = docked ? "Amarrage réussi : le sas du vaisseau est collé à celui de la station."
                : Space.isOrbitId(Space.id(level)) ? "Vaisseau posé sur le quai." : "Vaisseau posé.";
        for (ServerPlayer player : crewOnline(level)) {
            player.sendOverlayMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
        }
        GeoMod.LOGGER.info("[VAISSEAU] posé en {} {} ({} blocs, amarré : {})", level.dimension().identifier(), origin, blocks.size(), docked);
        blocks.clear();
        discard();
    }

    private void thrusterParticles(ServerLevel level, net.minecraft.core.particles.SimpleParticleType particle, int count) {
        for (ShipStructure.ShipBlock block : blocks) {
            if (block.state().is(com.terracraft.geo.content.ShipBlocks.SHIP_THRUSTER)) {
                BlockPos rel = block.rel().rotate(rotation);
                Direction out = block.state().rotate(rotation).getValue(com.terracraft.geo.content.ShipBlocks.ThrusterBlock.FACING);
                level.sendParticles(particle, getX() + rel.getX() + out.getStepX() * 0.7, getY() + rel.getY() + 0.5 + out.getStepY() * 0.7,
                        getZ() + rel.getZ() + out.getStepZ() * 0.7, count, 0.15, 0.15, 0.15, 0.03);
            }
        }
    }

    private List<ServerPlayer> crewOnline(ServerLevel level) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : crew) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    // --- Entité ------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        var list = output.list("Blocks", ShipStructure.ShipBlock.CODEC);
        blocks.forEach(list::add);
        var crewList = output.list("Crew", UUIDUtil.CODEC);
        crew.forEach(crewList::add);
        output.putByte("Phase", phase);
        output.putByte("Destination", destination);
        output.putInt("Timer", timer);
        output.putDouble("EarthX", earthX);
        output.putDouble("EarthZ", earthZ);
        if (landing != null) {
            output.store("Landing", BlockPos.CODEC, landing);
        }
        output.putString("Rotation", rotation.getSerializedName());
        output.putBoolean("Docked", docked);
        if (pilot != null) {
            output.store("Pilot", UUIDUtil.CODEC, pilot);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        blocks.clear();
        input.listOrEmpty("Blocks", ShipStructure.ShipBlock.CODEC).forEach(blocks::add);
        crew.clear();
        input.listOrEmpty("Crew", UUIDUtil.CODEC).forEach(crew::add);
        phase = input.getByteOr("Phase", DESCENT);
        destination = input.getByteOr("Destination", (byte) -1);
        timer = input.getIntOr("Timer", 0);
        earthX = input.getDoubleOr("EarthX", Double.NaN);
        earthZ = input.getDoubleOr("EarthZ", Double.NaN);
        landing = input.read("Landing", BlockPos.CODEC).orElse(null);
        String turn = input.getStringOr("Rotation", "none");
        for (Rotation value : Rotation.values()) {
            if (value.getSerializedName().equals(turn)) {
                rotation = value;
            }
        }
        docked = input.getBooleanOr("Docked", false);
        pilot = input.read("Pilot", UUIDUtil.CODEC).orElse(null);
    }
}
