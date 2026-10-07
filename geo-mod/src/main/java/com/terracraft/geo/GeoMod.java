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
import com.terracraft.geo.world.MarsChunkGenerator;
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
import net.minecraft.commands.arguments.EntityArgument;
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
    private static final AuctionHouse AUCTION_HOUSE = new AuctionHouse();
    private static final Missions MISSIONS = new Missions();

    private static final RateLimit GUI_RATE = new RateLimit(150);
    private static final AntiFly ANTI_FLY = new AntiFly();

    private record PendingLoot(ServerPlayer player, net.minecraft.core.BlockPos pos) {
    }

    private static final java.util.List<PendingLoot> PENDING_LOOT = new java.util.ArrayList<>();

    /** Véhicule complet, plein d'essence, posé devant le joueur (tests et administration). */
    private static int spawnVehicle(CommandSourceStack source, Vehicle.Kind kind) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var type = switch (kind) {
            case TRUCK -> ModContent.TRUCK;
            case MOTORCYCLE -> ModContent.MOTORCYCLE;
            default -> ModContent.CAR;
        };
        Vehicle vehicle = type.create(player.level(), EntitySpawnReason.COMMAND);
        if (vehicle == null) {
            return 0;
        }
        Vec3 at = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        vehicle.snapTo(at.x, player.getY(), at.z, player.getYRot(), 0);
        vehicle.setParts(Vehicle.ENGINE | Vehicle.RADIATOR | Vehicle.BATTERY | Vehicle.TURBO,
                kind.requiredWheels(), Vehicle.MAX_FUEL);
        player.level().addFreshEntity(vehicle);
        source.sendSuccess(() -> Component.literal(switch (kind) {
            case TRUCK -> "Camion";
            case MOTORCYCLE -> "Moto";
            default -> "Voiture";
        } + " prêt(e) : clic droit pour monter."), false);
        return 1;
    }

    private static int shareVehicle(CommandSourceStack source, ServerPlayer target, boolean allowed) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        Vehicle vehicle = owner.level().getEntitiesOfClass(Vehicle.class, owner.getBoundingBox().inflate(6)).stream()
                .filter(candidate -> candidate.isOwnedBy(owner))
                .findFirst().orElse(null);
        if (vehicle == null) {
            source.sendFailure(Component.literal("Place-toi à moins de 6 blocs de ton véhicule."));
            return 0;
        }
        vehicle.setTrusted(target.getUUID(), allowed);
        source.sendSuccess(() -> Component.literal((allowed ? "Accès donné à " : "Accès retiré pour ") + target.getName().getString()), false);
        target.sendSystemMessage(Component.literal(allowed ? "Tu peux maintenant utiliser le véhicule partagé." : "L'accès à un véhicule partagé a été retiré."));
        return 1;
    }

    private static int releaseVehicle(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        Vehicle vehicle = owner.level().getEntitiesOfClass(Vehicle.class, owner.getBoundingBox().inflate(6)).stream()
                .filter(candidate -> candidate.isOwnedBy(owner))
                .findFirst().orElse(null);
        if (vehicle == null) {
            source.sendFailure(Component.literal("Place-toi à moins de 6 blocs de ton véhicule."));
            return 0;
        }
        vehicle.clearOwnership(owner);
        source.sendSuccess(() -> Component.literal("Véhicule libéré : un autre joueur peut maintenant l'enregistrer."), false);
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
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath(MOD_ID, "mars"), MarsChunkGenerator.CODEC);

        SpaceSuit.register();
        PayloadTypeRegistry.serverboundPlay().register(StartPointPayload.TYPE, StartPointPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenSuitPayload.TYPE, OpenSuitPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenMapPayload.TYPE, OpenMapPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SheetPayload.TYPE, SheetPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MissionPayload.TYPE, MissionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MarketPayload.TYPE, MarketPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TutorialPayload.TYPE, TutorialPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestSheetPayload.TYPE, RequestSheetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestMissionsPayload.TYPE, RequestMissionsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ClaimMissionPayload.TYPE, ClaimMissionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestMarketPayload.TYPE, RequestMarketPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuyListingPayload.TYPE, BuyListingPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveListingPayload.TYPE, RemoveListingPayload.CODEC);
        // Les écrans envoient ces paquets sur un clic : au-delà de quelques par seconde, c'est un
        // client modifié qui inonde le serveur (chaque achat écrit sur le disque).
        ServerPlayNetworking.registerGlobalReceiver(RequestSheetPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                Progression.get().sendSheet(context.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(OpenSuitPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player()) && !context.player().isSpectator()) {
                SpaceSuit.open(context.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestMissionsPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                MISSIONS.send(context.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ClaimMissionPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                MISSIONS.claimFromClient(context.player(), payload.id(), AUCTION_HOUSE);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(RequestMarketPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                AUCTION_HOUSE.sendMarket(context.player(), payload.page());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(BuyListingPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                AUCTION_HOUSE.buyFromClient(context.player(), payload.id());
                AUCTION_HOUSE.sendMarket(context.player());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(RemoveListingPayload.TYPE, (payload, context) -> {
            if (GUI_RATE.allow(context.player())) {
                AUCTION_HOUSE.removeFromClient(context.player(), payload.id());
                AUCTION_HOUSE.sendMarket(context.player());
            }
        });
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
        ServerLifecycleEvents.SERVER_STARTED.register(AUCTION_HOUSE::load);
        ServerLifecycleEvents.SERVER_STARTED.register(MISSIONS::load);
        Tutorial.get().wire(SURVIVAL, MISSIONS, AUCTION_HOUSE, START_POINTS);
        ServerLifecycleEvents.SERVER_STARTED.register(Tutorial.get()::load);
        ServerTickEvents.END_SERVER_TICK.register(Tutorial.get()::tick);
        ServerPlayerEvents.COPY_FROM.register(SURVIVAL::onRespawn);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Progression.get().save());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> AUCTION_HOUSE.save());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> MISSIONS.save());
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
            // Coffre jamais ouvert (table de butin encore présente) : on vérifie à la fin du tick
            // que le butin a réellement été généré. Un clic refusé (claim, spectateur, accroupi
            // avec un bloc) ne compte donc pas, et un même coffre ne compte qu'une fois.
            if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(hit.getBlockPos()) instanceof RandomizableContainer container
                    && container.getLootTable() != null) {
                PENDING_LOOT.add(new PendingLoot(serverPlayer, hit.getBlockPos().immutable()));
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (PendingLoot pending : PENDING_LOOT) {
                if (!pending.player().isRemoved()
                        && pending.player().level().getBlockEntity(pending.pos()) instanceof RandomizableContainer container
                        && container.getLootTable() == null) {
                    Progression.get().count(pending.player(), "loot", 1, 2);
                }
            }
            PENDING_LOOT.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(REAL_SKY::tick);
        ServerTickEvents.END_SERVER_TICK.register(Space::tick);
        ServerTickEvents.END_SERVER_TICK.register(ANTI_FLY::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            START_POINTS.onJoin(handler.player);
            Progression.get().applyPerks(handler.player);
            ServerGuide.welcome(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            START_POINTS.onLeave(handler.player);
            GUI_RATE.forget(handler.player);
            ANTI_FLY.forget(handler.player);
            Tutorial.get().onLeave(handler.player);
            SURVIVAL.onLeave(handler.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SURVIVAL.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> AUCTION_HOUSE.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> MISSIONS.register(dispatcher, AUCTION_HOUSE));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ServerGuide.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> Tutorial.get().register(dispatcher));
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
                                .then(Commands.literal("voiture").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(command -> spawnVehicle(command.getSource(), Vehicle.Kind.CAR)))
                                .then(Commands.literal("camion").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(command -> spawnVehicle(command.getSource(), Vehicle.Kind.TRUCK)))
                                .then(Commands.literal("moto").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(command -> spawnVehicle(command.getSource(), Vehicle.Kind.MOTORCYCLE)))
                                .then(Commands.literal("partager")
                                        .then(Commands.argument("joueur", EntityArgument.player())
                                                .executes(command -> shareVehicle(command.getSource(),
                                                        EntityArgument.getPlayer(command, "joueur"), true))))
                                .then(Commands.literal("retirer")
                                        .then(Commands.argument("joueur", EntityArgument.player())
                                                .executes(command -> shareVehicle(command.getSource(),
                                                        EntityArgument.getPlayer(command, "joueur"), false))))
                                .then(Commands.literal("liberer")
                                        .executes(command -> releaseVehicle(command.getSource()))))
                        .then(Commands.literal("depart")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(command -> {
                                    START_POINTS.openMap(command.getSource().getPlayerOrException(), false);
                                    return 1;
                                }))));

        LOGGER.info("TerraCraft Geo chargé : générateur terracraft_geo:earth et carte du monde prêts.");
    }
}
