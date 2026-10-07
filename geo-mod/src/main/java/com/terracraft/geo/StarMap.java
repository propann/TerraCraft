package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.terracraft.geo.content.Rocket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carte des étoiles : écran de navigation de la fusée (Maj + clic droit, menu O, ou /fusee carte). Montre chaque
 * destination avec son coût en doses, ce qui empêche d'y aller (route, plan, carburant) et les stations du joueur.
 * Les règles sont celles du décollage ({@link Rocket#problemFor}) : la carte n'en invente aucune.
 */
public final class StarMap {
    /** Ordre d'affichage, et noms acceptés par /fusee cap. */
    static final Map<String, Byte> DESTINATIONS = new LinkedHashMap<>();

    static {
        DESTINATIONS.put("terre", Space.EARTH);
        DESTINATIONS.put("orbite", Space.ORBIT_ID);
        DESTINATIONS.put("orbite_lunaire", Space.MOON_ORBIT_ID);
        DESTINATIONS.put("lune", Space.MOON_ID);
        DESTINATIONS.put("orbite_mars", Space.MARS_ORBIT_ID);
        DESTINATIONS.put("mars", Space.MARS_ID);
    }

    private static final double REACH = 8;

    private StarMap() {
    }

    /** Fusée où le joueur est assis, sinon la plus proche à portée de main. */
    static Rocket rocketOf(ServerPlayer player) {
        if (player.getVehicle() instanceof Rocket rocket) {
            return rocket;
        }
        return player.level().getEntitiesOfClass(Rocket.class, player.getBoundingBox().inflate(REACH)).stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player))).orElse(null);
    }

    public static void open(ServerPlayer player, Rocket rocket) {
        JsonObject data = new JsonObject();
        data.addProperty("rocket", rocket.getId());
        data.addProperty("here", Space.id(player.level()));
        data.addProperty("target", rocket.target());
        data.addProperty("fuel", rocket.fuel());
        data.addProperty("maxFuel", rocket.maxFuel());
        data.addProperty("tier", rocket.tierName());
        data.addProperty("pilot", rocket.getFirstPassenger() == player);
        data.addProperty("complete", rocket.isComplete());
        JsonArray destinations = new JsonArray();
        for (byte id : DESTINATIONS.values()) {
            JsonObject destination = new JsonObject();
            destination.addProperty("id", id);
            destination.addProperty("name", Space.name(id));
            destination.addProperty("cost", id == Space.id(player.level()) ? 0 : rocket.costTo(id));
            String problem = rocket.problemFor(player, id);
            if (problem != null) {
                destination.addProperty("problem", problem);
            }
            ServerLevel level = Space.level(player.level().getServer(), id);
            destination.addProperty("station", level != null && Stations.get().hasStation(player, level));
            destinations.add(destination);
        }
        data.add("destinations", destinations);
        JsonArray routes = new JsonArray();
        for (int[] route : Space.ROUTES) {
            JsonArray r = new JsonArray();
            for (int value : route) {
                r.add(value);
            }
            routes.add(r);
        }
        data.add("routes", routes);
        ServerPlayNetworking.send(player, new StarMapPayload(data.toString()));
    }

    /** Action de la carte : vérifie la fusée et la destination, met le cap, décolle si demandé. */
    static void act(ServerPlayer player, StarMapActionPayload action) {
        Rocket rocket = player.level().getEntity(action.rocket()) instanceof Rocket r && r.distanceTo(player) <= REACH + 2
                ? r : rocketOf(player);
        if (rocket == null) {
            player.sendOverlayMessage(Component.literal("Aucune fusée à portée.").withStyle(ChatFormatting.RED));
            return;
        }
        if (action.destination() >= 0) {
            setCourse(player, rocket, action.destination());
            if (action.launch()) {
                rocket.requestLaunch(player, true);
                return;
            }
        }
        open(player, rocket);
    }

    /** Met le cap (même si le vol n'est pas encore possible : le joueur voit ce qui manque). */
    static boolean setCourse(ServerPlayer player, Rocket rocket, byte destination) {
        if (!DESTINATIONS.containsValue(destination) || destination == Space.id(player.level())) {
            player.sendOverlayMessage(Component.literal("Destination invalide.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (rocket.phase() != Rocket.IDLE) {
            return false;
        }
        rocket.setTarget(destination);
        String problem = rocket.problemFor(player, destination);
        GeoMod.LOGGER.info("[NAV] {} met le cap sur {} : {} dose(s), {}", player.getName().getString(), Space.name(destination),
                rocket.costTo(destination), problem == null ? "prête" : problem);
        player.sendSystemMessage(Component.literal("Cap sur " + Space.name(destination) + " : " + rocket.costTo(destination)
                        + " dose(s)" + (problem == null ? " — prête au décollage." : " — " + problem))
                .withStyle(problem == null ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return true;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fusee")
                .then(Commands.literal("carte").executes(command -> {
                    ServerPlayer player = command.getSource().getPlayerOrException();
                    Rocket rocket = rocketOf(player);
                    if (rocket == null) {
                        command.getSource().sendFailure(Component.literal("Aucune fusée à portée."));
                        return 0;
                    }
                    open(player, rocket);
                    return 1;
                }))
                .then(Commands.literal("cap").then(Commands.argument("destination", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(DESTINATIONS.keySet(), builder))
                        .executes(command -> {
                            ServerPlayer player = command.getSource().getPlayerOrException();
                            Byte destination = DESTINATIONS.get(StringArgumentType.getString(command, "destination"));
                            Rocket rocket = rocketOf(player);
                            if (destination == null || rocket == null) {
                                command.getSource().sendFailure(Component.literal(rocket == null ? "Aucune fusée à portée."
                                        : "Destinations : " + String.join(", ", DESTINATIONS.keySet())));
                                return 0;
                            }
                            return setCourse(player, rocket, destination) ? 1 : 0;
                        }))));
    }
}
