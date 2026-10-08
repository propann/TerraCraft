package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terracraft.geo.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Outils de l'économie : catégories d'objets, historique des prix de l'hôtel des ventes,
 * comptoir du serveur (dépenses utiles) et journal des crédits créés et détruits.
 */
final class Economy {
    /** Frais de mise en vente, en pour mille du prix demandé (20 ‰ = 2 %). */
    static final int LISTING_FEE_PER_MILLE = 20;
    /** Nombre de ventes gardées pour le prix moyen d'un objet. */
    private static final int PRICE_HISTORY = 20;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    enum Category {
        TOUT("Tout"), RESSOURCES("Ressources"), BLOCS("Blocs"), PIECES("Pièces"), ARMES("Armes"),
        ESPACE("Espace"), NOURRITURE("Nourriture"), EQUIPEMENT("Équipement");

        final String label;

        Category(String label) {
            this.label = label;
        }

        static Category parse(String name) {
            for (Category category : values()) {
                if (category.name().equalsIgnoreCase(name)) {
                    return category;
                }
            }
            return TOUT;
        }
    }

    private static final Set<String> MOD_PARTS = Set.of("wheel", "engine", "radiator", "battery", "turbo", "fuel_can",
            "car_chassis", "truck_chassis", "motorcycle_chassis", "plane_kit", "rocket_hull", "rocket_engine", "rocket_tank",
            "nose_cone", "fins", "rocket_fuel");
    private static final Set<String> MOD_WEAPONS = Set.of("pistol", "rifle", "shotgun", "smg", "sniper", "ammo",
            "grenade", "machete");

    static Category categoryOf(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.getNamespace().equals(GeoMod.MOD_ID)) {
            String path = id.getPath();
            return MOD_PARTS.contains(path) ? Category.PIECES : MOD_WEAPONS.contains(path) ? Category.ARMES : Category.ESPACE;
        }
        if (stack.has(DataComponents.FOOD)) {
            return Category.NOURRITURE;
        }
        if (stack.has(DataComponents.WEAPON) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.ARROW)) {
            return Category.ARMES;
        }
        if (stack.has(DataComponents.EQUIPPABLE) || stack.has(DataComponents.TOOL) || stack.isDamageableItem()) {
            return Category.EQUIPEMENT;
        }
        return stack.getItem() instanceof BlockItem ? Category.BLOCS : Category.RESSOURCES;
    }

    // --- Comptoir du serveur -----------------------------------------------------------------

    /** Offre permanente du comptoir : des consommables utiles, pour donner un usage aux crédits. */
    /**
     * Offre du comptoir. {@code job} et {@code level} non nuls : offre réservée (réputation) aux joueurs de ce métier
     * ayant ce niveau dans la compétence du métier.
     */
    record Offer(String label, Supplier<Item> item, int count, long price, Jobs.Job job, int level) {
        Offer(String label, Supplier<Item> item, int count, long price) {
            this(label, item, count, price, null, 0);
        }

        ItemStack stack() {
            return new ItemStack(item.get(), count);
        }

        /** Pourquoi ce joueur ne peut pas acheter (réputation), ou null. */
        String locked(net.minecraft.server.level.ServerPlayer player) {
            if (job == null) {
                return null;
            }
            if (Jobs.of(player) != job) {
                return "réservé aux " + job.label.toLowerCase(java.util.Locale.ROOT) + "s";
            }
            Progression.Skill skill = Jobs.skillOf(job);
            int have = Progression.get().skillLevel(player, skill);
            return have >= level ? null : "réputation " + job.label.toLowerCase(java.util.Locale.ROOT) + " " + level
                    + " requise (" + skill.label + " " + have + ")";
        }
    }

    static final List<Offer> SHOP = List.of(
            new Offer("Bidon d'essence", () -> ModContent.FUEL_CAN, 1, 60),
            new Offer("Bouteille d'oxygène", () -> ModContent.OXYGEN_TANK, 1, 80),
            new Offer("Munitions", () -> ModContent.AMMO, 16, 45),
            new Offer("Pain", () -> Items.BREAD, 8, 25),
            new Offer("Torches", () -> Items.TORCH, 16, 15),
            new Offer("Pomme dorée", () -> Items.GOLDEN_APPLE, 1, 150),
            // Réputation : offres réservées à un métier, à partir d'un niveau dans sa compétence.
            new Offer("Carburant de fusée", () -> ModContent.ROCKET_FUEL, 1, 180, Jobs.Job.PILOTE, 2),
            new Offer("Caisse de munitions", () -> ModContent.AMMO, 64, 140, Jobs.Job.COMBATTANT, 2),
            new Offer("Moteur", () -> ModContent.ENGINE, 1, 220, Jobs.Job.MECANICIEN, 2),
            new Offer("Roues", () -> ModContent.WHEEL, 2, 90, Jobs.Job.MECANICIEN, 1),
            new Offer("Détecteur de pétrole", () -> com.terracraft.geo.content.industry.IndustryBlocks.OIL_DETECTOR, 1, 150,
                    Jobs.Job.ECLAIREUR, 1),
            new Offer("Batterie", () -> ModContent.BATTERY, 1, 120, Jobs.Job.RECUPERATEUR, 2));

    // --- Historique des prix et journal --------------------------------------------------------

    /** Prix unitaires récents d'un objet vendu à l'hôtel des ventes. */
    static final class PriceStats {
        List<Long> recent = new ArrayList<>();
        long volume;
        long last;
    }

    private static final class Saved {
        Map<String, Long> created = new TreeMap<>();
        Map<String, Long> destroyed = new TreeMap<>();
        Map<String, PriceStats> prices = new LinkedHashMap<>();
    }

    private Saved data = new Saved();
    private Path file;

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("economie.json");
        Saved stored = JsonStore.load(file, json -> GSON.fromJson(json, Saved.class));
        data = stored == null ? new Saved() : stored;
    }

    void save() {
        if (file == null) {
            return;
        }
        try {
            JsonStore.write(file, GSON.toJson(data));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    /** Crédits créés (départ, récompenses, admin…). */
    void created(String source, long amount) {
        data.created.merge(source, amount, Long::sum);
    }

    /** Crédits retirés de l'économie (frais, comptoir…). */
    void destroyed(String sink, long amount) {
        data.destroyed.merge(sink, amount, Long::sum);
    }

    Map<String, Long> createdTotals() {
        return data.created;
    }

    Map<String, Long> destroyedTotals() {
        return data.destroyed;
    }

    void recordSale(String itemId, int count, long price) {
        PriceStats stats = data.prices.computeIfAbsent(itemId, ignored -> new PriceStats());
        long unit = Math.max(1, Math.round((double) price / Math.max(1, count)));
        stats.recent.add(unit);
        while (stats.recent.size() > PRICE_HISTORY) {
            stats.recent.remove(0);
        }
        stats.volume += count;
        stats.last = unit;
    }

    /** Prix unitaire moyen des dernières ventes, ou -1 si l'objet n'a jamais été vendu. */
    long averageUnitPrice(String itemId) {
        PriceStats stats = data.prices.get(itemId);
        if (stats == null || stats.recent.isEmpty()) {
            return -1;
        }
        return Math.round(stats.recent.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    long volume(String itemId) {
        PriceStats stats = data.prices.get(itemId);
        return stats == null ? 0 : stats.volume;
    }

    static long listingFee(long price) {
        return Math.max(1, (price * LISTING_FEE_PER_MILLE + 999) / 1000);
    }
}
