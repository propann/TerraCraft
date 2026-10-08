package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.terracraft.geo.world.OsmCells;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lieux réels (points d'intérêt OpenStreetMap) : /lieux donne le plus proche de chaque type avec son vrai nom, sa
 * distance et sa direction ; entrer dans un lieu pour la première fois est une découverte. Leurs bâtiments ont un
 * butin à leur image (voir Wasteland).
 */
final class Places {
    record Kind(String label, String discovery) {
    }

    static final Map<String, Kind> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("hospital", new Kind("✚ Hôpital", "lieu_hospital"));
        KINDS.put("pharmacy", new Kind("✚ Pharmacie", "lieu_pharmacy"));
        KINDS.put("police", new Kind("★ Commissariat", "lieu_police"));
        KINDS.put("grocery", new Kind("🛒 Supermarché", "lieu_grocery"));
        KINDS.put("fuel", new Kind("⛽ Station-service", "lieu_fuel"));
        KINDS.put("railway", new Kind("🚉 Gare", "lieu_railway"));
        KINDS.put("fire_station", new Kind("🔥 Caserne de pompiers", "lieu_fire_station"));
        KINDS.put("school", new Kind("✎ École", "lieu_school"));
        KINDS.put("college", new Kind("✎ Université", "lieu_school"));
        KINDS.put("town_hall", new Kind("🏛 Mairie", "lieu_town_hall"));
    }

    private Places() {
    }

    /** Lieux connus autour du joueur (cellule de carte où il se trouve et ses voisines). */
    static List<OsmCells.Poi> around(ServerPlayer player) {
        List<OsmCells.Poi> pois = new ArrayList<>();
        var generator = StartPoints.generator(player.level().getServer());
        if (generator == null || player.level() != player.level().getServer().overworld()) {
            return pois;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                // Non bloquant : une cellule pas encore chargée sera prête au passage suivant.
                OsmCells.Cell cell = generator.terrain().osm().cellIfLoaded(player.getBlockX() + dx * OsmCells.CELL_SIZE,
                        player.getBlockZ() + dz * OsmCells.CELL_SIZE);
                if (cell != null) {
                    pois.addAll(cell.pois());
                }
            }
        }
        return pois;
    }

    /** « nord-est », « sud »… d'après un déplacement (x vers l'est, z vers le sud). */
    static String direction(double dx, double dz) {
        String[] names = {"est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest", "nord", "nord-est"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        return names[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lieux").executes(c -> list(c.getSource().getPlayerOrException())));
    }

    private static int list(ServerPlayer player) {
        List<OsmCells.Poi> pois = around(player);
        if (pois.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucun lieu connu ici (hors ville, ou carte pas encore chargée).").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Lieux les plus proches :").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        GeoMod.LOGGER.info("[LIEUX] {} lieux connus autour de {} ({})", pois.size(), player.getName().getString(),
                String.join(", ", pois.stream().map(OsmCells.Poi::kind).distinct().sorted().toList()));
        for (Map.Entry<String, Kind> entry : KINDS.entrySet()) {
            pois.stream().filter(p -> p.kind().equals(entry.getKey()))
                    .min(Comparator.comparingDouble(p -> Math.hypot(p.x() - player.getX(), p.z() - player.getZ())))
                    .ifPresent(p -> {
                        double dx = p.x() - player.getX();
                        double dz = p.z() - player.getZ();
                        int distance = (int) Math.hypot(dx, dz);
                        player.sendSystemMessage(Component.literal("  " + entry.getValue().label() + (p.name().isBlank() ? "" : " « " + p.name() + " »")
                                + " — " + distance + " m " + (distance < 15 ? "(ici)" : "vers le " + direction(dx, dz)))
                                .withStyle(ChatFormatting.WHITE));
                    });
        }
        return 1;
    }

    /** Toutes les 2 s : premier passage dans un lieu de chaque type = découverte. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) {
            return;
        }
        for (ServerPlayer player : server.overworld().players()) {
            for (OsmCells.Poi poi : around(player)) {
                Kind kind = KINDS.get(poi.kind());
                if (kind == null || Math.hypot(poi.x() - player.getX(), poi.z() - player.getZ()) >= 14) {
                    continue;
                }
                if (!Progression.get().hasDiscovered(player, kind.discovery())) {
                    Progression.get().discover(player, kind.discovery());
                }
                // Chaque lieu réel ne compte qu'une fois par joueur (contrats « visite des lieux »).
                if (Progression.get().firstVisit(player, "lieu:" + poi.kind() + ":" + poi.x() + "," + poi.z())) {
                    Progression.get().count(player, "lieux", 1, 2);
                    switch (poi.kind()) {
                        case "hospital", "pharmacy" -> Progression.get().count(player, "lieux_soins", 1, 0);
                        case "railway", "fuel" -> Progression.get().count(player, "lieux_transport", 1, 0);
                        default -> {
                        }
                    }
                    player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(kind.label()
                            + (poi.name().isBlank() ? "" : " « " + poi.name() + " »") + " — lieu visité").withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }
}
