package com.terracraft.geo;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lien avec Open Parties and Claims, appelé par réflexion (pas de dépendance de compilation :
 * si le mod est absent, tout est simplement désactivé). Affiche le propriétaire du territoire
 * quand un joueur change de zone, et compte les claims d'un joueur (tutoriel).
 */
final class Claims {
    record Owner(UUID player, String username, String claimName) {
    }

    private static Method apiGet;
    private static Method claimsManager;
    private static Method chunkClaim;
    private static Method playerInfo;
    private static Method claimCount;
    private static Method chunkOwner;
    private static Method infoUsername;
    private static Method infoClaimsName;
    private static boolean unavailable;

    /** Dernier territoire affiché à chaque joueur (null = zone libre). */
    private final Map<UUID, UUID> shown = new HashMap<>();
    private final Map<UUID, Long> lastChunk = new HashMap<>();

    private static boolean ready() {
        if (unavailable) {
            return false;
        }
        if (apiGet != null) {
            return true;
        }
        if (!FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            unavailable = true;
            return false;
        }
        try {
            // Méthodes prises sur les interfaces publiques de l'API, jamais sur les classes internes.
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Class<?> manager = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            Class<?> info = Class.forName("xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI");
            apiGet = api.getMethod("get", MinecraftServer.class);
            claimsManager = api.getMethod("getServerClaimsManager");
            chunkClaim = manager.getMethod("get", Identifier.class, int.class, int.class);
            playerInfo = manager.getMethod("getPlayerInfo", UUID.class);
            claimCount = info.getMethod("getClaimCount");
            chunkOwner = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
            infoUsername = info.getMethod("getPlayerUsername");
            infoClaimsName = info.getMethod("getClaimsName");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            GeoMod.LOGGER.warn("API Open Parties and Claims inaccessible : affichage des territoires désactivé", e);
            return false;
        }
    }

    private static Object manager(MinecraftServer server) throws ReflectiveOperationException {
        return claimsManager.invoke(apiGet.invoke(null, server));
    }

    /** Nombre de chunks revendiqués par le joueur ; -1 si Open Parties and Claims est absent. */
    static int claimCount(ServerPlayer player) {
        if (!ready()) {
            return -1;
        }
        try {
            Object info = playerInfo.invoke(manager(player.level().getServer()), player.getUUID());
            return info == null ? 0 : (int) claimCount.invoke(info);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Propriétaire du chunk où se trouve le joueur, ou null pour une zone libre. */
    static Owner ownerAt(ServerPlayer player) {
        if (!ready()) {
            return null;
        }
        try {
            Object manager = manager(player.level().getServer());
            Object claim = chunkClaim.invoke(manager, player.level().dimension().identifier(),
                    player.chunkPosition().x(), player.chunkPosition().z());
            if (claim == null) {
                return null;
            }
            UUID owner = (UUID) chunkOwner.invoke(claim);
            Object info = playerInfo.invoke(manager, owner);
            String username = info == null ? "?" : String.valueOf(infoUsername.invoke(info));
            String name = info == null ? null : (String) infoClaimsName.invoke(info);
            return new Owner(owner, username, name == null || name.isBlank() ? null : name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Deux fois par seconde : message quand le joueur entre dans un autre territoire. */
    void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || !ready()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long chunk = player.chunkPosition().pack() ^ ((long) player.level().dimension().hashCode() << 1);
            if (Objects.equals(lastChunk.put(player.getUUID(), chunk), chunk)) {
                continue; // Même chunk : rien à vérifier.
            }
            Owner owner = ownerAt(player);
            UUID now = owner == null ? null : owner.player();
            if (Objects.equals(shown.get(player.getUUID()), now) && shown.containsKey(player.getUUID())) {
                continue;
            }
            shown.put(player.getUUID(), now);
            if (owner == null) {
                player.sendOverlayMessage(Component.literal("Zone libre").withStyle(ChatFormatting.GRAY));
            } else if (owner.player().equals(player.getUUID())) {
                player.sendOverlayMessage(Component.literal("Ton territoire" + (owner.claimName() == null ? "" : " — " + owner.claimName()))
                        .withStyle(ChatFormatting.GREEN));
            } else {
                player.sendOverlayMessage(Component.literal("Territoire de " + owner.username()
                        + (owner.claimName() == null ? "" : " — " + owner.claimName())).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    void onLeave(ServerPlayer player) {
        shown.remove(player.getUUID());
        lastChunk.remove(player.getUUID());
    }
}
