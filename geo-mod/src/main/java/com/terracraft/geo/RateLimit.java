package com.terracraft.geo;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Délai minimal entre deux actions d'un même joueur (thread serveur uniquement). */
final class RateLimit {
    private final long intervalMs;
    private final Map<UUID, Long> last = new HashMap<>();

    RateLimit(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    boolean allow(ServerPlayer player) {
        return remainingMs(player) == 0 && mark(player);
    }

    /** Temps restant avant la prochaine action autorisée, sans la consommer. */
    long remainingMs(ServerPlayer player) {
        Long previous = last.get(player.getUUID());
        return previous == null ? 0 : Math.max(0, previous + intervalMs - System.currentTimeMillis());
    }

    boolean mark(ServerPlayer player) {
        last.put(player.getUUID(), System.currentTimeMillis());
        return true;
    }

    void forget(ServerPlayer player) {
        last.remove(player.getUUID());
    }
}
