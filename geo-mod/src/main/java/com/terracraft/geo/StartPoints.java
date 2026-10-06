package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.terracraft.geo.world.EarthTerrain;
import com.terracraft.geo.world.ElevationTiles;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.OsmCells;
import com.terracraft.geo.world.WebMercator;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Parcours d'arrivée : le serveur ouvre la carte, reçoit le point choisi, précharge le relief
 * puis téléporte le joueur. Seuls les joueurs invités à choisir peuvent envoyer un point :
 * un client modifié ne peut pas s'en servir pour se téléporter n'importe où.
 */
public final class StartPoints {
    public static final int MAX_LABEL_LENGTH = 120;
    /** Rayon préchargé autour du point, en blocs (couvre la recherche de terre ferme). */
    private static final double PREFETCH_RADIUS = 320;
    private static final int LAND_SEARCH_RADIUS = 256;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Choice(double latitude, double longitude, String label, int x, int y, int z) {
    }

    private final Map<UUID, Choice> choices = new HashMap<>();
    private final Set<UUID> invited = new HashSet<>();
    private final Set<UUID> preparing = new HashSet<>();
    private MinecraftServer server;
    private Path file;

    public void load(MinecraftServer server) {
        this.server = server;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("start_points.json");
        choices.clear();
        invited.clear();
        preparing.clear();
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Choice> stored = GSON.fromJson(Files.readString(file), new TypeToken<Map<String, Choice>>() {
                }.getType());
                if (stored != null) {
                    stored.forEach((uuid, choice) -> choices.put(UUID.fromString(uuid), choice));
                }
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.error("Impossible de lire {}", file, e);
            }
        }
    }

    private void save() {
        Map<String, Choice> stored = new HashMap<>();
        choices.forEach((uuid, choice) -> stored.put(uuid.toString(), choice));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    public static GeoChunkGenerator generator(MinecraftServer server) {
        return server.overworld().getChunkSource().getGenerator() instanceof GeoChunkGenerator generator ? generator : null;
    }

    /** À la connexion : un joueur sans point de départ doit choisir sur la carte. */
    public void onJoin(ServerPlayer player) {
        if (choices.containsKey(player.getUUID()) || generator(server) == null) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, OpenMapPayload.TYPE)) {
            player.sendSystemMessage(Component.literal(
                    "Installe le mod TerraCraft Geo (Fabric) pour choisir ton point de départ sur la carte du monde.")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        // Spectateur en attendant le choix : le monde autour de (0, 0) est l'océan Atlantique.
        player.setGameMode(GameType.SPECTATOR);
        openMap(player, true);
    }

    public void onLeave(ServerPlayer player) {
        invited.remove(player.getUUID());
    }

    public void openMap(ServerPlayer player, boolean required) {
        invited.add(player.getUUID());
        ServerPlayNetworking.send(player, new OpenMapPayload(required));
    }

    public void onChoice(ServerPlayer player, StartPointPayload payload) {
        UUID uuid = player.getUUID();
        if (!invited.contains(uuid) || preparing.contains(uuid)) {
            GeoMod.LOGGER.warn("Point de départ non sollicité ignoré pour {}", player.getName().getString());
            return;
        }
        double latitude = payload.latitude();
        double longitude = payload.longitude();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > WebMercator.MAX_LATITUDE || Math.abs(longitude) > 180) {
            player.sendSystemMessage(Component.literal("Position refusée.").withStyle(ChatFormatting.RED));
            openMap(player, !choices.containsKey(uuid));
            return;
        }
        GeoChunkGenerator generator = generator(server);
        if (generator == null) {
            player.sendSystemMessage(Component.literal(
                    "Ce monde n'utilise pas le générateur TerraCraft (level-type=terracraft_geo:earth).")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String label = payload.label().isBlank() ? "Point choisi" : payload.label().strip();
        EarthTerrain terrain = generator.terrain();
        double x = WebMercator.blockX(longitude, terrain.scale());
        double z = WebMercator.blockZ(latitude, terrain.scale());
        preparing.add(uuid);
        player.sendOverlayMessage(Component.literal("Import du relief, des rues et des bâtiments de " + label + "…")
                .withStyle(ChatFormatting.AQUA));
        CompletableFuture.allOf(terrain.prefetch(x, z, PREFETCH_RADIUS), terrain.osm().prefetch(x, z, 64))
                .orTimeout(150, TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> server.execute(() -> {
                    preparing.remove(uuid);
                    ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                    if (online == null) {
                        return;
                    }
                    if (error != null) {
                        GeoMod.LOGGER.warn("Préchargement du relief incomplet pour {}", label, error);
                    }
                    land(online, terrain, latitude, longitude, label, (int) Math.floor(x), (int) Math.floor(z));
                }));
    }

    private void land(ServerPlayer player, EarthTerrain terrain, double latitude, double longitude, String label,
                      int targetX, int targetZ) {
        int[] spot = findLand(terrain, targetX, targetZ);
        ServerLevel level = server.overworld();
        // Génère le chunk d'arrivée maintenant pour connaître la vraie hauteur (arbres compris).
        level.getChunk(spot[0] >> 4, spot[1] >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot[0], spot[1]);
        BlockPos pos = new BlockPos(spot[0], y, spot[1]);

        boolean firstChoice = !choices.containsKey(player.getUUID());
        invited.remove(player.getUUID());
        // Largage au-dessus du point choisi (chute ralentie) ; la réapparition, elle, se fait au sol.
        int dropY = Math.min(level.getMaxY() - 2, pos.getY() + Arrival.DROP_HEIGHT);
        player.teleportTo(level, pos.getX() + 0.5, dropY, pos.getZ() + 0.5, Set.of(), player.getYRot(), 20, true);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, pos), player.getYRot(), 0), true), false);
        if (firstChoice) {
            player.setGameMode(server.getDefaultGameType());
        }
        Arrival.welcome(player, label, firstChoice);
        choices.put(player.getUUID(), new Choice(latitude, longitude, label, pos.getX(), pos.getY(), pos.getZ()));
        save();

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "Bienvenue à %s (%.4f, %.4f) — 1 bloc ≈ %.2f m.", label, latitude, longitude,
                1.0 / terrain.blocksPerMetre(latitude))).withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal(ElevationTiles.ATTRIBUTION + " · " + OsmCells.ATTRIBUTION)
                .withStyle(ChatFormatting.DARK_GRAY));
        GeoMod.LOGGER.info("{} arrive à {} ({}, {}) → {}", player.getName().getString(), label, latitude, longitude, pos);
    }

    /** Terre ferme, hors eau et hors bâtiment (on atterrit dans la rue, pas sur un toit). */
    private static boolean isGoodSpot(EarthTerrain terrain, int x, int z, int originX, int originZ) {
        if (terrain.elevation(x, z) <= 0) {
            return false;
        }
        if (Math.floorDiv(x, OsmCells.CELL_SIZE) != Math.floorDiv(originX, OsmCells.CELL_SIZE)
                || Math.floorDiv(z, OsmCells.CELL_SIZE) != Math.floorDiv(originZ, OsmCells.CELL_SIZE)) {
            // Cellule voisine pas encore téléchargée : ne pas bloquer le serveur pour elle.
            return true;
        }
        OsmCells.Cell cell = terrain.osm().cellAt(x, z);
        return cell.surface(x, z) != OsmCells.WATER && cell.building(x, z) == null;
    }

    /** Cherche le bon endroit le plus proche en spirale ; reste sur place si rien ne convient. */
    private static int[] findLand(EarthTerrain terrain, int x, int z) {
        if (isGoodSpot(terrain, x, z, x, z)) {
            return new int[]{x, z};
        }
        for (int radius = 8; radius <= LAND_SEARCH_RADIUS; radius += 8) {
            // Un échantillon tous les ~6 blocs sur le cercle.
            for (int step = 0; step < radius; step++) {
                double angle = Math.PI * 2 * step / radius;
                int cx = x + (int) Math.round(Math.cos(angle) * radius);
                int cz = z + (int) Math.round(Math.sin(angle) * radius);
                if (isGoodSpot(terrain, cx, cz, x, z)) {
                    return new int[]{cx, cz};
                }
            }
        }
        return new int[]{x, z};
    }

    public Choice choice(UUID uuid) {
        return choices.get(uuid);
    }
}
