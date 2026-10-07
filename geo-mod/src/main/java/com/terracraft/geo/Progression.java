package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.ModMobs;
import com.terracraft.geo.world.GeoChunkGenerator;
import com.terracraft.geo.world.Wasteland;
import com.terracraft.geo.world.WebMercator;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Progression du survivant : chaque découverte rapporte des points ; 10 paliers débloquent
 * une amélioration permanente et du matériel. La fiche de personnage (touche K) affiche tout.
 */
public final class Progression {
    // --- Paliers et améliorations ------------------------------------------------------------

    record Perk(String name, String description, Holder<Attribute> attribute, double amount,
                AttributeModifier.Operation operation, List<ItemStack> rewards) {
    }

    static final int[] THRESHOLDS = {50, 150, 300, 500, 800, 1200, 1700, 2300, 3000, 4000};

    static List<Perk> perks() {
        return List.of(
                perk("Endurci", "+1 cœur", Attributes.MAX_HEALTH, 2, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.PISTOL, 1), stack(ModContent.AMMO, 16)),
                perk("Pieds légers", "+5 % de vitesse", Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        stack(ModContent.CAR_CHASSIS, 1), stack(ModContent.WHEEL, 4)),
                perk("Poumons d'acier", "Oxygène consommé 2× moins vite", null, 0, null,
                        stack(ModContent.ENGINE, 1), stack(ModContent.RADIATOR, 1), stack(ModContent.BATTERY, 1), stack(ModContent.FUEL_CAN, 2)),
                perk("Coriace", "+1 cœur", Attributes.MAX_HEALTH, 2, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.SHOTGUN, 1), stack(ModContent.AMMO, 24)),
                perk("Peau tannée", "+2 d'armure", Attributes.ARMOR, 2, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.SPACE_HELMET, 1), stack(ModContent.OXYGEN_TANK, 3)),
                perk("Survivant aguerri", "+10 % de dégâts", Attributes.ATTACK_DAMAGE, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                        stack(ModContent.ROCKET_HULL, 1), stack(ModContent.NOSE_CONE, 1), stack(ModContent.FINS, 1)),
                perk("Cœur vaillant", "+1 cœur", Attributes.MAX_HEALTH, 2, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.ROCKET_ENGINE, 1), stack(ModContent.ROCKET_TANK, 1), stack(ModContent.ROCKET_FUEL, 4)),
                perk("Atterrissage", "Chutes amorties (+4 blocs)", Attributes.SAFE_FALL_DISTANCE, 4, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModBlocks.STATION_HULL, 16), stack(ModBlocks.OXYGEN_DISTRIBUTOR, 1)),
                perk("Fouineur", "Meilleur butin (+1 chance)", Attributes.LUCK, 1, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.RIFLE, 1), stack(ModContent.AMMO, 32), stack(ModContent.TURBO, 1)),
                perk("Légende des ruines", "+2 cœurs", Attributes.MAX_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE,
                        stack(ModContent.TRUCK_CHASSIS, 1), stack(ModContent.WHEEL, 4), stack(Items.DIAMOND, 5)));
    }

    private static Perk perk(String name, String description, Holder<Attribute> attribute, double amount,
                             AttributeModifier.Operation operation, ItemStack... rewards) {
        return new Perk(name, description, attribute, amount, operation, List.of(rewards));
    }

    private static ItemStack stack(ItemLike item, int count) {
        return new ItemStack(item, count);
    }

    // --- Découvertes -------------------------------------------------------------------------

    record Discovery(String id, String name, int points) {
    }

    static final List<Discovery> DISCOVERIES = List.of(
            new Discovery("first_zone", "Premiers pas — atterrir dans le monde", 10),
            new Discovery("zones_5", "Vagabond — 5 zones explorées", 30),
            new Discovery("zones_20", "Grand voyageur — 20 zones explorées", 80),
            new Discovery("first_loot", "Pilleur — premier coffre fouillé", 5),
            new Discovery("loot_25", "Fouilleur de ruines — 25 coffres", 40),
            new Discovery("first_bunker", "Abri antiatomique — premier bunker", 30),
            new Discovery("first_cave", "Antre des monstres — première cave", 20),
            new Discovery("kills_50", "Nettoyeur — 50 monstres", 40),
            new Discovery("first_vehicle", "Mécano — premier véhicule assemblé", 30),
            new Discovery("drive_10k", "Routier — 10 000 blocs en véhicule", 50),
            new Discovery("first_launch", "Décollage — premier lancement de fusée", 50),
            new Discovery("moon", "Un petit pas — marcher sur la Lune", 80),
            new Discovery("orbit", "En orbite — atteindre l'orbite", 80),
            new Discovery("mars", "Planète rouge — atteindre Mars", 120),
            new Discovery("moon_orbit", "Orbite lunaire — tourner autour de la Lune", 80),
            new Discovery("first_module", "Bâtisseur orbital — premier module de station", 60),
            new Discovery("titanium", "Métal lunaire — miner du titane", 20),
            new Discovery("helium", "Hélium-3 — récolter des cristaux", 20),
            new Discovery("lunar_hunter", "Chasseur lunaire — 10 ennemis lunaires", 50),
            new Discovery("alien_sanctuary", "Sous la poussière — entrer dans un sanctuaire extraterrestre", 100));

    /** Libellés des compteurs affichés sur la fiche, dans l'ordre. */
    static final Map<String, String> STAT_LABELS = new java.util.LinkedHashMap<>();

    static {
        STAT_LABELS.put("zones", "Zones explorées");
        STAT_LABELS.put("bunkers", "Bunkers découverts");
        STAT_LABELS.put("caves", "Caves découvertes");
        STAT_LABELS.put("loot", "Coffres fouillés");
        STAT_LABELS.put("kills", "Monstres tués");
        STAT_LABELS.put("lunar_kills", "Ennemis lunaires tués");
        STAT_LABELS.put("vehicles", "Véhicules assemblés");
        STAT_LABELS.put("driven", "Blocs parcourus en véhicule");
        STAT_LABELS.put("launches", "Lancements de fusée");
        STAT_LABELS.put("titanium", "Titane miné");
        STAT_LABELS.put("helium", "Cristaux d'hélium-3");
        STAT_LABELS.put("supplies", "Caisses de ravitaillement ouvertes");
        STAT_LABELS.put("listings", "Objets mis en vente");
        STAT_LABELS.put("modules", "Modules de station posés");
        STAT_LABELS.put("upgrades", "Améliorations de fusée installées");
    }

    // --- Compétences --------------------------------------------------------------------------

    /** Arbres de compétences : chacun a son expérience, ses 20 niveaux et un bonus par niveau. */
    public enum Skill {
        COMBAT("Combat", "+2 % de dégâts, rechargement 2 % plus rapide"),
        EXPLORATION("Exploration", "+0,5 % de vitesse, +0,1 chance au butin"),
        MECHANICS("Mécanique", "−2 % de consommation d'essence"),
        SPACE("Espace", "−3 % de consommation d'oxygène");

        final String label;
        final String perLevel;

        Skill(String label, String perLevel) {
            this.label = label;
            this.perLevel = perLevel;
        }
    }

    public static final int MAX_SKILL_LEVEL = 20;

    /** Expérience totale nécessaire pour atteindre un niveau (courbe en n^1,5). */
    static int levelFor(long xp) {
        int level = 0;
        while (level < MAX_SKILL_LEVEL && xp >= cumulative(level + 1)) {
            level++;
        }
        return level;
    }

    /** Expérience cumulée pour atteindre le niveau donné. */
    static long cumulative(int level) {
        long total = 0;
        for (int n = 1; n <= level; n++) {
            total += Math.round(100 * Math.pow(n, 1.5));
        }
        return total;
    }

    public int skillLevel(ServerPlayer player, Skill skill) {
        return levelFor(record(player).skills.getOrDefault(skill.name(), 0L));
    }

    /** Gagne de l'expérience de compétence ; annonce et applique les montées de niveau. */
    public void train(ServerPlayer player, Skill skill, long xp) {
        Record r = record(player);
        int before = levelFor(r.skills.getOrDefault(skill.name(), 0L));
        long total = r.skills.merge(skill.name(), xp, Long::sum);
        dirty = true;
        int after = levelFor(total);
        if (after > before) {
            applyPerks(player);
            player.sendOverlayMessage(Component.literal(skill.label + " niveau " + after + " — " + skill.perLevel)
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    /** Mort : on perd 10 % de la progression du niveau en cours de chaque compétence. */
    public void onDeath(ServerPlayer player) {
        Record r = record(player);
        for (Skill skill : Skill.values()) {
            long xp = r.skills.getOrDefault(skill.name(), 0L);
            int level = levelFor(xp);
            long floor = cumulative(level);
            r.skills.put(skill.name(), xp - Math.round((xp - floor) * 0.10));
        }
        dirty = true;
    }

    /** Multiplicateur de dégâts des armes à feu (compétence Combat). */
    public double combatMultiplier(ServerPlayer player) {
        return 1 + 0.02 * skillLevel(player, Skill.COMBAT) + (Jobs.is(player, Jobs.Job.COMBATTANT) ? 0.15 : 0);
    }

    // --- Données par joueur ------------------------------------------------------------------

    static final class Record {
        int points;
        int level;
        Map<String, Long> stats = new HashMap<>();
        Set<String> discoveries = new LinkedHashSet<>();
        Set<String> places = new LinkedHashSet<>();
        Map<String, Long> skills = new HashMap<>();
        /** Métier choisi (nom de {@link Jobs.Job}) et date du dernier changement. */
        String job;
        long jobChanged;
        /** Plans de fusée débloqués (noms de {@link Plans.Plan}). */
        Set<String> plans = new LinkedHashSet<>();
        /** Dernière version du mod vue par le joueur (nouveautés à l'accueil). */
        String lastVersion;
    }

    String lastVersion(ServerPlayer player) {
        return record(player).lastVersion;
    }

    void setLastVersion(ServerPlayer player, String version) {
        Record r = record(player);
        if (!version.equals(r.lastVersion)) {
            r.lastVersion = version;
            dirty = true;
        }
    }

    boolean knowsPlan(ServerPlayer player, String plan) {
        Set<String> plans = record(player).plans;
        return plans != null && plans.contains(plan);
    }

    void learnPlan(ServerPlayer player, String plan) {
        Record r = record(player);
        if (r.plans == null) {
            r.plans = new LinkedHashSet<>();
        }
        r.plans.add(plan);
        dirty = true;
    }

    String jobName(ServerPlayer player) {
        return record(player).job;
    }

    long jobChangedAt(ServerPlayer player) {
        return record(player).jobChanged;
    }

    void setJob(ServerPlayer player, String job) {
        Record r = record(player);
        r.job = job;
        r.jobChanged = System.currentTimeMillis();
        dirty = true;
        save();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Progression INSTANCE = new Progression();

    private final Map<UUID, Record> records = new HashMap<>();
    private MinecraftServer server;
    private Path file;
    private boolean dirty;
    /**
     * Recherche des bunkers et caves hors du thread serveur : elle lit les données OSM et le
     * relief, qui peuvent devoir être téléchargés. Sur le thread principal, cela figeait tout le
     * serveur le temps du téléchargement.
     */
    private final ExecutorService locator = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TerraCraft-decouvertes");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<UUID> locating = ConcurrentHashMap.newKeySet();

    public static Progression get() {
        return INSTANCE;
    }

    void load(MinecraftServer server) {
        this.server = server;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("progression.json");
        records.clear();
        Map<String, Record> stored = JsonStore.load(file,
                json -> GSON.fromJson(json, new TypeToken<Map<String, Record>>() { }.getType()));
        if (stored != null) {
            stored.forEach((uuid, record) -> records.put(UUID.fromString(uuid), record));
        }
    }

    void save() {
        if (!dirty || file == null) {
            return;
        }
        Map<String, Record> stored = new HashMap<>();
        records.forEach((uuid, record) -> stored.put(uuid.toString(), record));
        try {
            JsonStore.write(file, GSON.toJson(stored));
            dirty = false;
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    private Record record(ServerPlayer player) {
        return records.computeIfAbsent(player.getUUID(), u -> new Record());
    }

    // --- Points, compteurs et découvertes ----------------------------------------------------------

    /** Ajoute un compteur et des points, puis vérifie les découvertes liées. */
    public void count(ServerPlayer player, String stat, long amount, int points) {
        Record r = record(player);
        long value = r.stats.merge(stat, amount, Long::sum);
        dirty = true;
        addPoints(player, points);
        switch (stat) {
            case "zones" -> train(player, Skill.EXPLORATION, 20 * amount);
            case "bunkers" -> train(player, Skill.EXPLORATION, 60 * amount);
            case "caves" -> train(player, Skill.EXPLORATION, 40 * amount);
            case "loot" -> train(player, Skill.EXPLORATION, 6 * amount);
            case "kills" -> train(player, Skill.COMBAT, 5 * amount);
            case "lunar_kills" -> train(player, Skill.COMBAT, 12 * amount);
            case "vehicles" -> train(player, Skill.MECHANICS, 80 * amount);
            case "driven" -> train(player, Skill.MECHANICS, 4 * amount / 100);
            case "launches" -> train(player, Skill.SPACE, 100 * amount);
            case "titanium", "helium" -> train(player, Skill.SPACE, 5 * amount);
            default -> {
            }
        }
        switch (stat) {
            case "zones" -> {
                discover(player, "first_zone");
                if (value >= 5) {
                    discover(player, "zones_5");
                }
                if (value >= 20) {
                    discover(player, "zones_20");
                }
            }
            case "loot" -> {
                discover(player, "first_loot");
                if (value >= 25) {
                    discover(player, "loot_25");
                }
            }
            case "bunkers" -> discover(player, "first_bunker");
            case "caves" -> discover(player, "first_cave");
            case "kills" -> {
                if (value >= 50) {
                    discover(player, "kills_50");
                }
            }
            case "lunar_kills" -> {
                if (value >= 10) {
                    discover(player, "lunar_hunter");
                }
            }
            case "vehicles" -> discover(player, "first_vehicle");
            case "driven" -> {
                if (value >= 10_000) {
                    discover(player, "drive_10k");
                }
            }
            case "launches" -> discover(player, "first_launch");
            case "modules" -> discover(player, "first_module");
            case "titanium" -> discover(player, "titanium");
            case "helium" -> discover(player, "helium");
            default -> {
            }
        }
    }

    /** Valeur actuelle d'un compteur pour les systèmes de missions et d'objectifs. */
    public long stat(ServerPlayer player, String stat) {
        return record(player).stats.getOrDefault(stat, 0L);
    }

    public void discover(ServerPlayer player, String id) {
        Record r = record(player);
        if (!r.discoveries.add(id)) {
            return;
        }
        dirty = true;
        Discovery discovery = DISCOVERIES.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
        if (discovery == null) {
            return;
        }
        if (id.equals("moon") || id.equals("orbit") || id.equals("mars") || id.equals("moon_orbit")) {
            train(player, Skill.SPACE, 150);
        }
        player.sendSystemMessage(Component.literal("✦ Découverte : " + discovery.name() + "  (+" + discovery.points() + " pts)")
                .withStyle(ChatFormatting.AQUA));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        addPoints(player, discovery.points());
    }

    private void addPoints(ServerPlayer player, int points) {
        if (points <= 0) {
            return;
        }
        Record r = record(player);
        r.points += points;
        dirty = true;
        List<Perk> perks = perks();
        while (r.level < THRESHOLDS.length && r.points >= THRESHOLDS[r.level]) {
            Perk perk = perks.get(r.level);
            r.level++;
            reward(player, r.level, perk);
        }
    }

    private void reward(ServerPlayer player, int level, Perk perk) {
        applyPerks(player);
        for (ItemStack stack : perk.rewards()) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.spawnAtLocation(player.level(), copy);
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Palier " + level)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(perk.name() + " — " + perk.description())
                .withStyle(ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("★ Palier " + level + " : " + perk.name() + " (" + perk.description()
                + "). Récompense ajoutée à ton inventaire. Touche K : fiche de personnage.").withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1f, 1f);
    }

    /** (Ré)applique les améliorations débloquées (connexion, réapparition, nouveau palier). */
    public void applyPerks(ServerPlayer player) {
        applySkillPerks(player);
        Record r = record(player);
        List<Perk> perks = perks();
        for (int i = 0; i < perks.size(); i++) {
            Perk perk = perks.get(i);
            if (perk.attribute() == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(perk.attribute());
            if (instance == null) {
                continue;
            }
            Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "perk_" + (i + 1));
            if (i < r.level) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(id, perk.amount(), perk.operation()));
            } else {
                instance.removeModifier(id);
            }
        }
    }

    /** Bonus des compétences (attributs) : dégâts, vitesse, chance. */
    private void applySkillPerks(ServerPlayer player) {
        int combat = skillLevel(player, Skill.COMBAT);
        int explore = skillLevel(player, Skill.EXPLORATION);
        skillModifier(player, Attributes.ATTACK_DAMAGE, "skill_combat", 0.02 * combat, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        skillModifier(player, Attributes.MOVEMENT_SPEED, "skill_exploration", 0.005 * explore, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        skillModifier(player, Attributes.LUCK, "skill_luck", 0.1 * explore, AttributeModifier.Operation.ADD_VALUE);
        skillModifier(player, Attributes.MOVEMENT_SPEED, "job_scout", Jobs.is(player, Jobs.Job.ECLAIREUR) ? 0.08 : 0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        skillModifier(player, Attributes.LUCK, "job_scavenger", Jobs.is(player, Jobs.Job.RECUPERATEUR) ? 1.5 : 0,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void skillModifier(ServerPlayer player, Holder<Attribute> attribute, String name, double amount,
                                      AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        if (amount > 0) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        } else {
            instance.removeModifier(id);
        }
    }

    /** Compétence Espace : chance d'économiser l'unité d'oxygène de cette seconde. */
    public boolean saveOxygen(ServerPlayer player) {
        return player.getRandom().nextFloat() < 0.03f * skillLevel(player, Skill.SPACE);
    }

    /** Compétence Mécanique : chance d'économiser l'essence de ce tick. */
    public boolean saveFuel(ServerPlayer player) {
        float chance = 0.02f * skillLevel(player, Skill.MECHANICS) + (Jobs.is(player, Jobs.Job.MECANICIEN) ? 0.25f : 0f);
        return player.getRandom().nextFloat() < chance;
    }

    /** Compétence Combat : durée de rechargement réduite. */
    public int reloadTicks(ServerPlayer player, int base) {
        return Math.max(5, Math.round(base * (1 - 0.02f * skillLevel(player, Skill.COMBAT))));
    }

    /** Palier 3 : l'oxygène dure deux fois plus longtemps. */
    public boolean hasDiscovered(ServerPlayer player, String id) {
        return record(player).discoveries.contains(id);
    }

    public boolean hasSteelLungs(ServerPlayer player) {
        return record(player).level >= 3;
    }

    // --- Suivi automatique (une fois par seconde) ----------------------------------------------------

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        GeoChunkGenerator generator = StartPoints.generator(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) {
                Plans.check(player); // Plans de fusée débloqués par les découvertes et compteurs.
            }
            if (generator == null || player.level() != server.overworld() || player.isSpectator()) {
                continue;
            }
            Record r = record(player);
            double scale = generator.terrain().scale();
            // Zones d'environ 10 km (cases de 0,1°).
            String zone = String.format(Locale.ROOT, "zone:%d,%d",
                    Math.round(WebMercator.latitudeAt(player.getZ(), scale) * 10),
                    Math.round(WebMercator.longitudeAt(player.getX(), scale) * 10));
            if (r.places.add(zone)) {
                count(player, "zones", 1, 5);
            }
            locateAsync(server, generator, player);
        }
        if (server.getTickCount() % 1200 == 0) {
            save();
        }
    }

    private void locateAsync(MinecraftServer server, GeoChunkGenerator generator, ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!locating.add(uuid)) {
            return; // Recherche précédente pas encore terminée.
        }
        int x = player.getBlockX();
        int y = player.getBlockY();
        int z = player.getBlockZ();
        CompletableFuture.supplyAsync(() -> Wasteland.locate(generator.terrain(), x, y, z), locator)
                .whenComplete((place, error) -> server.execute(() -> {
                    locating.remove(uuid);
                    if (error != null) {
                        GeoMod.LOGGER.debug("Recherche de découverte impossible en {}, {}, {}", x, y, z, error);
                        return;
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                    if (place == null || online == null || !record(online).places.add(place)) {
                        return;
                    }
                    boolean bunker = place.startsWith("bunker");
                    online.sendOverlayMessage(Component.literal(bunker ? "Bunker découvert !" : "Cave à monstres découverte !")
                            .withStyle(ChatFormatting.RED));
                    count(online, bunker ? "bunkers" : "caves", 1, bunker ? 25 : 15);
                }));
    }

    void onKill(LivingEntity victim, ServerPlayer killer) {
        if (victim.getType() == ModMobs.MOON_CRAWLER || victim.getType() == ModMobs.LOST_ASTRONAUT) {
            count(killer, "lunar_kills", 1, 3);
        } else if (victim instanceof Enemy) {
            count(killer, "kills", 1, 1);
        }
    }

    // --- Fiche envoyée au client ---------------------------------------------------------------

    void sendSheet(ServerPlayer player) {
        Tutorial.get().mark(player, "sheet");
        Record r = record(player);
        JsonObject sheet = new JsonObject();
        sheet.addProperty("name", player.getName().getString());
        sheet.addProperty("level", r.level);
        sheet.addProperty("points", r.points);
        sheet.addProperty("previous", r.level == 0 ? 0 : THRESHOLDS[r.level - 1]);
        sheet.addProperty("next", r.level < THRESHOLDS.length ? THRESHOLDS[r.level] : -1);
        sheet.addProperty("health", Math.round(player.getMaxHealth()));
        sheet.addProperty("armor", player.getArmorValue());
        JsonArray perkList = new JsonArray();
        List<Perk> perks = perks();
        for (int i = 0; i < perks.size(); i++) {
            JsonObject p = new JsonObject();
            p.addProperty("name", perks.get(i).name());
            p.addProperty("description", perks.get(i).description());
            p.addProperty("threshold", THRESHOLDS[i]);
            p.addProperty("unlocked", i < r.level);
            perkList.add(p);
        }
        sheet.add("perks", perkList);
        JsonArray skills = new JsonArray();
        for (Skill skill : Skill.values()) {
            long xp = r.skills.getOrDefault(skill.name(), 0L);
            int level = levelFor(xp);
            JsonObject o = new JsonObject();
            o.addProperty("name", skill.label);
            o.addProperty("level", level);
            o.addProperty("xp", xp);
            o.addProperty("from", cumulative(level));
            o.addProperty("to", level >= MAX_SKILL_LEVEL ? -1 : cumulative(level + 1));
            o.addProperty("bonus", skill.perLevel);
            skills.add(o);
        }
        sheet.add("skills", skills);
        JsonArray stats = new JsonArray();
        STAT_LABELS.forEach((key, label) -> {
            JsonObject s = new JsonObject();
            s.addProperty("label", label);
            s.addProperty("value", r.stats.getOrDefault(key, 0L));
            stats.add(s);
        });
        sheet.add("stats", stats);
        JsonArray discoveries = new JsonArray();
        for (Discovery d : DISCOVERIES) {
            JsonObject o = new JsonObject();
            o.addProperty("name", d.name());
            o.addProperty("points", d.points());
            o.addProperty("done", r.discoveries.contains(d.id()));
            discoveries.add(o);
        }
        sheet.add("discoveries", discoveries);
        ServerPlayNetworking.send(player, new SheetPayload(sheet.toString()));
    }
}
