package com.terracraft.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.WebMercator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;

/**
 * Ciel réel : l'heure du jour suit l'heure solaire locale et la météo suit Open-Meteo, à la
 * position d'un joueur de référence (le premier connecté par ordre alphabétique : un seul
 * ciel est partagé par tout le serveur).
 *
 * <p>L'horloge vanilla tourne à 1/72 de sa vitesse (une journée Minecraft = 24 h) et elle est
 * recalée régulièrement. La météo est réappliquée avant que le cycle vanilla ne la change.
 */
final class RealSky {
    /** 24 000 ticks par 86 400 s réelles, au lieu de 20 ticks par seconde. */
    private static final float REAL_TIME_RATE = 1f / 72f;
    private static final int SYNC_TICKS = 20 * 60;
    private static final long WEATHER_REFRESH_MS = 10 * 60 * 1000;
    /** Durée appliquée à chaque mise à jour météo (plus longue que l'intervalle de rafraîchissement). */
    private static final int WEATHER_DURATION_TICKS = 20 * 60 * 20;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private int ticks;
    private long lastWeatherFetch;
    private String lastWeatherCell = "";
    private volatile int weatherCode = -1;

    void tick(MinecraftServer server) {
        if (++ticks % SYNC_TICKS != 1) {
            return;
        }
        GeoChunkGenerator generator = StartPoints.generator(server);
        ServerPlayer reference = server.getPlayerList().getPlayers().stream()
                .min(Comparator.comparing(p -> p.getName().getString()))
                .orElse(null);
        if (generator == null || reference == null) {
            return;
        }
        double scale = generator.terrain().scale();
        double latitude = WebMercator.latitudeAt(reference.getZ(), scale);
        double longitude = WebMercator.longitudeAt(reference.getX(), scale);
        syncClock(server, longitude);
        syncWeather(server, latitude, longitude);
    }

    private static void syncClock(MinecraftServer server, double longitude) {
        Holder<WorldClock> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        ServerClockManager clocks = server.clockManager();
        // Heure solaire locale (sans fuseau ni heure d'été) : midi = soleil au plus haut.
        double utcHours = (Instant.now().getEpochSecond() % 86_400) / 3600.0;
        double solarHours = ((utcHours + longitude / 15.0) % 24 + 24) % 24;
        long dayTicks = (long) (((solarHours - 6 + 24) % 24) * 1000);
        long total = clocks.getInstance(clock).totalTicks();
        long target = Math.floorDiv(total, 24_000L) * 24_000L + dayTicks;
        if (Math.abs(target - total) > 20) {
            clocks.setTotalTicks(clock, target);
        }
        clocks.setRate(clock, REAL_TIME_RATE);
    }

    private void syncWeather(MinecraftServer server, double latitude, double longitude) {
        String cell = String.format(Locale.ROOT, "%.1f,%.1f", latitude, longitude);
        long now = System.currentTimeMillis();
        if (cell.equals(lastWeatherCell) && now - lastWeatherFetch < WEATHER_REFRESH_MS) {
            applyWeather(server);
            return;
        }
        lastWeatherCell = cell;
        lastWeatherFetch = now;
        String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=weather_code", latitude, longitude);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", GeoMod.USER_AGENT)
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            if (error != null || response.statusCode() != 200) {
                GeoMod.LOGGER.warn("Météo Open-Meteo indisponible ({})", error != null ? error.toString() : response.statusCode());
                return;
            }
            try {
                JsonObject current = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("current");
                weatherCode = current.get("weather_code").getAsInt();
                server.execute(() -> applyWeather(server));
            } catch (RuntimeException e) {
                GeoMod.LOGGER.warn("Réponse Open-Meteo illisible", e);
            }
        });
    }

    /** Codes météo WMO : 51-67 pluie, 71-77 neige, 80-86 averses, 95-99 orage. */
    private void applyWeather(MinecraftServer server) {
        int code = weatherCode;
        if (code < 0) {
            return;
        }
        boolean thunder = code >= 95;
        boolean rain = thunder || (code >= 51 && code <= 67) || (code >= 71 && code <= 77) || (code >= 80 && code <= 86);
        if (rain) {
            server.setWeatherParameters(0, WEATHER_DURATION_TICKS, true, thunder);
        } else {
            server.setWeatherParameters(WEATHER_DURATION_TICKS, 0, false, false);
        }
    }
}
