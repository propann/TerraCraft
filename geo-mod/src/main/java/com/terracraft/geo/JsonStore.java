package com.terracraft.geo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

/**
 * Fichiers JSON des données joueurs (économie, missions, progression, maisons…).
 *
 * <p>Écriture atomique : on écrit un fichier temporaire puis on le renomme, et la version
 * précédente est gardée en {@code .bak}. Un arrêt brutal pendant l'écriture ne peut donc plus
 * vider l'économie. À la lecture, un fichier illisible est mis de côté ({@code .corrompu-…})
 * et on repart de la sauvegarde {@code .bak}.
 */
final class JsonStore {
    private JsonStore() {
    }

    /** Données lues par {@code parser}, depuis le fichier ou sa sauvegarde ; null si aucun n'est lisible. */
    static <T> T load(Path file, Function<String, T> parser) {
        if (Files.isRegularFile(file)) {
            try {
                T value = parser.apply(Files.readString(file));
                if (value != null) {
                    return value;
                }
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.error("Fichier illisible {} : tentative avec la sauvegarde", file, e);
                try {
                    Files.copy(file, file.resolveSibling(file.getFileName() + ".corrompu-" + System.currentTimeMillis()),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // La copie de diagnostic est facultative.
                }
            }
        }
        Path backup = backup(file);
        if (Files.isRegularFile(backup)) {
            try {
                T value = parser.apply(Files.readString(backup));
                GeoMod.LOGGER.warn("Données restaurées depuis {}", backup);
                return value;
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.error("Sauvegarde illisible {}", backup, e);
            }
        }
        return null;
    }

    static void write(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        if (Files.isRegularFile(file)) {
            Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }
}
