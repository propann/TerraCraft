package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Missions courtes, persistantes et liées aux statistiques de progression existantes. */
final class Missions {
    private record Mission(String id, String title, String description, String stat, long target, long reward) {
    }

    private static final List<Mission> LIST = List.of(
            new Mission("first_steps", "Premiers pas", "Explore 1 zone", "zones", 1, 100),
            new Mission("traveller", "Voyageur", "Explore 5 zones", "zones", 5, 300),
            new Mission("scavenger", "Récupérateur", "Fouille 5 coffres", "loot", 5, 250),
            new Mission("mechanic", "Mécanicien", "Assemble 1 véhicule", "vehicles", 1, 400),
            new Mission("driver", "Conducteur", "Parcours 1 000 blocs en véhicule", "driven", 1_000, 500),
            new Mission("cleaner", "Nettoyeur", "Élimine 10 monstres", "kills", 10, 350),
            new Mission("bunker", "Abri sûr", "Découvre 1 bunker", "bunkers", 1, 450),
            new Mission("launch", "Vers les étoiles", "Lance 1 fusée", "launches", 1, 1_000),
            new Mission("merchant", "Marchand", "Mets 1 objet en vente à l'hôtel des ventes", "listings", 1, 200),
            new Mission("customer", "Client du comptoir", "Achète 1 offre au comptoir", "shop", 1, 100),
            new Mission("citizen", "Citoyen", "Fonde ou rejoins une ville", "town", 1, 300),
            new Mission("specialist", "Spécialiste", "Choisis un métier (/metier)", "job", 1, 150),
            new Mission("supply", "Largage", "Ouvre 1 caisse de ravitaillement", "supplies", 1, 300),
            new Mission("lunar_miner", "Mineur lunaire", "Mine 5 minerais de titane", "titanium", 5, 400),
            new Mission("orbital_builder", "Bâtisseur orbital", "Pose 1 module de station dans l'espace", "modules", 1, 500),
            new Mission("space_station", "Station spatiale", "Pose 4 modules de station", "modules", 4, 1_200),
            new Mission("engineer", "Ingénieur spatial", "Installe 1 amélioration de fusée à l'atelier", "upgrades", 1, 400),
            // Collections : paliers de lieux réels visités (chacun ne compte qu'une fois).
            new Mission("guide", "Guide des ruines", "Visite 5 lieux réels (/lieux)", "lieux", 5, 300),
            new Mission("cartographer", "Cartographe", "Visite 20 lieux réels", "lieux", 20, 800),
            new Mission("memory", "Mémoire du monde", "Visite 50 lieux réels", "lieux", 50, 2_000),
            new Mission("medic", "Infirmier de fortune", "Visite 5 hôpitaux ou pharmacies", "lieux_soins", 5, 400),
            new Mission("traveller_net", "Routard", "Visite 5 gares ou stations-service", "lieux_transport", 5, 400));

    private final Map<UUID, Set<String>> claimed = new HashMap<>();
    private Contracts contracts;

    void contracts(Contracts contracts) {
        this.contracts = contracts;
    }
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path file;

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("missions.json");
        claimed.clear();
        Map<String, Set<String>> stored = JsonStore.load(file,
                json -> gson.fromJson(json, new TypeToken<Map<String, Set<String>>>() { }.getType()));
        if (stored != null) {
            stored.forEach((uuid, ids) -> claimed.put(UUID.fromString(uuid), new HashSet<>(ids)));
        }
    }

    void save() {
        if (file == null) {
            return;
        }
        try {
            Map<String, Set<String>> stored = new HashMap<>();
            claimed.forEach((uuid, ids) -> stored.put(uuid.toString(), ids));
            JsonStore.write(file, gson.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'enregistrer {}", file, e);
        }
    }

    boolean hasClaimedAny(ServerPlayer player) {
        return !claimed.getOrDefault(player.getUUID(), Set.of()).isEmpty();
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher, AuctionHouse auctionHouse) {
        dispatcher.register(Commands.literal("missions")
                .executes(c -> show(c.getSource().getPlayerOrException(), auctionHouse))
                .then(Commands.literal("voir").executes(c -> show(c.getSource().getPlayerOrException(), auctionHouse)))
                .then(Commands.literal("reclamer")
                        .then(Commands.argument("mission", StringArgumentType.word())
                                .executes(c -> claim(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "mission"), auctionHouse)))));
    }

    private int show(ServerPlayer player, AuctionHouse auctionHouse) {
        Set<String> done = claimed.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        if (contracts != null) {
            player.sendSystemMessage(Component.literal("✦ Contrats du jour").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (Contracts.Contract contract : Contracts.todays()) {
                long progress = contracts.progress(player, contract);
                boolean ready = progress >= contract.target() && !contracts.claimed(player, contract);
                String state = contracts.claimed(player, contract) ? "✓" : ready ? "[Réclamer]" : "[En cours]";
                player.sendSystemMessage(Component.literal(state + " ").withStyle(s -> s
                                .withColor(ready ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                                .withClickEvent(ready ? new ClickEvent.RunCommand("/missions reclamer " + Contracts.PREFIX + contract.id()) : null))
                        .append(Component.literal(contract.title() + " : " + contract.description() + " (" + progress + "/"
                                + contract.target() + ") — " + contract.reward() + " crédits").withStyle(ChatFormatting.WHITE)));
            }
        }
        player.sendSystemMessage(Component.literal("✦ Missions TerraCraft").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (Mission mission : LIST) {
            long progress = Math.min(mission.target(), Progression.get().stat(player, mission.stat()));
            if (done.contains(mission.id())) {
                player.sendSystemMessage(Component.literal("✓ " + mission.title() + " — récompense récupérée")
                        .withStyle(ChatFormatting.DARK_GREEN));
            } else {
                net.minecraft.network.chat.MutableComponent button = Component.literal("[" + (progress >= mission.target() ? "Réclamer" : "En cours") + "] ")
                        .withStyle(style -> style.withColor(progress >= mission.target() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                                .withClickEvent(progress >= mission.target()
                                        ? new ClickEvent.RunCommand("/missions reclamer " + mission.id()) : null));
                Component line = button.append(Component.literal(mission.title() + " : " + mission.description()
                        + " (" + progress + "/" + mission.target() + ") — " + mission.reward() + " crédits")
                        .withStyle(ChatFormatting.WHITE));
                player.sendSystemMessage(line);
            }
        }
        return 1;
    }

    private int claim(ServerPlayer player, String id, AuctionHouse auctionHouse) {
        if (id.startsWith(Contracts.PREFIX)) {
            return contracts != null && contracts.claim(player, id) ? 1 : 0;
        }
        Mission mission = LIST.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
        if (mission == null) {
            player.sendSystemMessage(Component.literal("Mission inconnue. Fais /missions.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Set<String> done = claimed.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        if (!done.add(id)) {
            player.sendSystemMessage(Component.literal("Cette mission a déjà été réclamée.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (Progression.get().stat(player, mission.stat()) < mission.target()) {
            done.remove(id);
            player.sendSystemMessage(Component.literal("Mission non terminée.").withStyle(ChatFormatting.RED));
            return 0;
        }
        save();
        auctionHouse.credit(player, mission.reward(), mission.title());
        GeoMod.LOGGER.info("[MISSION] {} réclame {} (+{} crédits)", player.getName().getString(), mission.id(), mission.reward());
        player.sendSystemMessage(Component.literal("Mission terminée : " + mission.title()).withStyle(ChatFormatting.GOLD));
        return 1;
    }

    void send(ServerPlayer player) {
        Set<String> done = claimed.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
        com.google.gson.JsonArray missions = new com.google.gson.JsonArray();
        for (Mission mission : LIST) {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("id", mission.id());
            json.addProperty("title", mission.title());
            json.addProperty("description", mission.description());
            json.addProperty("progress", Math.min(mission.target(), Progression.get().stat(player, mission.stat())));
            json.addProperty("target", mission.target());
            json.addProperty("reward", mission.reward());
            json.addProperty("claimed", done.contains(mission.id()));
            missions.add(json);
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("missions", missions);
        root.add("contracts", contracts == null ? new com.google.gson.JsonArray() : contracts.toJson(player));
        Jobs.Job job = Jobs.of(player);
        root.addProperty("job", job == null ? "Aucun métier — /metier" : job.label + " : " + job.bonus);
        ServerPlayNetworking.send(player, new MissionPayload(gson.toJson(root)));
    }

    void claimFromClient(ServerPlayer player, String id, AuctionHouse auctionHouse) {
        claim(player, id, auctionHouse);
        send(player);
    }
}
