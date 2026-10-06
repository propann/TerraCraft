package com.terracraft.geo;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.ModMobs;
import com.terracraft.geo.content.Vehicle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import com.terracraft.geo.world.EarthTerrain;
import com.terracraft.geo.world.GeoBiomeSource;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.MoonChunkGenerator;
import com.terracraft.geo.world.WebMercator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.dedicated.DedicatedServer;
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
    public static final String USER_AGENT = "TerraCraftGeo/0.2 (+https://github.com/propann/TerraCraft)";

    private static final StartPoints START_POINTS = new StartPoints();
    private static final RealSky REAL_SKY = new RealSky();
    private static final Survival SURVIVAL = new Survival();

    /** Véhicule complet, plein d'essence, posé devant le joueur (tests et administration). */
    private static int spawnVehicle(CommandSourceStack source, boolean truck) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vehicle vehicle = (truck ? ModContent.TRUCK : ModContent.CAR).create(player.level(), EntitySpawnReason.COMMAND);
        if (vehicle == null) {
            return 0;
        }
        Vec3 at = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        vehicle.snapTo(at.x, player.getY(), at.z, player.getYRot(), 0);
        vehicle.setParts(Vehicle.ENGINE | Vehicle.RADIATOR | Vehicle.BATTERY | Vehicle.TURBO, 4, Vehicle.MAX_FUEL);
        player.level().addFreshEntity(vehicle);
        source.sendSuccess(() -> Component.literal((truck ? "Camion" : "Voiture") + " prêt(e) : clic droit pour monter."), false);
        return 1;
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        ModBlocks.init();
        ModMobs.init();
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "earth"), GeoChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(MOD_ID, "earth"), GeoBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "moon"), MoonChunkGenerator.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(StartPointPayload.TYPE, StartPointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMapPayload.TYPE, OpenMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SheetPayload.TYPE, SheetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSheetPayload.TYPE, RequestSheetPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RequestSheetPayload.TYPE, (payload, context) ->
                Progression.get().sendSheet(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(StartPointPayload.TYPE, (payload, context) ->
                START_POINTS.onChoice(context.player(), payload));

        ServerLifecycleEvents.SERVER_STARTED.register(START_POINTS::load);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Gravité lunaire, fusée, chute d'arrivée : sans vol autorisé, le serveur expulserait les joueurs.
            if (server instanceof DedicatedServer dedicated && !dedicated.allowFlight()) {
                dedicated.setAllowFlight(true);
                LOGGER.info("TerraCraft : allow-flight activé (nécessaire pour la Lune, l'orbite et les fusées).");
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Progression.get()::load);
        ServerLifecycleEvents.SERVER_STARTED.register(SURVIVAL::load);
        ServerPlayerEvents.COPY_FROM.register(SURVIVAL::onRespawn);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Progression.get().save());
        ServerTickEvents.END_SERVER_TICK.register(Progression.get()::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Progression.get().applyPerks(newPlayer));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer dead) {
                SURVIVAL.onDeath(dead);
            }
            if (source.getEntity() instanceof ServerPlayer killer) {
                Progression.get().onKill(entity, killer);
            }
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (state.is(ModBlocks.TITANIUM_ORE)) {
                    Progression.get().count(serverPlayer, "titanium", 1, 2);
                } else if (state.is(ModBlocks.HELIUM3_CRYSTALS)) {
                    Progression.get().count(serverPlayer, "helium", 1, 2);
                }
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            // Coffre jamais ouvert (table de butin encore présente) : compte comme fouillé.
            if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(hit.getBlockPos()) instanceof RandomizableContainer container
                    && container.getLootTable() != null) {
                Progression.get().count(serverPlayer, "loot", 1, 2);
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(REAL_SKY::tick);
        ServerTickEvents.END_SERVER_TICK.register(Space::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            START_POINTS.onJoin(handler.player);
            Progression.get().applyPerks(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> START_POINTS.onLeave(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SURVIVAL.register(dispatcher));
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
                        .then(Commands.literal("vehicule")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.literal("voiture").executes(command -> spawnVehicle(command.getSource(), false)))
                                .then(Commands.literal("camion").executes(command -> spawnVehicle(command.getSource(), true))))
                        .then(Commands.literal("depart")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(command -> {
                                    START_POINTS.openMap(command.getSource().getPlayerOrException(), false);
                                    return 1;
                                }))));

        LOGGER.info("TerraCraft Geo chargé : générateur terracraft_geo:earth et carte du monde prêts.");
    }
}
