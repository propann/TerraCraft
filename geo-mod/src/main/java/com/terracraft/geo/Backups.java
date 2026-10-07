package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Sauvegardes automatiques du monde (aucun mod de sauvegarde n'existe pour la 26.3).
 *
 * <p>Même méthode que {@code save-all} + {@code save-off} : tout est écrit sur le disque, l'écriture
 * des chunks est suspendue, le dossier du monde est compressé en tâche de fond (le jeu continue),
 * puis l'écriture reprend. Les plus anciennes archives au-delà de {@code keep} sont supprimées.
 * Réglages : {@code config/terracraft-backup.json}. Restauration : voir deploy/FALIX.md.
 */
final class Backups {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Réglages modifiables sans recompiler. */
    private static final class Config {
        boolean enabled = true;
        /** Délai entre deux sauvegardes automatiques. */
        int intervalMinutes = 360;
        /** Première sauvegarde automatique après le démarrage. */
        int firstDelayMinutes = 30;
        /** Nombre d'archives conservées. */
        int keep = 3;
        /** Dossier des archives, relatif au dossier du serveur. */
        String directory = "backups";
    }

    private Config config = new Config();
    private long nextTick;
    private volatile boolean running;

    void load(MinecraftServer server) {
        Path file = server.getServerDirectory().resolve("config").resolve("terracraft-backup.json");
        try {
            if (Files.isRegularFile(file)) {
                Config read = GSON.fromJson(Files.readString(file), Config.class);
                if (read != null) {
                    config = read;
                }
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(config));
            }
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.error("Réglages de sauvegarde illisibles ({}) : valeurs par défaut", file, e);
            config = new Config();
        }
        config.keep = Math.max(1, config.keep);
        config.intervalMinutes = Math.max(15, config.intervalMinutes);
        nextTick = server.getTickCount() + config.firstDelayMinutes * 1200L;
        GeoMod.LOGGER.info("[SAUVEGARDE] {} : toutes les {} min, {} archives dans {}/",
                config.enabled ? "activée" : "désactivée", config.intervalMinutes, config.keep, config.directory);
    }

    void tick(MinecraftServer server) {
        if (config.enabled && !running && server.getTickCount() >= nextTick) {
            nextTick = server.getTickCount() + config.intervalMinutes * 1200L;
            start(server, null);
        }
    }

    LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("sauvegarde")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> start(c.getSource().getServer(), c.getSource()) ? 1 : 0)
                .then(Commands.literal("liste").executes(c -> list(c.getSource())));
    }

    private boolean start(MinecraftServer server, CommandSourceStack source) {
        if (running) {
            if (source != null) {
                source.sendFailure(Component.literal("Une sauvegarde est déjà en cours."));
            }
            return false;
        }
        running = true;
        long started = System.currentTimeMillis();
        notifyOps(server, Component.literal("[Sauvegarde] Sauvegarde du monde en cours…").withStyle(ChatFormatting.GRAY));
        server.saveEverything(true, true, true);
        // Vrai si c'est nous qui coupons l'écriture : un « save-off » manuel reste respecté.
        boolean resumeAutosave = server.setAutoSave(false);

        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path directory = server.getServerDirectory().resolve(config.directory).toAbsolutePath().normalize();
        String name = server.getWorldData().getLevelName().replaceAll("[^A-Za-z0-9_-]", "_") + "_"
                + LocalDateTime.now().format(STAMP) + ".zip";
        int keep = config.keep;
        CompletableFuture.supplyAsync(() -> archive(world, directory, name, keep), runnable -> {
            Thread thread = new Thread(runnable, "TerraCraft-sauvegarde");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
        }).whenComplete((size, error) -> server.execute(() -> {
            if (resumeAutosave) {
                server.setAutoSave(true);
            }
            running = false;
            long seconds = (System.currentTimeMillis() - started) / 1000;
            if (error != null) {
                GeoMod.LOGGER.error("[SAUVEGARDE] Échec", error);
                notifyOps(server, Component.literal("[Sauvegarde] ÉCHEC : " + rootMessage(error) + " (voir la console)")
                        .withStyle(ChatFormatting.RED));
                if (source != null) {
                    source.sendFailure(Component.literal("Sauvegarde échouée : " + rootMessage(error)));
                }
                return;
            }
            String done = String.format(Locale.ROOT, "%s (%.1f Mo, %d s)", name, size / 1_048_576.0, seconds);
            GeoMod.LOGGER.info("[SAUVEGARDE] Terminée : {}/{}", directory, done);
            notifyOps(server, Component.literal("[Sauvegarde] Terminée : " + done).withStyle(ChatFormatting.GREEN));
        }));
        return true;
    }

    /** Compresse le monde dans une archive temporaire puis la renomme ; renvoie sa taille. */
    private static long archive(Path world, Path directory, String name, int keep) {
        try {
            Files.createDirectories(directory);
            if (directory.startsWith(world)) {
                throw new IOException("Le dossier des sauvegardes ne doit pas être dans le monde : " + directory);
            }
            long worldSize;
            try (Stream<Path> files = Files.walk(world)) {
                worldSize = files.filter(Files::isRegularFile).mapToLong(Backups::sizeOf).sum();
            }
            long free = Files.getFileStore(directory).getUsableSpace();
            if (free < worldSize) {
                throw new IOException(String.format(Locale.ROOT, "espace disque insuffisant (%.0f Mo libres, monde de %.0f Mo)",
                        free / 1_048_576.0, worldSize / 1_048_576.0));
            }
            Path tmp = directory.resolve(name + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out);
                 Stream<Path> files = Files.walk(world)) {
                zip.setLevel(Deflater.BEST_SPEED);
                Path root = world.getParent() == null ? world : world.getParent();
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String fileName = file.getFileName().toString();
                    if (fileName.equals("session.lock") || fileName.endsWith(".tmp")) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
                    try {
                        Files.copy(file, zip);
                    } catch (IOException e) {
                        // Fichier disparu entre-temps (cache, verrou) : on continue sans lui.
                        GeoMod.LOGGER.warn("[SAUVEGARDE] Fichier ignoré {} : {}", file, e.getMessage());
                    }
                    zip.closeEntry();
                }
            }
            Path archive = directory.resolve(name);
            Files.move(tmp, archive, StandardCopyOption.REPLACE_EXISTING);
            prune(directory, keep);
            return Files.size(archive);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Garde les {@code keep} archives les plus récentes (noms horodatés, donc triés par date). */
    private static void prune(Path directory, int keep) throws IOException {
        List<Path> archives;
        try (Stream<Path> files = Files.list(directory)) {
            archives = files.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList();
        }
        for (int i = 0; i < archives.size() - keep; i++) {
            Files.deleteIfExists(archives.get(i));
            GeoMod.LOGGER.info("[SAUVEGARDE] Ancienne archive supprimée : {}", archives.get(i).getFileName());
        }
    }

    private int list(CommandSourceStack source) {
        Path directory = source.getServer().getServerDirectory().resolve(config.directory);
        try (Stream<Path> files = Files.exists(directory) ? Files.list(directory) : Stream.empty()) {
            List<Path> archives = files.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList();
            if (archives.isEmpty()) {
                source.sendSuccess(() -> Component.literal("Aucune sauvegarde dans " + config.directory + "/."), false);
                return 0;
            }
            for (Path archive : archives) {
                String line = String.format(Locale.ROOT, "%s — %.1f Mo", archive.getFileName(), sizeOf(archive) / 1_048_576.0);
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return archives.size();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Impossible de lister " + directory + " : " + e.getMessage()));
            return 0;
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void notifyOps(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(player.nameAndId())) {
                player.sendSystemMessage(message);
            }
        }
    }
}
