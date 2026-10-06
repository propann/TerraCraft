package com.terracraft.geo;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tuiles raster OpenStreetMap affichées dans l'écran de carte.
 *
 * <p>Respect de la politique d'usage d'OSM : User-Agent identifiable, deux connexions au
 * maximum, cache disque de 30 jours, aucune pré-récupération massive, attribution affichée.
 * Toutes les méthodes publiques s'appellent depuis le thread de rendu.
 */
final class MapTileCache {
    static final String URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
    static final int MAX_ZOOM = 19;
    private static final Duration DISK_TTL = Duration.ofDays(30);
    private static final int MAX_TEXTURES = 384;
    /** Une tuile plus demandée depuis ce délai n'est plus téléchargée (l'utilisateur a bougé). */
    private static final long STALE_REQUEST_MS = 1500;

    private enum Status { LOADING, READY, FAILED }

    private static final class Entry {
        volatile Status status = Status.LOADING;
        volatile long lastRequested;
        volatile long failedAt;
        Identifier texture;
    }

    private static MapTileCache instance;

    private final Minecraft minecraft;
    private final Path cacheDir;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService downloads = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "TerraCraft-carte");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, Entry> entries = new HashMap<>();

    private MapTileCache(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.cacheDir = minecraft.gameDirectory.toPath().resolve("terracraft-cache").resolve("osm");
    }

    static MapTileCache get() {
        if (instance == null) {
            instance = new MapTileCache(Minecraft.getInstance());
        }
        return instance;
    }

    /** Texture prête pour la tuile, ou {@code null} (téléchargement lancé si besoin). */
    Identifier request(int z, int x, int y) {
        Entry entry = entries.get(key(z, x, y));
        long now = System.currentTimeMillis();
        if (entry == null) {
            entry = new Entry();
            entries.put(key(z, x, y), entry);
            Entry created = entry;
            created.lastRequested = now;
            downloads.execute(() -> load(z, x, y, created));
            evictIfNeeded();
            return null;
        }
        entry.lastRequested = now;
        if (entry.status == Status.FAILED && now - entry.failedAt > 10_000) {
            // Nouvel essai au prochain affichage.
            entries.remove(key(z, x, y));
        }
        return entry.status == Status.READY ? entry.texture : null;
    }

    /** Texture déjà chargée, sans déclencher de téléchargement (utilisée pour les aperçus flous). */
    Identifier peek(int z, int x, int y) {
        Entry entry = entries.get(key(z, x, y));
        if (entry == null || entry.status != Status.READY) {
            return null;
        }
        entry.lastRequested = System.currentTimeMillis();
        return entry.texture;
    }

    private void load(int z, int x, int y, Entry entry) {
        if (System.currentTimeMillis() - entry.lastRequested > STALE_REQUEST_MS) {
            // Plus visible : on oublie la tuile pour pouvoir la redemander plus tard.
            minecraft.execute(() -> entries.remove(key(z, x, y), entry));
            return;
        }
        try {
            byte[] png = readOrDownload(z, x, y);
            NativeImage image = NativeImage.read(png);
            minecraft.execute(() -> {
                Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "osm/" + z + "/" + x + "/" + y);
                minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, image));
                entry.texture = id;
                entry.status = Status.READY;
            });
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.warn("Tuile carte {}/{}/{} indisponible : {}", z, x, y, e.toString());
            entry.failedAt = System.currentTimeMillis();
            entry.status = Status.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private byte[] readOrDownload(int z, int x, int y) throws IOException, InterruptedException {
        Path file = cacheDir.resolve(z + "/" + x + "/" + y + ".png");
        if (Files.isRegularFile(file)
                && Files.getLastModifiedTime(file).toInstant().isAfter(Instant.now().minus(DISK_TTL))) {
            return Files.readAllBytes(file);
        }
        String url = URL_TEMPLATE.replace("{z}", Integer.toString(z))
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", GeoMod.USER_AGENT)
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(y + ".png.part");
        Files.write(tmp, response.body());
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return response.body();
    }

    private void evictIfNeeded() {
        if (entries.size() <= MAX_TEXTURES) {
            return;
        }
        List<Map.Entry<Long, Entry>> oldest = entries.entrySet().stream()
                .filter(e -> e.getValue().status != Status.LOADING)
                .sorted(Comparator.comparingLong(e -> e.getValue().lastRequested))
                .limit(entries.size() - MAX_TEXTURES + 32)
                .toList();
        for (Map.Entry<Long, Entry> e : oldest) {
            entries.remove(e.getKey());
            if (e.getValue().texture != null) {
                minecraft.getTextureManager().release(e.getValue().texture);
            }
        }
    }

    private static long key(int z, int x, int y) {
        return ((long) z << 58) | ((long) x << 29) | y;
    }
}
