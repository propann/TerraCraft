package com.terracraft.geo;

import com.terracraft.geo.content.Vehicle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Détection du vol en survie. TerraCraft force {@code allow-flight=true} (gravité lunaire,
 * fusées, largage d'arrivée) : le contrôle vanilla qui expulse les joueurs volants est donc
 * coupé, et un client modifié pourrait voler librement. On le remplace par un contrôle adapté.
 *
 * <p>Règle : un joueur (ou la voiture qu'il conduit) en l'air depuis plus de 3 secondes sans
 * être descendu d'au moins un demi-bloc pendant la dernière seconde plane : en gravité normale,
 * une chute fait bien plus. Les cas légitimes sont exemptés (espace, chute lente, élytres, eau,
 * échelles, toiles d'araignée, coups reçus, téléportations, créatif…).
 *
 * <p>Sanction volontairement douce, car un faux positif reste possible : le joueur est ramené
 * au sol, l'événement est journalisé et les opérateurs connectés sont prévenus. Pas d'expulsion.
 */
final class AntiFly {
    /** Temps en l'air avant d'examiner la descente (ticks). */
    private static final int MIN_AIR_TICKS = 60;
    /** Fenêtre d'observation de la descente (ticks). */
    private static final int WINDOW = 20;
    /** Descente minimale attendue sur la fenêtre, en blocs. */
    private static final double MIN_DROP = 0.5;
    /** Déplacement en un tick au-delà duquel on considère une téléportation. */
    private static final double TELEPORT_DISTANCE = 10;
    /** Distance maximale pour ramener le joueur à son dernier appui au sol. */
    private static final double MAX_RETURN_DISTANCE = 96;

    private static final class State {
        Level level;
        Vec3 last;
        Vec3 ground;
        int airTicks;
        final double[] history = new double[WINDOW];
        int flags;
    }

    private final Map<UUID, State> states = new HashMap<>();

    void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            check(server, player);
        }
    }

    void forget(ServerPlayer player) {
        states.remove(player.getUUID());
    }

    private void check(MinecraftServer server, ServerPlayer player) {
        State state = states.computeIfAbsent(player.getUUID(), ignored -> new State());
        Entity subject = player.getVehicle() instanceof Vehicle car ? car
                : player.getVehicle() instanceof com.terracraft.geo.content.Plane plane ? plane : player;
        Vec3 position = subject.position();
        boolean teleported = state.level != player.level() || state.last == null
                || state.last.distanceToSqr(position) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
        state.level = player.level();
        state.last = position;

        if (teleported || exempt(player, subject)) {
            state.airTicks = 0;
            if (subject.onGround()) {
                state.ground = position;
            } else if (teleported) {
                state.ground = null;
            }
            return;
        }
        if (subject.onGround()) {
            state.airTicks = 0;
            state.ground = position;
            return;
        }
        state.history[state.airTicks % WINDOW] = position.y;
        state.airTicks++;
        if (state.airTicks < MIN_AIR_TICKS) {
            return;
        }
        double secondAgo = state.history[state.airTicks % WINDOW];
        if (secondAgo - position.y >= MIN_DROP) {
            return; // Il tombe : rien d'anormal.
        }
        correct(server, player, subject, state);
    }

    private static boolean exempt(ServerPlayer player, Entity subject) {
        if (player.isSpectator() || player.isCreative() || player.getAbilities().mayfly
                || player.isFallFlying() || player.isAutoSpinAttack() || player.isSleeping() || player.isDeadOrDying()) {
            return true;
        }
        // Passager d'une fusée, d'un bateau, d'une monture… : seule la voiture conduite est contrôlée.
        if (player.isPassenger() && subject == player) {
            return true;
        }
        if (SpaceSuit.hasJetpackFuel(player) && player.getLastClientInput().jump()) {
            return true; // Jetpack en marche (carburant limité, décompté par le serveur).
        }
        if (subject instanceof com.terracraft.geo.content.Plane plane && plane.fuel() > 0) {
            return true; // Avion en vol : carburant limité, décompté par le serveur.
        }
        if (Space.isSpace(player.level())) {
            return true; // Gravité réduite : les sauts durent légitimement plusieurs secondes.
        }
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return true; // Largage d'arrivée, potions.
        }
        if (player.hurtTime > 0 || player.getLastHurtByMob() != null) {
            return true; // Recul, explosion, charge de vent.
        }
        if (subject.isInWater() || subject.isInLava() || player.onClimbable()) {
            return true;
        }
        return slowsFall(player.level(), subject.blockPosition()) || slowsFall(player.level(), subject.blockPosition().above());
    }

    private static boolean slowsFall(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.SCAFFOLDING)
                || isHoney(level, pos.north()) || isHoney(level, pos.south())
                || isHoney(level, pos.east()) || isHoney(level, pos.west());
    }

    private static boolean isHoney(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.HONEY_BLOCK);
    }

    private void correct(MinecraftServer server, ServerPlayer player, Entity subject, State state) {
        ServerLevel level = player.level();
        Vec3 position = subject.position();
        Vec3 target = state.ground != null && state.ground.distanceToSqr(position) <= MAX_RETURN_DISTANCE * MAX_RETURN_DISTANCE
                ? state.ground
                : new Vec3(position.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(position).getX(), BlockPos.containing(position).getZ()), position.z);
        if (subject != player) {
            player.stopRiding(); // La voiture, sans conducteur, retombe d'elle-même.
        }
        player.teleportTo(level, target.x, target.y, target.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.resetFallDistance();
        state.airTicks = 0;
        state.last = target;
        state.flags++;

        String where = String.format(Locale.ROOT, "%s %.0f %.0f %.0f", level.dimension().identifier(), position.x, position.y, position.z);
        GeoMod.LOGGER.warn("[ANTITRICHE] {} plane sans tomber{} à {} (signalement n° {})", player.getName().getString(),
                subject != player ? " en voiture" : "", where, state.flags);
        player.sendSystemMessage(Component.literal("Vol non autorisé détecté : retour au sol.").withStyle(ChatFormatting.RED));
        Component alert = Component.literal("[Antitriche] " + player.getName().getString() + " plane sans tomber ("
                + where + ", n° " + state.flags + ")").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online != player && server.getPlayerList().isOp(online.nameAndId())) {
                online.sendSystemMessage(alert);
            }
        }
    }
}
