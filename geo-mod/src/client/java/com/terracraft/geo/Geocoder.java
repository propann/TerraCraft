package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Recherche de lieux via Nominatim (OpenStreetMap). Politique d'usage : une requête par
 * seconde au plus, User-Agent identifiable, uniquement sur action explicite du joueur.
 */
final class Geocoder {
    private static final String URL = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=fr&q=";
    private static final long MIN_INTERVAL_MS = 1100;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static long lastRequest;

    record Place(String name, String detail, double latitude, double longitude,
                 double south, double north, double west, double east) {
    }

    private Geocoder() {
    }

    static synchronized CompletableFuture<List<Place>> search(String query) {
        long wait = Math.max(0, lastRequest + MIN_INTERVAL_MS - System.currentTimeMillis());
        lastRequest = System.currentTimeMillis() + wait;
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", GeoMod.USER_AGENT)
                .build();
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(wait, java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenCompose(ignored -> HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return parse(response.body());
                });
    }

    private static List<Place> parse(String body) {
        List<Place> places = new ArrayList<>();
        JsonArray results = JsonParser.parseString(body).getAsJsonArray();
        for (JsonElement element : results) {
            JsonObject result = element.getAsJsonObject();
            String display = result.get("display_name").getAsString();
            String name = result.has("name") && !result.get("name").getAsString().isBlank()
                    ? result.get("name").getAsString()
                    : display.split(",", 2)[0];
            String detail = display.contains(",") ? display.substring(display.indexOf(',') + 1).strip() : "";
            JsonArray box = result.getAsJsonArray("boundingbox");
            places.add(new Place(name, detail,
                    result.get("lat").getAsDouble(), result.get("lon").getAsDouble(),
                    box.get(0).getAsDouble(), box.get(1).getAsDouble(),
                    box.get(2).getAsDouble(), box.get(3).getAsDouble()));
        }
        return places;
    }

    static String describe(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause instanceof IOException ? "connexion impossible" : cause.getMessage();
    }
}
