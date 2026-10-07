package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stations : chaque balise de station posée dans l'espace est enregistrée. Une fusée qui arrive
 * dans ce monde se pose à la balise de son pilote (ou d'un habitant de sa ville) au lieu d'un quai
 * au hasard : la station se retrouve toujours, même après un redémarrage.
 */
public final class Stations {
    private static final Stations INSTANCE = new Stations();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Beacon(String owner, String ownerName, String dimension, int x, int y, int z, String name) {
        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private final List<Beacon> beacons = new ArrayList<>();
    private Path file;
    private Towns towns;

    public static Stations get() {
        return INSTANCE;
    }

    void wire(Towns towns) {
        this.towns = towns;
    }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("stations.json");
        beacons.clear();
        List<Beacon> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<List<Beacon>>() { }.getType()));
        if (stored != null) {
            beacons.addAll(stored);
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            JsonStore.write(file, GSON.toJson(beacons));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    /** Balise posée par un joueur. */
    public void placed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!Space.isSpace(level)) {
            player.sendSystemMessage(Component.literal("Balise posée sur Terre : elle ne sert qu'en orbite, sur la Lune ou sur Mars.")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        long mine = beacons.stream().filter(b -> b.owner().equals(player.getUUID().toString())).count();
        String name = "Station de " + player.getName().getString() + (mine == 0 ? "" : " " + (mine + 1));
        beacons.add(new Beacon(player.getUUID().toString(), player.getName().getString(),
                level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), name));
        save();
        GeoMod.LOGGER.info("[STATION] {} pose une balise en {} {}", player.getName().getString(), level.dimension().identifier(), pos);
        player.sendSystemMessage(Component.literal("✦ " + name + " enregistrée : tes fusées se poseront ici. /station nom <nom> pour la renommer.")
                .withStyle(ChatFormatting.AQUA));
    }

    /** Station déployée par un kit : enregistrée au nom du pilote, sans message de pose. */
    public void registerBuilt(ServerPlayer owner, ServerLevel level, BlockPos pos, String name) {
        beacons.add(new Beacon(owner.getUUID().toString(), owner.getName().getString(),
                level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), name));
        save();
    }

    /**
     * Déploie la station orbitale de départ du joueur (kit de station) : le quai est placé sous le point d'arrivée
     * de la fusée, qui s'y pose. Renvoie la position de la balise du quai.
     */
    public BlockPos deployOrbital(ServerPlayer owner, ServerLevel level, double x, double z) {
        BlockPos beacon = new BlockPos((int) Math.floor(x) - 2, Space.DOCK_Y, (int) Math.floor(z));
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                level.getChunk((beacon.getX() >> 4) + cx, (beacon.getZ() >> 4) + cz);
            }
        }
        StationBuilder.buildOrbital(level, beacon);
        registerBuilt(owner, level, beacon, "Station orbitale de " + owner.getName().getString());
        GeoMod.LOGGER.info("[STATION] {} déploie sa station orbitale en {}", owner.getName().getString(), beacon);
        owner.sendSystemMessage(Component.literal("✦ Station orbitale déployée : salle de travail, tunnels, stockage et quai "
                + "d'amarrage. Tes fusées se poseront ici ; les voyages vers la Lune et au-delà partent de ce quai.")
                .withStyle(ChatFormatting.AQUA));
        return beacon;
    }

    /**
     * Base de surface (kit lunaire ou martien) déployée sous une fusée qui vient de se poser en
     * ({@code x}, {@code y}, {@code z}) : l'aire d'atterrissage remplace le sol sous la fusée.
     */
    public BlockPos deploySurfaceBase(ServerPlayer owner, ServerLevel level, double x, int y, double z, String kind) {
        BlockPos beacon = new BlockPos((int) Math.floor(x) - 2, y - 1, (int) Math.floor(z));
        StationBuilder.buildSurfaceBase(level, beacon);
        registerBuilt(owner, level, beacon, kind + " de " + owner.getName().getString());
        GeoMod.LOGGER.info("[STATION] {} déploie sa {} en {} {}", owner.getName().getString(), kind.toLowerCase(java.util.Locale.ROOT),
                level.dimension().identifier(), beacon);
        owner.sendSystemMessage(Component.literal("✦ " + kind + " déployée : aire d'atterrissage, sas, salle de vie avec oxygène et atelier. "
                + "Tes fusées se poseront ici.").withStyle(ChatFormatting.AQUA));
        return beacon;
    }

    /** Une balise de station se trouve-t-elle à moins de {@code radius} blocs (fusée amarrée) ? */
    public boolean dockedNear(ServerLevel level, BlockPos pos, int radius) {
        String dimension = level.dimension().identifier().toString();
        return beacons.stream().anyMatch(b -> b.dimension().equals(dimension) && b.pos().distSqr(pos) <= (double) radius * radius);
    }

    /** Le joueur a-t-il déjà une station dans ce monde ? */
    public boolean hasStation(ServerPlayer owner, ServerLevel level) {
        String dimension = level.dimension().identifier().toString();
        return beacons.stream().anyMatch(b -> b.dimension().equals(dimension) && b.owner().equals(owner.getUUID().toString()));
    }

    public void removed(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        if (beacons.removeIf(b -> b.dimension().equals(dimension) && b.pos().equals(pos))) {
            save();
        }
    }

    /**
     * Point d'arrivée d'une fusée dans ce monde : balise du pilote, sinon d'un habitant de sa ville ;
     * null s'il n'y en a pas (la fusée construit alors un quai).
     */
    public BlockPos landing(ServerPlayer pilot, ServerLevel level) {
        String dimension = level.dimension().identifier().toString();
        Beacon own = null;
        Beacon townMate = null;
        Towns.Town town = towns == null ? null : towns.townOf(pilot.getUUID());
        for (Beacon beacon : beacons) {
            if (!beacon.dimension().equals(dimension)) {
                continue;
            }
            if (beacon.owner().equals(pilot.getUUID().toString())) {
                own = beacon;
            } else if (town != null && town.members.contains(UUID.fromString(beacon.owner()))) {
                townMate = beacon;
            }
        }
        Beacon chosen = own != null ? own : townMate;
        return chosen == null ? null : chosen.pos();
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("station")
                .executes(c -> list(c.getSource().getPlayerOrException()))
                .then(Commands.literal("nom").then(Commands.argument("nom", StringArgumentType.greedyString())
                        .executes(c -> rename(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom"))))));
    }

    private int list(ServerPlayer player) {
        List<Beacon> mine = beacons.stream().filter(b -> b.owner().equals(player.getUUID().toString())).toList();
        player.sendSystemMessage(Component.literal("✦ Tes stations").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (mine.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucune. Pose une balise de station en orbite, sur la Lune ou sur Mars : "
                    + "tes fusées s'y poseront. Agrandis-la avec des modules de station (clic droit sur le sol).")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        for (Beacon beacon : mine) {
            player.sendSystemMessage(Component.literal(beacon.name() + " — " + Space.name(dimensionId(beacon.dimension()))
                    + " (" + beacon.x() + " / " + beacon.y() + " / " + beacon.z() + ")"));
        }
        return mine.size();
    }

    private int rename(ServerPlayer player, String raw) {
        String name = raw.strip();
        if (name.length() < 3 || name.length() > 32) {
            player.sendSystemMessage(Component.literal("Nom de station : 3 à 32 caractères.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String dimension = player.level().dimension().identifier().toString();
        for (int i = 0; i < beacons.size(); i++) {
            Beacon b = beacons.get(i);
            if (b.owner().equals(player.getUUID().toString()) && b.dimension().equals(dimension)
                    && b.pos().distSqr(player.blockPosition()) < 32 * 32) {
                beacons.set(i, new Beacon(b.owner(), b.ownerName(), b.dimension(), b.x(), b.y(), b.z(), name));
                save();
                player.sendSystemMessage(Component.literal("Station renommée : " + name).withStyle(ChatFormatting.GREEN));
                return 1;
            }
        }
        player.sendSystemMessage(Component.literal("Place-toi à moins de 32 blocs d'une de tes balises.").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static byte dimensionId(String dimension) {
        return switch (dimension) {
            case "terracraft_geo:moon" -> Space.MOON_ID;
            case "terracraft_geo:orbit" -> Space.ORBIT_ID;
            case "terracraft_geo:moon_orbit" -> Space.MOON_ORBIT_ID;
            case "terracraft_geo:mars" -> Space.MARS_ID;
            case "terracraft_geo:mars_orbit" -> Space.MARS_ORBIT_ID;
            default -> Space.EARTH;
        };
    }
}
