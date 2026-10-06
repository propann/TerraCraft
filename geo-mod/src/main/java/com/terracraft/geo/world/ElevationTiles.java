package com.terracraft.geo.world;

import com.terracraft.geo.GeoMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tuiles d'altitude Terrarium (AWS Open Data « Terrain Tiles », dérivées de SRTM, GMTED, ETOPO1…).
 *
 * <p>Chaque tuile PNG 256×256 encode l'altitude en mètres : {@code R*256 + G + B/256 - 32768}.
 * Les valeurs négatives sont la bathymétrie. Les tuiles sont mises en cache sur disque
 * puis en mémoire (LRU) ; plusieurs threads de génération qui demandent la même tuile
 * partagent un seul téléchargement.
 */
public final class ElevationTiles {
    public static final String DEFAULT_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";
    public static final String ATTRIBUTION = "Relief : AWS Terrain Tiles (SRTM, GMTED2010, ETOPO1)";
    private static final int TILE_SIZE = 256;
    private static final int MEMORY_TILES = 96;
    private static final int ATTEMPTS = 3;

    private final int zoom;
    private final String urlTemplate;
    private final Path cacheDir;
    private final HttpClient http;
    private final ExecutorService downloads = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "TerraCraft-relief");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Long, CompletableFuture<float[]>> inFlight = new ConcurrentHashMap<>();
    private final Map<Long, float[]> memory = new LinkedHashMap<>(MEMORY_TILES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
            return size() > MEMORY_TILES;
        }
    };

    public ElevationTiles(int zoom, String urlTemplate, Path cacheDir) {
        this.zoom = zoom;
        this.urlTemplate = urlTemplate;
        this.cacheDir = cacheDir;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public int zoom() {
        return zoom;
    }

    /** Nombre de pixels sur toute la largeur du monde à ce niveau de zoom. */
    public int worldPixels() {
        return TILE_SIZE << zoom;
    }

    /**
     * Altitude en mètres au pixel global (gx, gy). Bloquant si la tuile n'est pas encore
     * en cache. Renvoie {@code NaN} si la tuile est définitivement indisponible.
     */
    public float pixel(int gx, int gy) {
        int tiles = 1 << zoom;
        int tx = Math.floorMod(gx >> 8, tiles);
        int ty = Math.max(0, Math.min(tiles - 1, gy >> 8));
        int lx = gx & 0xFF;
        int ly = gy < 0 ? 0 : gy >= tiles * TILE_SIZE ? TILE_SIZE - 1 : gy & 0xFF;
        float[] tile = tile(tx, ty);
        return tile == null ? Float.NaN : tile[ly * TILE_SIZE + lx];
    }

    /** Télécharge en arrière-plan toutes les tuiles couvrant le rectangle de pixels globaux. */
    public CompletableFuture<Void> prefetch(int minGx, int minGy, int maxGx, int maxGy) {
        int tiles = 1 << zoom;
        List<CompletableFuture<float[]>> futures = new ArrayList<>();
        for (int ty = Math.max(0, minGy >> 8); ty <= Math.min(tiles - 1, maxGy >> 8); ty++) {
            for (int tx = minGx >> 8; tx <= maxGx >> 8; tx++) {
                futures.add(load(Math.floorMod(tx, tiles), ty));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private float[] tile(int tx, int ty) {
        long key = key(tx, ty);
        synchronized (memory) {
            float[] cached = memory.get(key);
            if (cached != null) {
                return cached;
            }
        }
        float[] data = load(tx, ty).join();
        return data.length == 0 ? null : data;
    }

    private CompletableFuture<float[]> load(int tx, int ty) {
        long key = key(tx, ty);
        synchronized (memory) {
            float[] cached = memory.get(key);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        CompletableFuture<float[]> future = inFlight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
            float[] data = fetch(tx, ty);
            synchronized (memory) {
                memory.put(k, data);
            }
            return data;
        }, downloads));
        future.whenComplete((data, error) -> inFlight.remove(key, future));
        return future;
    }

    private float[] fetch(int tx, int ty) {
        Path file = cacheDir.resolve(zoom + "/" + tx + "/" + ty + ".png");
        try {
            if (Files.isRegularFile(file)) {
                return decode(Files.readAllBytes(file));
            }
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.warn("Cache relief illisible {}, nouveau téléchargement", file, e);
        }
        String url = urlTemplate.replace("{z}", Integer.toString(zoom))
                .replace("{x}", Integer.toString(tx))
                .replace("{y}", Integer.toString(ty));
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", GeoMod.USER_AGENT)
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                float[] data = decode(response.body());
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(ty + ".png.part");
                Files.write(tmp, response.body());
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return data;
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.warn("Tuile relief {}/{}/{} : essai {}/{} échoué ({})", zoom, tx, ty, attempt, ATTEMPTS, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        GeoMod.LOGGER.error("Tuile relief {}/{}/{} indisponible : la zone sera générée à plat.", zoom, tx, ty);
        return new float[0];
    }

    private static float[] decode(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null || image.getWidth() != TILE_SIZE || image.getHeight() != TILE_SIZE) {
            throw new IOException("image Terrarium invalide");
        }
        int[] rgb = image.getRGB(0, 0, TILE_SIZE, TILE_SIZE, null, 0, TILE_SIZE);
        float[] metres = new float[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >> 16) & 0xFF;
            int g = (rgb[i] >> 8) & 0xFF;
            int b = rgb[i] & 0xFF;
            metres[i] = r * 256f + g + b / 256f - 32768f;
        }
        return metres;
    }

    private static long key(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
    }
}
