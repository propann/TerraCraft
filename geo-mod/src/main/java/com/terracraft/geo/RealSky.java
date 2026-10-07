package com.terracraft.geo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.WebMercator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;

/**
 * Ciel réel : la météo suit Open-Meteo à la position d'un joueur de référence (le premier connecté par ordre
 * alphabétique : un seul ciel est partagé par tout le serveur). La météo est réappliquée avant que le cycle vanilla
 * ne la change.
 *
 * <p>Le jour et la nuit suivent le cycle normal de Minecraft (20 minutes). Jusqu'à la 0.18, l'horloge suivait l'heure
 * solaire réelle à 1/72 de sa vitesse : des nuits de 12 heures. La vitesse ralentie étant enregistrée dans le monde,
 * elle est remise à la normale à chaque passage.
 */
final class RealSky {
    private static final float NORMAL_RATE = 1f;
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
        normalClock(server);
        syncWeather(server, latitude, longitude);
    }

    /** Cycle normal : annule l'ancienne horloge à l'heure solaire réelle, ralentie 72 fois. */
    private static void normalClock(MinecraftServer server) {
        Holder<WorldClock> clock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
        server.clockManager().setRate(clock, NORMAL_RATE);
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
