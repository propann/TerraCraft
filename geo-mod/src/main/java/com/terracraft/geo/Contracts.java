package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Contrats du jour : trois objectifs courts tirés chaque jour (les mêmes pour tout le monde),
 * comptés depuis le début de la journée du joueur. Un joueur a toujours une prochaine activité
 * rémunérée, sans grind : les contrats changent à minuit (heure de Paris).
 */
final class Contracts {
    static final String PREFIX = "contrat:";
    private static final int PER_DAY = 3;
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Contract(String id, String title, String description, String stat, long target, long reward) {
    }

    static final List<Contract> POOL = List.of(
            new Contract("pillage", "Pillage du jour", "Fouille 3 coffres jamais ouverts", "loot", 3, 150),
            new Contract("nettoyage", "Nettoyage", "Élimine 8 monstres", "kills", 8, 180),
            new Contract("livraison", "Livraison", "Parcours 800 blocs en véhicule", "driven", 800, 200),
            new Contract("reconnaissance", "Reconnaissance", "Explore 2 nouvelles zones", "zones", 2, 200),
            new Contract("commerce", "Commerce", "Mets 1 objet en vente à l'hôtel des ventes", "listings", 1, 120),
            new Contract("ravitaillement", "Ravitaillement", "Achète 1 offre au comptoir", "shop", 1, 80),
            new Contract("titane", "Minerai lunaire", "Mine 4 minerais de titane", "titanium", 4, 300),
            new Contract("speleologie", "Spéléologie", "Découvre 1 cave à monstres", "caves", 1, 220),
            new Contract("caisse", "Largage militaire", "Ouvre 1 caisse de ravitaillement", "supplies", 1, 250),
            new Contract("tournee", "Tournée des ruines", "Visite 3 lieux réels (/lieux)", "lieux", 3, 220),
            new Contract("urgences", "Urgences", "Visite 1 hôpital ou pharmacie (/lieux)", "lieux_soins", 1, 180),
            new Contract("transports", "Réseau de transport", "Visite 1 gare ou station-service (/lieux)", "lieux_transport", 1, 180));

    /** État d'un joueur pour la journée : compteurs au début du jour et contrats réclamés. */
    private static final class Daily {
        long day;
        Map<String, Long> baseline = new HashMap<>();
        Set<String> claimed = new HashSet<>();
    }

    private final Map<UUID, Daily> players = new HashMap<>();
    private final AuctionHouse bank;
    private Path file;

    Contracts(AuctionHouse bank) {
        this.bank = bank;
    }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("contrats.json");
        players.clear();
        Map<String, Daily> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Map<String, Daily>>() { }.getType()));
        if (stored != null) {
            stored.forEach((uuid, daily) -> players.put(UUID.fromString(uuid), daily));
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        Map<String, Daily> stored = new HashMap<>();
        players.forEach((uuid, daily) -> stored.put(uuid.toString(), daily));
        try {
            JsonStore.write(file, GSON.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    static long today() {
        return LocalDate.now(ZONE).toEpochDay();
    }

    /** Les contrats du jour, identiques pour tous les joueurs. */
    static List<Contract> todays() {
        List<Contract> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, new Random(today() * 7_919L));
        return pool.subList(0, PER_DAY);
    }

    /** Ouvre la journée du joueur : ses compteurs actuels servent de point de départ. */
    private Daily daily(ServerPlayer player) {
        Daily daily = players.get(player.getUUID());
        long today = today();
        if (daily == null || daily.day != today) {
            daily = new Daily();
            daily.day = today;
            for (Contract contract : POOL) {
                daily.baseline.put(contract.stat(), Progression.get().stat(player, contract.stat()));
            }
            players.put(player.getUUID(), daily);
            save();
        }
        return daily;
    }

    long progress(ServerPlayer player, Contract contract) {
        Daily daily = daily(player);
        long start = daily.baseline.getOrDefault(contract.stat(), 0L);
        return Math.min(contract.target(), Math.max(0, Progression.get().stat(player, contract.stat()) - start));
    }

    boolean claimed(ServerPlayer player, Contract contract) {
        return daily(player).claimed.contains(contract.id());
    }

    /** Toutes les 10 secondes : ouvre la journée des joueurs connectés (minuit passé). */
    void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 == 0) {
            server.getPlayerList().getPlayers().forEach(this::daily);
        }
    }

    /** Réclame un contrat du jour ; renvoie vrai si la récompense a été versée. */
    boolean claim(ServerPlayer player, String id) {
        Contract contract = todays().stream().filter(c -> (PREFIX + c.id()).equals(id)).findFirst().orElse(null);
        if (contract == null) {
            player.sendSystemMessage(Component.literal("Ce contrat n'est plus proposé aujourd'hui.").withStyle(ChatFormatting.RED));
            return false;
        }
        Daily daily = daily(player);
        if (daily.claimed.contains(contract.id())) {
            player.sendSystemMessage(Component.literal("Contrat déjà réclamé aujourd'hui.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (progress(player, contract) < contract.target()) {
            player.sendSystemMessage(Component.literal("Contrat pas encore rempli.").withStyle(ChatFormatting.RED));
            return false;
        }
        daily.claimed.add(contract.id());
        save();
        bank.credit(player, contract.reward(), "Contrat du jour : " + contract.title());
        GeoMod.LOGGER.info("[CONTRAT] {} remplit « {} » (+{} crédits)", player.getName().getString(), contract.title(), contract.reward());
        return true;
    }

    JsonArray toJson(ServerPlayer player) {
        JsonArray array = new JsonArray();
        for (Contract contract : todays()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", PREFIX + contract.id());
            json.addProperty("title", contract.title());
            json.addProperty("description", contract.description());
            json.addProperty("progress", progress(player, contract));
            json.addProperty("target", contract.target());
            json.addProperty("reward", contract.reward());
            json.addProperty("claimed", claimed(player, contract));
            array.add(json);
        }
        return array;
    }
}
