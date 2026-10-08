package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Signalements des joueurs (/signaler <message>) : bug, perte d'objet, triche. Enregistrés avec
 * la position et la version dans signalements.json, journalisés et transmis aux opérateurs.
 */
final class Reports {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private record Report(String date, String player, String uuid, String dimension, long x, long y, long z, String version,
                          String message) {
    }

    private final List<Report> reports = new ArrayList<>();
    private final RateLimit rate = new RateLimit(60_000);
    private Path file;
    private MinecraftServer server;

    void load(MinecraftServer server) {
        this.server = server;
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("signalements.json");
        reports.clear();
        List<Report> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<List<Report>>() { }.getType()));
        if (stored != null) {
            reports.addAll(stored);
        }
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("signaler")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> report(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")))));
    }

    private int report(ServerPlayer player, String message) {
        long wait = rate.remainingMs(player);
        if (wait > 0) {
            player.sendSystemMessage(Component.literal("Un signalement par minute : réessaie dans " + (wait + 999) / 1000 + " s.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        rate.mark(player);
        String text = message.length() > 500 ? message.substring(0, 500) : message;
        Report report = new Report(LocalDateTime.now().format(DATE), player.getName().getString(), player.getUUID().toString(),
                player.level().dimension().identifier().toString(), player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                GeoMod.version(), text);
        reports.add(report);
        try {
            JsonStore.write(file, GSON.toJson(reports));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
        GeoMod.LOGGER.warn("[SIGNALEMENT] {} en {} {} {} {} : {}", report.player(), report.dimension(), report.x(), report.y(),
                report.z(), text);
        player.sendSystemMessage(Component.literal("Merci ! Ton signalement n° " + reports.size()
                + " est enregistré avec ta position. Un administrateur va le regarder.").withStyle(ChatFormatting.GREEN));
        Component alert = Component.literal(String.format(Locale.ROOT, "[Signalement n° %d] %s (%d %d %d) : %s", reports.size(),
                report.player(), report.x(), report.y(), report.z(), text)).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online != player && server.getPlayerList().isOp(online.nameAndId())) {
                online.sendSystemMessage(alert);
            }
        }
        return 1;
    }

    /** Les {@code count} derniers signalements, du plus récent au plus ancien (modération). */
    List<String> recent(int count) {
        List<String> lines = new ArrayList<>();
        for (int i = reports.size() - 1; i >= 0 && lines.size() < count; i--) {
            Report r = reports.get(i);
            lines.add(r.date() + " · " + r.player() + " · " + r.dimension() + " " + r.x() + " " + r.y() + " " + r.z() + " · " + r.message());
        }
        return lines;
    }

    void onLeave(ServerPlayer player) {
        rate.forget(player);
    }
}
