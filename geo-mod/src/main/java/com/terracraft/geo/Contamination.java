package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Zones contaminées sur la Terre : fixes (une au plus par carré de 1 024 blocs, 18 % des carrés), rayon de 40 à
 * 110 blocs. Sans casque et combinaison spatiaux on y subit poison et faim, et du wither au cœur de la zone. Un
 * compteur dans la barre d'action prévient à l'approche. Au centre, une cache de matériel, posée la première fois
 * qu'un joueur protégé atteint le cœur (une seule par zone, mémorisée dans contamination.json).
 */
final class Contamination {
    static final int CELL = 1024;
    private static final int WARNING = 30;
    private static final Gson GSON = new Gson();
    private static final ResourceKey<LootTable> CACHE_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/contaminated_cache"));

    record Zone(int x, int z, int radius) {
        String id() {
            return x + "," + z;
        }
    }

    private final Set<String> caches = new HashSet<>();
    private Path file;

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("contamination.json");
        caches.clear();
        Set<String> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Set<String>>() { }.getType()));
        if (stored != null) {
            caches.addAll(stored);
        }
    }

    private void save() {
        try {
            JsonStore.write(file, GSON.toJson(caches));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    /** Zone du carré contenant (x, z), ou null. Le centre reste à 160 blocs des bords du carré. */
    static Zone zone(int x, int z) {
        int cx = Math.floorDiv(x, CELL);
        int cz = Math.floorDiv(z, CELL);
        long h = hash(cx, cz);
        if (Math.floorMod(h, 100) >= 18) {
            return null;
        }
        int margin = 160;
        return new Zone(cx * CELL + margin + (int) Math.floorMod(h >>> 8, CELL - 2 * margin),
                cz * CELL + margin + (int) Math.floorMod(h >>> 28, CELL - 2 * margin), 40 + (int) Math.floorMod(h >>> 48, 71));
    }

    /** Zone la plus proche (carrés voisins compris) de (x, z), ou null à plus de 2 000 blocs. */
    static Zone nearest(int x, int z) {
        Zone best = null;
        double distance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Zone zone = zone(x + dx * CELL, z + dz * CELL);
                if (zone != null && Math.hypot(zone.x - x, zone.z - z) < distance) {
                    distance = Math.hypot(zone.x - x, zone.z - z);
                    best = zone;
                }
            }
        }
        return best;
    }

    private static long hash(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ 0x5DEECE66DL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ h >>> 29;
    }

    static boolean protectedFrom(ServerPlayer player) {
        return !SpaceSuit.helmet(player).isEmpty() && SpaceSuit.hasSuit(player);
    }

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel earth = server.overworld();
        for (ServerPlayer player : earth.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Zone zone = nearest(player.getBlockX(), player.getBlockZ());
            if (zone == null) {
                continue;
            }
            double distance = Math.hypot(player.getX() - zone.x, player.getZ() - zone.z);
            if (distance > zone.radius + WARNING) {
                continue;
            }
            boolean safe = protectedFrom(player) || player.isCreative();
            if (distance > zone.radius) {
                player.sendOverlayMessage(Component.literal("☢ Radiation en hausse : zone contaminée à " + (int) (distance - zone.radius)
                        + " blocs" + (safe ? "" : " — casque et combinaison spatiaux requis")).withStyle(ChatFormatting.YELLOW));
                continue;
            }
            boolean core = distance < zone.radius / 2.0;
            Progression.get().discover(player, "contamination");
            earth.sendParticles(player, ParticleTypes.WARPED_SPORE, false, false, player.getX(), player.getY() + 1, player.getZ(),
                    12, 3, 1.5, 3, 0.01);
            if (safe) {
                player.sendOverlayMessage(Component.literal("☢ Zone contaminée" + (core ? " — cœur de la zone" : "")
                        + " · ta combinaison filtre l'air").withStyle(ChatFormatting.GREEN));
                if (core) {
                    placeCache(earth, zone, player);
                }
                continue;
            }
            player.sendOverlayMessage(Component.literal("☢ ZONE CONTAMINÉE — sors ou équipe casque et combinaison (J)")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1, false, true));
            if (core) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
            }
            if (server.getTickCount() % 60 == 0) {
                earth.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
            }
        }
    }

    /** Cache de matériel au centre de la zone, une seule fois (premier joueur protégé à atteindre le cœur). */
    private void placeCache(ServerLevel level, Zone zone, ServerPlayer finder) {
        if (caches.contains(zone.id())) {
            return;
        }
        BlockPos column = new BlockPos(zone.x, 0, zone.z);
        if (!level.isPositionEntityTicking(column.atY(finder.getBlockY()))) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, zone.x, zone.z);
        BlockPos pos = new BlockPos(zone.x, y, zone.z);
        if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos.below()).isEmpty()) {
            return;
        }
        caches.add(zone.id());
        save();
        level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, CACHE_LOOT);
        level.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_TORCH.defaultBlockState());
        GeoMod.LOGGER.info("[CONTAMINATION] cache posée en {} {} {} pour {}", zone.x, y, zone.z, finder.getName().getString());
        finder.sendSystemMessage(Component.literal("☢ Une cache de matériel apparaît au cœur de la zone, en " + zone.x + " / " + y
                + " / " + zone.z + " (torche rouge).").withStyle(ChatFormatting.GOLD));
    }
}
