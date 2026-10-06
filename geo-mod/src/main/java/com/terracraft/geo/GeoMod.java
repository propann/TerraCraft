package com.terracraft.geo;

import com.terracraft.geo.world.EarthTerrain;
import com.terracraft.geo.world.GeoBiomeSource;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.WebMercator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class GeoMod implements ModInitializer {
    public static final String MOD_ID = "terracraft_geo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Identifiant HTTP exigé par les politiques d'usage d'OSM et de Nominatim. */
    public static final String USER_AGENT = "TerraCraftGeo/0.2 (prototype de serveur Minecraft local)";

    private static final StartPoints START_POINTS = new StartPoints();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "earth"), GeoChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(MOD_ID, "earth"), GeoBiomeSource.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(StartPointPayload.TYPE, StartPointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMapPayload.TYPE, OpenMapPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartPointPayload.TYPE, (payload, context) ->
                START_POINTS.onChoice(context.player(), payload));

        ServerLifecycleEvents.SERVER_STARTED.register(START_POINTS::load);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> START_POINTS.onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> START_POINTS.onLeave(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
                Commands.literal("terracraft")
                        .then(Commands.literal("ou").executes(command -> {
                            ServerPlayer player = command.getSource().getPlayerOrException();
                            GeoChunkGenerator generator = StartPoints.generator(command.getSource().getServer());
                            if (generator == null) {
                                command.getSource().sendFailure(Component.literal("Ce monde n'est pas un monde TerraCraft."));
                                return 0;
                            }
                            EarthTerrain terrain = generator.terrain();
                            double latitude = WebMercator.latitudeAt(player.getZ(), terrain.scale());
                            double longitude = WebMercator.longitudeAt(player.getX(), terrain.scale());
                            command.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                    "Tu es à %.5f, %.5f — altitude réelle ≈ %.0f m",
                                    latitude, longitude, terrain.elevation(player.getX(), player.getZ()))), false);
                            return 1;
                        }))
                        .then(Commands.literal("depart")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(command -> {
                                    START_POINTS.openMap(command.getSource().getPlayerOrException(), false);
                                    return 1;
                                }))));

        LOGGER.info("TerraCraft Geo chargé : générateur terracraft_geo:earth et carte du monde prêts.");
    }
}
