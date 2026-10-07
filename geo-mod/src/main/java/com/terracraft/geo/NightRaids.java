package com.terracraft.geo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vagues nocturnes : à la tombée de la nuit, une ville défendue (au moins un habitant à moins de 64 blocs du centre)
 * a une chance sur sept d'être attaquée. Trois vagues convergent vers le centre, espacées d'une minute et demie ou
 * plus tôt si la précédente est presque vaincue. Si 70 % des assaillants tombent avant l'aube, la trésorerie reçoit
 * 100 crédits plus 50 par vague, et les défenseurs présents la découverte « Rempart ».
 */
final class NightRaids {
    private static final int WAVES = 3;
    private static final int WAVE_INTERVAL = 90 * 20;
    private static final double SUCCESS = 0.7;

    private static final class Raid {
        final Towns.Town town;
        final Set<UUID> mobs = new HashSet<>();
        final Set<UUID> defenders = new HashSet<>();
        /** Vague lancée par un administrateur : n'attend pas la nuit, s'arrête au bout de 10 minutes. */
        final boolean forced;
        final long deadline;
        int wave;
        int spawned;
        long nextWave;

        Raid(Towns.Town town, boolean forced, long now) {
            this.town = town;
            this.forced = forced;
            this.deadline = forced ? now + 10 * 60 * 20 : Long.MAX_VALUE;
        }
    }

    private final Towns towns;
    private final List<Raid> raids = new ArrayList<>();
    private boolean wasNight;

    NightRaids(Towns towns) {
        this.towns = towns;
    }

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel earth = server.overworld();
        boolean night = earth.isDarkOutside();
        long now = earth.getGameTime();
        if (night && !wasNight) {
            for (Towns.Town town : towns.all()) {
                if (!defenders(earth, town).isEmpty() && earth.getRandom().nextInt(7) == 0) {
                    start(earth, town, false);
                }
            }
        }
        wasNight = night;
        for (Raid raid : List.copyOf(raids)) {
            raid.mobs.removeIf(id -> {
                Entity mob = earth.getEntity(id);
                return mob == null || !mob.isAlive();
            });
            List<ServerPlayer> present = defenders(earth, raid.town);
            present.forEach(p -> raid.defenders.add(p.getUUID()));
            boolean active = (night || raid.forced) && now < raid.deadline;
            boolean waveDone = raid.mobs.size() <= 2;
            if (raid.wave < WAVES && active && (now >= raid.nextWave || waveDone && raid.wave > 0)) {
                wave(earth, raid, present);
            } else if (!active || raid.wave >= WAVES && raid.mobs.isEmpty()) {
                finish(server, raid);
            }
        }
    }

    /** Commande d'administration : vague immédiate sur la ville du joueur (ou la plus proche). */
    boolean startNow(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || raids.stream().anyMatch(r -> r.town == town)) {
            return false;
        }
        start(player.level().getServer().overworld(), town, true);
        return true;
    }

    private void start(ServerLevel level, Towns.Town town, boolean forced) {
        Raid raid = new Raid(town, forced, level.getGameTime());
        raid.nextWave = level.getGameTime() + 10 * 20;
        raids.add(raid);
        GeoMod.LOGGER.info("[VAGUE] vague nocturne sur {}", town.name);
        towns.tellMembers(town, Component.literal("☾ Des hordes approchent de " + town.name + " : défendez le centre-ville ! ("
                + WAVES + " vagues)").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        for (ServerPlayer player : defenders(level, town)) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Vague nocturne").withStyle(ChatFormatting.DARK_RED)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Défendez " + town.name + " jusqu'à l'aube")
                    .withStyle(ChatFormatting.GOLD)));
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1f);
        }
    }

    private static List<ServerPlayer> defenders(ServerLevel level, Towns.Town town) {
        if (!town.center.dimension().equals(level.dimension().identifier().toString())) {
            return List.of();
        }
        return level.players().stream().filter(p -> !p.isSpectator() && town.members.contains(p.getUUID())
                && Math.hypot(p.getX() - town.center.x(), p.getZ() - town.center.z()) <= Towns.RADIUS).toList();
    }

    private void wave(ServerLevel level, Raid raid, List<ServerPlayer> present) {
        raid.wave++;
        raid.nextWave = level.getGameTime() + WAVE_INTERVAL;
        RandomSource random = level.getRandom();
        int count = Math.min(14, 4 + 2 * raid.wave + 2 * Math.max(1, present.size()));
        int placed = 0;
        for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 28 + random.nextInt(14);
            int x = (int) Math.floor(raid.town.center.x() + Math.cos(angle) * distance);
            int z = (int) Math.floor(raid.town.center.z() + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, (int) raid.town.center.y(), z);
            if (!level.isPositionEntityTicking(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
                continue;
            }
            Mob mob = attacker(level, random, raid.wave);
            if (mob == null) {
                continue;
            }
            mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            if (!level.noCollision(mob, mob.getBoundingBox())) {
                continue;
            }
            if (!present.isEmpty()) {
                mob.setTarget(present.get(random.nextInt(present.size())));
            }
            level.addFreshEntityWithPassengers(mob);
            raid.mobs.add(mob.getUUID());
            placed++;
        }
        raid.spawned += placed;
        GeoMod.LOGGER.info("[VAGUE] {} : vague {}/{} ({} assaillants)", raid.town.name, raid.wave, WAVES, placed);
        towns.tellMembers(raid.town, Component.literal("☾ Vague " + raid.wave + "/" + WAVES + " : " + placed + " assaillants !")
                .withStyle(ChatFormatting.RED));
    }

    private static Mob attacker(ServerLevel level, RandomSource random, int wave) {
        int r = random.nextInt(100);
        EntityType<? extends Mob> type = r < 45 ? EntityTypes.ZOMBIE : r < 70 ? EntityTypes.SKELETON : r < 85 ? EntityTypes.SPIDER
                : wave >= 2 ? EntityTypes.VINDICATOR : EntityTypes.ZOMBIE;
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob != null && type != EntityTypes.SPIDER) {
            // Casque : la vague ne fond pas au lever du soleil avant d'avoir été repoussée.
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(wave >= 3 ? Items.IRON_HELMET : Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.05f);
        }
        return mob;
    }

    private void finish(MinecraftServer server, Raid raid) {
        raids.remove(raid);
        int killed = raid.spawned - raid.mobs.size();
        boolean won = raid.spawned > 0 && killed >= raid.spawned * SUCCESS;
        GeoMod.LOGGER.info("[VAGUE] fin sur {} : {}/{} assaillants tués, {}", raid.town.name, killed, raid.spawned,
                won ? "ville défendue" : "ville débordée");
        if (won) {
            long reward = 100 + 50L * raid.wave;
            towns.reward(raid.town, reward, "vague_nocturne");
            towns.tellMembers(raid.town, Component.literal("☀ " + raid.town.name + " a tenu ! " + killed + " assaillants repoussés : +"
                    + reward + " crédits pour la trésorerie.").withStyle(ChatFormatting.GOLD));
            for (UUID id : raid.defenders) {
                ServerPlayer defender = server.getPlayerList().getPlayer(id);
                if (defender != null) {
                    Progression.get().discover(defender, "rampart");
                }
            }
        } else {
            towns.tellMembers(raid.town, Component.literal("☀ L'aube se lève sur " + raid.town.name + " : " + killed + "/" + raid.spawned
                    + " assaillants repoussés, pas assez pour une récompense.").withStyle(ChatFormatting.GRAY));
        }
    }
}
