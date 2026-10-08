package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Suivi de la bêta (activite.json) : première et dernière venue, nombre de sessions et temps de jeu par joueur.
 * /terracraft suivi en tire les chiffres utiles : joueurs uniques, actifs sur 24 h et 7 jours, retour après la
 * première journée, temps de jeu, et l'état du serveur (joueurs en ligne, durée moyenne d'un tick).
 */
final class Activity {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DAY = 24 * 3600_000L;

    static final class Record {
        String name;
        long first;
        long last;
        int sessions;
        long playedMs;
    }

    private final Map<String, Record> records = new LinkedHashMap<>();
    private final Map<String, Long> sessionStart = new HashMap<>();
    private Path file;

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("activite.json");
        records.clear();
        Map<String, Record> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Map<String, Record>>() { }.getType()));
        if (stored != null) {
            records.putAll(stored);
        }
    }

    void save() {
        if (file == null) {
            return;
        }
        try {
            JsonStore.write(file, GSON.toJson(records));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    void onJoin(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Record record = records.computeIfAbsent(player.getUUID().toString(), k -> {
            Record r = new Record();
            r.first = now;
            return r;
        });
        record.name = player.getName().getString();
        record.last = now;
        record.sessions++;
        sessionStart.put(player.getUUID().toString(), now);
        save();
    }

    void onLeave(ServerPlayer player) {
        Long start = sessionStart.remove(player.getUUID().toString());
        Record record = records.get(player.getUUID().toString());
        if (start != null && record != null) {
            record.playedMs += System.currentTimeMillis() - start;
            record.last = System.currentTimeMillis();
            save();
        }
    }

    int report(CommandSourceStack source) {
        long now = System.currentTimeMillis();
        long active24 = records.values().stream().filter(r -> now - r.last < DAY).count();
        long active7 = records.values().stream().filter(r -> now - r.last < 7 * DAY).count();
        // Revenus : venus au moins une fois plus de 24 h après leur première venue (parmi ceux arrivés depuis plus d'un jour).
        long eligible = records.values().stream().filter(r -> now - r.first > DAY).count();
        long returned = records.values().stream().filter(r -> now - r.first > DAY && r.last - r.first > DAY).count();
        long playedHours = records.values().stream().mapToLong(r -> r.playedMs).sum() / 3_600_000L;
        MinecraftServer server = source.getServer();
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        source.sendSuccess(() -> Component.literal("━━ Suivi TerraCraft ━━").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Joueurs uniques : " + records.size() + " · actifs 24 h : " + active24
                + " · actifs 7 j : " + active7), false);
        source.sendSuccess(() -> Component.literal("Retour après le premier jour : " + returned + "/" + eligible
                + (eligible == 0 ? "" : String.format(Locale.ROOT, " (%.0f %%)", 100.0 * returned / eligible))
                + " · temps de jeu total : " + playedHours + " h"), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "En ligne : %d · tick moyen : %.1f ms (%s)",
                server.getPlayerCount(), mspt, mspt < 40 ? "bon" : mspt < 50 ? "limite" : "surchargé"))
                .withStyle(mspt < 40 ? ChatFormatting.GREEN : mspt < 50 ? ChatFormatting.YELLOW : ChatFormatting.RED), false);
        GeoMod.LOGGER.info("[SUIVI] {} joueurs uniques, {} actifs 24 h, {} actifs 7 j, retour {}/{}, {} h jouées, tick {} ms",
                records.size(), active24, active7, returned, eligible, playedHours, String.format(Locale.ROOT, "%.1f", mspt));
        return 1;
    }
}
