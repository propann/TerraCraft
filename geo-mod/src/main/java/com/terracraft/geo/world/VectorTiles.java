package com.terracraft.geo.world;

import com.google.gson.JsonParser;
import com.terracraft.geo.GeoMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tuiles vectorielles OpenFreeMap (données OpenStreetMap, schéma OpenMapTiles) au zoom 14.
 *
 * <p>Servies par un CDN, sans clé ni quota strict : bien plus fiables qu'Overpass pour générer
 * des chunks à la demande. Les tuiles brutes sont gardées sur disque ; les tuiles décodées
 * restent en mémoire (LRU) car une tuile couvre plusieurs cellules.
 */
final class VectorTiles {
    static final int ZOOM = 14;
    private static final String TILEJSON = "https://tiles.openfreemap.org/planet";
    private static final String FALLBACK_URL = "https://tiles.openfreemap.org/planet/20261004_113936_pt/{z}/{x}/{y}.pbf";
    private static final int MEMORY_TILES = 6;
    private static final int ATTEMPTS = 4;

    private final Path cacheDir;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<Long, List<MvtDecoder.Feature>> memory = new LinkedHashMap<>(MEMORY_TILES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<MvtDecoder.Feature>> eldest) {
            return size() > MEMORY_TILES;
        }
    };
    /** Un verrou par tuile : deux cellules voisines partagent souvent la même tuile. */
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    private volatile String urlTemplate;

    VectorTiles(Path cacheDir) {
        this.cacheDir = cacheDir.resolve("openfreemap");
    }

    /** Éléments de la tuile (vide si indisponible). Bloquant ; appelé hors du thread serveur. */
    List<MvtDecoder.Feature> tile(int x, int y) {
        long key = ((long) x << 32) | (y & 0xFFFFFFFFL);
        synchronized (memory) {
            List<MvtDecoder.Feature> cached = memory.get(key);
            if (cached != null) {
                return cached;
            }
        }
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            synchronized (memory) {
                List<MvtDecoder.Feature> cached = memory.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            byte[] data = read(x, y);
            List<MvtDecoder.Feature> features;
            try {
                features = data == null ? List.of() : MvtDecoder.decode(data);
            } catch (RuntimeException e) {
                GeoMod.LOGGER.error("Tuile vectorielle {}/{}/{} illisible", ZOOM, x, y, e);
                features = List.of();
            }
            synchronized (memory) {
                memory.put(key, features);
            }
            return features;
        }
    }

    private byte[] read(int x, int y) {
        Path file = cacheDir.resolve(ZOOM + "/" + x + "/" + y + ".pbf");
        try {
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        } catch (IOException e) {
            GeoMod.LOGGER.warn("Cache vectoriel illisible {}", file);
        }
        String url = template().replace("{z}", Integer.toString(ZOOM))
                .replace("{x}", Integer.toString(x)).replace("{y}", Integer.toString(y));
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", GeoMod.USER_AGENT)
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 204 || response.statusCode() == 404) {
                    return new byte[0]; // Tuile vide (pleine mer, désert…).
                }
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(y + ".pbf.part");
                Files.write(tmp, response.body());
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return response.body();
            } catch (IOException e) {
                GeoMod.LOGGER.warn("Tuile vectorielle {}/{}/{} : essai {}/{} ({})", ZOOM, x, y, attempt, ATTEMPTS, e.toString());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        GeoMod.LOGGER.error("Tuile vectorielle {}/{}/{} indisponible : zone sans routes ni bâtiments.", ZOOM, x, y);
        return null;
    }

    /** L'URL des tuiles contient la date du dernier rendu : on la lit dans le TileJSON. */
    private String template() {
        String template = urlTemplate;
        if (template != null) {
            return template;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(TILEJSON))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", GeoMod.USER_AGENT)
                    .build();
            String body = http.send(request, HttpResponse.BodyHandlers.ofString()).body();
            template = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("tiles").get(0).getAsString();
        } catch (IOException | RuntimeException e) {
            GeoMod.LOGGER.warn("TileJSON OpenFreeMap indisponible, URL de secours utilisée ({})", e.toString());
            template = FALLBACK_URL;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FALLBACK_URL;
        }
        urlTemplate = template;
        return template;
    }
}
