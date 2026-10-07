package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Règle de combat : PvE partout, PvP seulement dans les zones déclarées par les administrateurs (cercles nommés,
 * pvp-zones.json). Un joueur ne peut blesser un autre joueur (coup, flèche, grenade) que si les deux sont dans une
 * zone PvP. Un titre s'affiche en entrant ou en sortant d'une zone.
 */
final class PvpZones {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Zone(String name, String dimension, double x, double z, int radius) {
        boolean contains(Player player) {
            return player.level().dimension().identifier().toString().equals(dimension)
                    && Math.hypot(player.getX() - x, player.getZ() - z) <= radius;
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    private final Map<UUID, String> inside = new HashMap<>();
    private Path file;

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("pvp-zones.json");
        zones.clear();
        List<Zone> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<List<Zone>>() { }.getType()));
        if (stored != null) {
            zones.addAll(stored);
        }
    }

    private void save() {
        try {
            JsonStore.write(file, GSON.toJson(zones));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    Zone zoneOf(Player player) {
        for (Zone zone : zones) {
            if (zone.contains(player)) {
                return zone;
            }
        }
        return null;
    }

    /** ALLOW_DAMAGE : refuse les dégâts entre joueurs hors zone PvP (l'attaquant est prévenu). */
    boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer target) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == target) {
            return true;
        }
        if (zoneOf(target) != null && zoneOf(attacker) != null) {
            return true;
        }
        attacker.sendOverlayMessage(Component.literal("Zone sûre : pas de combat entre joueurs ici (zones PvP : /pvp)")
                .withStyle(ChatFormatting.GREEN));
        return false;
    }

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Zone zone = zoneOf(player);
            String now = zone == null ? null : zone.name();
            String before = inside.get(player.getUUID());
            if (java.util.Objects.equals(now, before)) {
                continue;
            }
            if (now == null) {
                inside.remove(player.getUUID());
            } else {
                inside.put(player.getUUID(), now);
            }
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(now == null ? "Zone sûre" : "⚔ Zone PvP")
                    .withStyle(now == null ? ChatFormatting.GREEN : ChatFormatting.RED)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(now == null
                    ? "Les joueurs ne peuvent plus te blesser" : now + " : les joueurs peuvent t'attaquer").withStyle(ChatFormatting.GRAY)));
        }
    }

    void onLeave(ServerPlayer player) {
        inside.remove(player.getUUID());
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pvp")
                .executes(c -> list(c.getSource()))
                .then(Commands.literal("creer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 2000))
                                .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(c -> {
                                    ServerPlayer player = c.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(c, "nom").strip();
                                    int radius = IntegerArgumentType.getInteger(c, "rayon");
                                    zones.removeIf(z -> z.name().equalsIgnoreCase(name));
                                    zones.add(new Zone(name, player.level().dimension().identifier().toString(), player.getX(), player.getZ(), radius));
                                    save();
                                    GeoMod.LOGGER.info("[PVP] {} crée la zone {} (rayon {}) en {}", player.getName().getString(), name, radius,
                                            player.blockPosition());
                                    c.getSource().sendSuccess(() -> Component.literal("Zone PvP « " + name + " » créée ici, rayon " + radius
                                            + " blocs."), true);
                                    return 1;
                                }))))
                .then(Commands.literal("supprimer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("nom", StringArgumentType.greedyString()).executes(c -> {
                            String name = StringArgumentType.getString(c, "nom").strip();
                            boolean removed = zones.removeIf(z -> z.name().equalsIgnoreCase(name));
                            save();
                            if (!removed) {
                                c.getSource().sendFailure(Component.literal("Aucune zone PvP « " + name + " »."));
                                return 0;
                            }
                            GeoMod.LOGGER.info("[PVP] zone {} supprimée", name);
                            c.getSource().sendSuccess(() -> Component.literal("Zone PvP « " + name + " » supprimée."), true);
                            return 1;
                        }))));
    }

    private int list(CommandSourceStack source) {
        if (zones.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucune zone PvP : le combat entre joueurs est désactivé partout.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("PvE partout, PvP seulement dans ces zones :").withStyle(ChatFormatting.GOLD), false);
        for (Zone zone : zones) {
            source.sendSuccess(() -> Component.literal("  ⚔ " + zone.name() + " — " + (int) zone.x() + " / " + (int) zone.z() + ", rayon "
                    + zone.radius() + " (" + zone.dimension() + ")").withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }
}
