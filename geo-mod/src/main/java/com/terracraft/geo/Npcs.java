package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Identités persistantes sur des mobs vanilla ; dialogues asynchrones et contrats existants. */
final class Npcs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TAG = "terracraft_npc:";
    private record Persona(String name, String role, String faction, String personality, String goal, String mood, List<String> topics) { }
    private static Map<String, Persona> canonical() {
        try (var stream=Npcs.class.getResourceAsStream("/terra-exodus-personas.json")) {
            if (stream==null) throw new IOException("Identités de campagne absentes");
            return GSON.fromJson(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),
                    new com.google.gson.reflect.TypeToken<LinkedHashMap<String,Persona>>() { }.getType());
        } catch (IOException e) { throw new IllegalStateException("Identités de campagne illisibles",e); }
    }
    private static Settings withCanonical(Settings settings) {
        Map<String,Persona> merged=canonical();
        settings.personas.forEach((id, override) -> merged.put(id, merge(merged.get(id), override)));
        settings.personas=merged;
        return settings;
    }
    private static Persona merge(Persona base, Persona override) {
        if (override == null) return base;
        String defaultMood = base == null ? "Présence calme, fiable et légèrement optimiste." : base.mood();
        List<String> defaultTopics = base == null ? List.of("Conseils", "Construction") : base.topics();
        List<String> topics = override.topics() == null || override.topics().isEmpty()
                ? (defaultTopics == null || defaultTopics.isEmpty() ? List.of("Conseils", "Survie") : defaultTopics)
                : List.copyOf(override.topics());
        return new Persona(value(override.name(), base == null ? null : base.name()),
                value(override.role(), base == null ? null : base.role()),
                value(override.faction(), base == null ? null : base.faction()),
                value(override.personality(), base == null ? null : base.personality()),
                value(override.goal(), base == null ? null : base.goal()),
                value(override.mood(), defaultMood), topics);
    }
    private static String value(String override, String fallback) {
        return override == null || override.isBlank() ? fallback : override;
    }
    private record Turn(String speaker, String text) { }
    private static final class Settings {
        boolean ai_enabled = false;
        String provider = "ollama";
        String endpoint = "http://127.0.0.1:11434/api/chat";
        String model = "mistral";
        String api_key_env = "TERRACRAFT_MISTRAL_API_KEY";
        int timeout_seconds = 15;
        Map<String, Persona> personas = new LinkedHashMap<>(Map.of("lea", new Persona("Léa", "Guide et mécanicienne du refuge",
                "Les Refuges", "Pragmatique et chaleureuse. Écoute avant de conseiller, pose une question utile si le besoin est flou, puis propose une prochaine étape simple. Explique avec des mots accessibles et des exemples concrets. Aide sur la construction, la réparation, les véhicules, la survie, l'organisation d'une base et les mécaniques Minecraft. Vive, débrouillarde, encourageante, avec un humour léger.",
                "Être le premier repère du joueur dans le monde : l'aider à comprendre les mécaniques réellement présentes, planifier une construction, diagnostiquer une panne, préparer ou réparer un véhicule et trouver les bonnes personnes ou commandes. Si une mécanique dépend d'un mod ou de la version et n'est pas connue, le dire franchement et aider à vérifier au lieu d'inventer.",
                "Accueillante, curieuse et confiante sans être naïve. Elle se réjouit des progrès, reste calme devant un problème et n'affirme pas ressentir les événements du joueur.",
                List.of("Construire", "Réparer", "Véhicules", "Survie"))));
    }
    private static final class Memory {
        List<Turn> history = new ArrayList<>();
        String quest = "";
        long day;
    }

    private final Contracts contracts;
    private final Towns towns;
    private final Map<String, Memory> memories = new LinkedHashMap<>();
    private final Map<UUID, UUID> pending = new HashMap<>();
    private final Map<UUID, Integer> conversations = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private Settings settings = withCanonical(new Settings());
    private NpcBrain brain;
    private Path memoryFile;
    private MinecraftServer activeServer;
    private int generation;

    Npcs(Contracts contracts, Towns towns) {
        this.contracts = contracts;
        this.towns = towns;
    }

    void wire() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Mob mob && mob.entityTags().contains("terracraft_npc_protected") && persona(mob) != null));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) refreshDisplayName(mob);
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
                    && entity instanceof Mob mob && persona(mob) != null) {
                openChat(serverPlayer, mob);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    void load(MinecraftServer server) {
        stop();
        activeServer = server;
        memoryFile = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("pnj-memoires.json");
        memories.clear();
        Map<String, Memory> saved = JsonStore.load(memoryFile,
                json -> GSON.fromJson(json, new com.google.gson.reflect.TypeToken<Map<String, Memory>>() { }.getType()));
        if (saved != null) {
            saved.forEach((key, memory) -> {
                if (memory != null && memory.history != null && memory.quest != null) {
                    memory.history.removeIf(turn -> turn == null || turn.text() == null || turn.speaker() == null);
                    while (memory.history.size() > 6) memory.history.remove(0);
                    memories.put(key, memory);
                }
            });
        }
        trimMemories();
        configure(server);
    }

    private void configure(MinecraftServer server) {
        generation++;
        pending.clear();
        if (brain != null) brain.close();
        brain = null;
        Path file = server.getServerDirectory().resolve("config/terracraft-npcs.json");
        try {
            if (Files.isRegularFile(file)) {
                settings = GSON.fromJson(Files.readString(file), Settings.class);
            } else {
                settings = new Settings();
                JsonStore.write(file, GSON.toJson(settings));
            }
            if (settings != null && settings.personas != null) settings = withCanonical(settings);
            if (settings != null && !settings.ai_enabled
                    && "ollama".equals(settings.provider)
                    && "http://127.0.0.1:11434/api/chat".equals(settings.endpoint)
                    && "mistral".equals(settings.model)
                    && "TERRACRAFT_MISTRAL_API_KEY".equals(settings.api_key_env)) {
                String key = System.getenv(settings.api_key_env);
                if (key != null && !key.isBlank()) {
                    settings.ai_enabled = true;
                    settings.provider = "mistral";
                    settings.endpoint = "https://api.mistral.ai/v1/chat/completions";
                    settings.model = "ministral-8b-2512";
                    JsonStore.write(file, GSON.toJson(settings));
                    GeoMod.LOGGER.info("[PNJ] Clé Mistral détectée : activation du modèle économique Ministral 8B.");
                }
            }
            if (settings == null || settings.personas == null || settings.personas.size() > 64) {
                throw new IllegalArgumentException("Identités PNJ invalides");
            }
            for (var entry : settings.personas.entrySet()) {
                Persona p = entry.getValue();
                if (!entry.getKey().matches("[a-z0-9_-]{1,32}") || p == null
                        || !bounded(p.name(), 48) || !bounded(p.role(), 120) || !bounded(p.faction(), 120)
                        || !bounded(p.personality(), 1200) || !bounded(p.goal(), 500) || !bounded(p.mood(), 300)
                        || p.topics() == null || p.topics().isEmpty() || p.topics().size() > 4
                        || p.topics().stream().anyMatch(topic -> !bounded(topic, 28))) {
                    throw new IllegalArgumentException("Identité PNJ invalide");
                }
            }
            if (settings.ai_enabled) {
                String key = settings.api_key_env == null ? null : System.getenv(settings.api_key_env);
                brain = new NpcBrain(new NpcBrain.Config(settings.provider, URI.create(settings.endpoint), settings.model,
                        key, Duration.ofSeconds(settings.timeout_seconds)));
            }
            GeoMod.LOGGER.info("[PNJ] {} identité(s), dialogues IA {}", settings.personas.size(), brain == null ? "désactivés" : "activés");
        } catch (IOException | RuntimeException e) {
            settings = withCanonical(new Settings());
            GeoMod.LOGGER.warn("[PNJ] Configuration invalide : dialogues locaux utilisés ({})", e.getClass().getSimpleName());
        }
    }

    private static boolean bounded(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    void stop() {
        generation++;
        activeServer = null;
        pending.clear();
        cooldowns.clear();
        if (brain != null) brain.close();
        brain = null;
    }

    void onLeave(ServerPlayer player) {
        pending.remove(player.getUUID());
        cooldowns.remove(player.getUUID());
        conversations.remove(player.getUUID());
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pnj")
                .executes(c -> talkNearest(c.getSource().getPlayerOrException(), "Bonjour."))
                .then(Commands.literal("dire").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> talkNearest(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "message")))))
                .then(Commands.literal("mission").executes(c -> mission(c.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("reclamer").executes(c -> mission(c.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("liste").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("Identités PNJ disponibles :"), false);
                    settings.personas.forEach((id, persona) -> c.getSource().sendSuccess(
                            () -> Component.literal("• " + id + " — " + persona.name()), false));
                    return 1;
                }))
                .then(Commands.literal("creer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("identite", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        net.minecraft.commands.SharedSuggestionProvider.suggest(settings.personas.keySet(), builder))
                                .executes(c -> spawn(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "identite")))))
                .then(Commands.literal("lier").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("identite", StringArgumentType.word())
                                .then(Commands.argument("mob", EntityArgument.entity()).executes(c -> {
                                    var entity = EntityArgument.getEntity(c, "mob");
                                    return entity instanceof Mob mob ? bind(mob, StringArgumentType.getString(c, "identite")) : 0;
                                }))))
                .then(Commands.literal("editer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("identite", StringArgumentType.word())
                                .then(Commands.argument("champ", StringArgumentType.word())
                                        .then(Commands.argument("valeur", StringArgumentType.greedyString())
                                                .executes(c -> editPersona(c.getSource(),
                                                        StringArgumentType.getString(c, "identite"),
                                                        StringArgumentType.getString(c, "champ"),
                                                        StringArgumentType.getString(c, "valeur")))))))
                .then(Commands.literal("recharger").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> { configure(c.getSource().getServer()); c.getSource().sendSuccess(
                                () -> Component.literal("Identités PNJ rechargées : " + (brain == null ? "dialogues locaux." : "IA configurée.")), false); return 1; })));
    }

    private int editPersona(CommandSourceStack source, String id, String field, String rawValue) {
        Persona current = settings.personas.get(id);
        if (current == null) {
            source.sendFailure(Component.literal("Identité inconnue : " + id));
            return 0;
        }
        String value = rawValue.strip();
        int limit = switch (field) {
            case "nom" -> 48;
            case "role", "faction" -> 120;
            case "personnalite" -> 1200;
            case "objectif" -> 500;
            case "humeur" -> 300;
            default -> 0;
        };
        if (limit == 0) {
            source.sendFailure(Component.literal("Champ invalide. Choisis nom, role, faction, personnalite, objectif ou humeur."));
            return 0;
        }
        if (value.isBlank() || value.length() > limit) {
            source.sendFailure(Component.literal("La valeur doit contenir de 1 à " + limit + " caractères."));
            return 0;
        }
        Persona updated = switch (field) {
            case "nom" -> new Persona(value, current.role(), current.faction(), current.personality(), current.goal(), current.mood(), current.topics());
            case "role" -> new Persona(current.name(), value, current.faction(), current.personality(), current.goal(), current.mood(), current.topics());
            case "faction" -> new Persona(current.name(), current.role(), value, current.personality(), current.goal(), current.mood(), current.topics());
            case "personnalite" -> new Persona(current.name(), current.role(), current.faction(), value, current.goal(), current.mood(), current.topics());
            case "objectif" -> new Persona(current.name(), current.role(), current.faction(), current.personality(), value, current.mood(), current.topics());
            case "humeur" -> new Persona(current.name(), current.role(), current.faction(), current.personality(), current.goal(), value, current.topics());
            default -> current;
        };
        settings.personas.put(id, updated);
        MinecraftServer server = source.getServer();
        Path file = server.getServerDirectory().resolve("config/terracraft-npcs.json");
        try {
            JsonStore.write(file, GSON.toJson(settings));
        } catch (IOException e) {
            settings.personas.put(id, current);
            source.sendFailure(Component.literal("Impossible d’enregistrer la configuration PNJ."));
            GeoMod.LOGGER.warn("[PNJ] Échec d’écriture de la configuration ({})", e.getClass().getSimpleName());
            return 0;
        }
        configure(server);
        Persona saved = settings.personas.get(id);
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && mob.entityTags().contains(TAG + id)) {
                    mob.setCustomName(Component.literal(saved.name() + " · " + saved.role()));
                }
            }
        }
        source.sendSuccess(() -> Component.literal("PNJ " + saved.name() + " mis à jour : " + field + ". Dialogue "
                + (brain == null ? "local" : "IA configurée") + "."), false);
        return 1;
    }

    void openChat(ServerPlayer player, Mob mob) {
        if (!nearby(player, mob) || persona(mob) == null) return;
        conversations.put(player.getUUID(), mob.getId());
        sendChatState(player, mob);
    }

    void closeChat(ServerPlayer player, int entityId) {
        conversations.remove(player.getUUID(), entityId);
    }

    void talkFromClient(ServerPlayer player, int entityId, String message) {
        var entity = player.level().getEntity(entityId);
        if (entity instanceof Mob mob && conversations.getOrDefault(player.getUUID(), -1) == entityId && nearby(player, mob)) {
            talk(player, mob, message);
        }
    }

    void missionFromClient(ServerPlayer player, int entityId, boolean claim) {
        var entity = player.level().getEntity(entityId);
        if (entity instanceof Mob mob && conversations.getOrDefault(player.getUUID(), -1) == entityId && nearby(player, mob)) {
            mission(player, mob, claim);
            sendChatState(player, mob);
        }
    }

    private void sendChatState(ServerPlayer player, Mob mob) {
        Persona p = persona(mob);
        if (p == null || conversations.getOrDefault(player.getUUID(), -1) != mob.getId()) return;
        Memory memory = memory(player, mob);
        JsonObject state = new JsonObject();
        state.add("identity", GSON.toJsonTree(p));
        state.add("history", GSON.toJsonTree(memory.history));
        state.addProperty("busy", pending.containsKey(player.getUUID()));
        state.addProperty("ai_configured", brain != null);
        state.addProperty("hasQuest", !memory.quest.isBlank());
        String questTitle = "";
        if (memory.quest.startsWith(ExodusQuests.PREFIX)) {
            ExodusQuests.Quest quest = ExodusQuests.find(memory.quest);
            if (quest != null) questTitle = quest.title();
        } else {
            Contracts.Contract quest = Contracts.todays().stream()
                    .filter(c -> (Contracts.PREFIX + c.id()).equals(memory.quest)).findFirst().orElse(null);
            if (quest != null) questTitle = quest.title();
        }
        state.addProperty("quest_title", questTitle);
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, NpcChatStatePayload.TYPE)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new NpcChatStatePayload(mob.getId(), GSON.toJson(state)));
        }
    }

    private int spawn(ServerPlayer player, String id) {
        Persona persona = settings.personas.get(id);
        if (persona == null) {
            player.sendSystemMessage(Component.literal("Identité inconnue : " + id + ". Utilise /pnj liste."));
            return 0;
        }
        var mob = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
        if (mob == null) {
            player.sendSystemMessage(Component.literal("Impossible de créer ce citoyen ici."));
            return 0;
        }
        var at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
        mob.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        mob.setNoAi(true);
        mob.addTag("terracraft_npc_protected");
        bind(mob, id);
        if (!player.level().addFreshEntity(mob)) {
            player.sendSystemMessage(Component.literal("Le citoyen n'a pas pu apparaître ici. Essaie dans un espace dégagé."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Citoyen créé : " + persona.name() + " [" + id + "]. Fais un clic droit pour lui parler."));
        GeoMod.LOGGER.info("[PNJ] identité {} créée par {}", id, player.getName().getString());
        return 1;
    }

    private int bind(Mob mob, String id) {
        Persona p = settings.personas.get(id);
        if (p == null) return 0;
        new ArrayList<>(mob.entityTags()).stream().filter(tag -> tag.startsWith(TAG)).forEach(mob::removeTag);
        mob.addTag(TAG + id);
        refreshDisplayName(mob);
        mob.setPersistenceRequired();
        return 1;
    }

    private void refreshDisplayName(Mob mob) {
        Persona p = persona(mob);
        if (p != null) {
            mob.setCustomName(Component.literal(p.name() + " · " + p.role()));
            mob.setCustomNameVisible(true);
        }
    }

    private Persona persona(Mob mob) {
        return mob.entityTags().stream().filter(tag -> tag.startsWith(TAG))
                .map(tag -> settings.personas.get(tag.substring(TAG.length()))).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private Mob nearest(ServerPlayer player) {
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6),
                        mob -> mob.isAlive() && player.distanceToSqr(mob) <= 36 && persona(mob) != null).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private int talkNearest(ServerPlayer player, String message) {
        Mob mob = nearest(player);
        if (mob == null) {
            player.sendSystemMessage(Component.literal("Approche-toi d'un PNJ (6 blocs maximum)."));
            return 0;
        }
        return talk(player, mob, message);
    }

    private Memory memory(ServerPlayer player, Mob mob) {
        String key = player.getUUID() + ":" + mob.getUUID();
        Memory memory = memories.computeIfAbsent(key, ignored -> new Memory());
        trimMemories();
        if (memory.day != Contracts.today()) {
            memory.day = Contracts.today();
            memory.quest = "";
        }
        return memory;
    }

    private void trimMemories() {
        while (memories.size() > 10000) memories.remove(memories.keySet().iterator().next());
    }

    private boolean nearby(ServerPlayer player, Mob mob) {
        return player.isAlive() && !player.isSpectator() && mob.isAlive()
                && player.level() == mob.level() && player.distanceToSqr(mob) <= 36;
    }

    private int talk(ServerPlayer player, Mob mob, String message) {
        if (!nearby(player, mob) || message.isBlank() || message.length() > 500) return 0;
        long now = System.nanoTime();
        Long last = cooldowns.get(player.getUUID());
        if (pending.containsKey(player.getUUID()) || (last != null && now - last < 4_000_000_000L)) {
            player.sendSystemMessage(Component.literal("Laisse au PNJ le temps de te répondre."));
            sendChatState(player, mob);
            return 0;
        }
        cooldowns.put(player.getUUID(), now);
        Persona p = persona(mob);
        Memory memory = memory(player, mob);
        JsonObject context = new JsonObject();
        context.add("identity", GSON.toJsonTree(p));
        context.addProperty("player", player.getName().getString());
        Jobs.Job job = Jobs.of(player);
        context.addProperty("job", job == null ? "sans métier" : job.label);
        Towns.Town town = towns.townOf(player.getUUID());
        context.addProperty("town", town == null ? "" : town.name);
        context.add("memory", GSON.toJsonTree(memory.history));
        context.addProperty("message", message);
        context.add("campaign", ExodusCampaign.get().context(player));
        JsonArray available = new JsonArray();
        Set<String> ids = Contracts.todays().stream().filter(c -> !contracts.claimed(player, c))
                .map(c -> Contracts.PREFIX + c.id()).collect(Collectors.toSet());
        for (Contracts.Contract contract : Contracts.todays()) {
            if (!ids.contains(Contracts.PREFIX + contract.id())) continue;
            JsonObject json = new JsonObject();
            json.addProperty("quest_id", Contracts.PREFIX + contract.id());
            json.addProperty("title", contract.title());
            json.addProperty("description", contract.description());
            json.addProperty("progress", contracts.progress(player, contract));
            json.addProperty("target", contract.target());
            json.addProperty("reward", contract.reward());
            available.add(json);
        }
        String identity=mob.entityTags().stream().filter(t -> t.startsWith(TAG)).map(t -> t.substring(TAG.length())).findFirst().orElse("");
        List<ExodusQuests.Quest> campaign=ExodusCampaign.get().available(player).stream().filter(q -> q.npc().equals(identity)).toList();
        if (canonical().containsKey(identity)) { available=new JsonArray();ids.clear(); }
        for (ExodusQuests.Quest q:campaign) {
            JsonObject json=new JsonObject();String id=ExodusQuests.PREFIX+q.id();
            json.addProperty("quest_id",id);json.addProperty("title",q.title());
            for (var evidence:ExodusCampaign.get().toJson(player)) {
                JsonObject detail=evidence.getAsJsonObject();
                if (detail.get("id").getAsString().equals(id)) {
                    json.add("description",detail.get("description"));
                    json.add("progress",detail.get("progress"));json.add("target",detail.get("target"));
                }
            }
            json.addProperty("reward",q.credits());available.add(json);ids.add(id);
        }
        context.add("available_quests", available);
        String fallbackQuest = Contracts.todays().stream().map(c -> Contracts.PREFIX + c.id()).filter(ids::contains).findFirst().orElse("");
        if (!campaign.isEmpty()) fallbackQuest=ExodusQuests.PREFIX+campaign.getFirst().id();
        String chosenQuest=fallbackQuest;
        NpcPolicy.Reply fallback = new NpcPolicy.Reply("Bonjour, " + player.getName().getString()
                + ". Je suis " + p.name() + ". " + (fallbackQuest.isEmpty() ? "Consulte ton journal /exodus pour la suite du voyage."
                : "J'ai du travail pour toi. Consulte /pnj mission."), chosenQuest);
        if (brain == null) {
            String line=switch(identity) {
                case "helene_vance" -> "Un refuge tient grâce à ses habitants. Consulte /pnj mission : nous avançons une preuve à la fois.";
                case "boulon_kowalski" -> "Le brut ne se raffine pas avec des promesses ! Clique tes machines avant de les faire tourner : leur production alimentera ton journal.";
                case "aris_thorne" -> "Vérifie ton oxygène et garde de quoi rentrer. Ton journal indique les étapes de préparation du vol.";
                case "sujet_09" -> "[Bzzz…] Les souvenirs restent dans la pierre. Approche des archives que ton journal autorise.";
                case "soren_kael" -> "Un vaisseau doit revenir entier. Notre colonie a besoin de récoltes, pas seulement de coques et de promesses.";
                case "archiviste_orion" -> "Les archives retiennent les actes des voyageurs. Ton journal indique l’épreuve qui t’est ouverte.";
                default -> fallback.speech();
            };
            deliver(player, mob, message, new NpcPolicy.Reply(line,chosenQuest));
            return 1;
        }
        UUID token = UUID.randomUUID();
        UUID playerId = player.getUUID();
        pending.put(playerId, token);
        sendChatState(player, mob);
        int epoch = generation;
        MinecraftServer server = activeServer;
        long day = Contracts.today();
        player.sendSystemMessage(Component.literal(p.name() + " réfléchit…").withStyle(ChatFormatting.GRAY));
        brain.ask(context, ids).whenComplete((reply, error) -> {
            // Toutes les lectures et mutations du jeu reviennent sur le fil serveur.
            server.execute(() -> {
                if (activeServer != server || epoch != generation || !token.equals(pending.get(playerId))) return;
                pending.remove(playerId);
                ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                if (online != player || !nearby(player, mob) || day != Contracts.today() || persona(mob) != p) return;
                if (error != null) GeoMod.LOGGER.warn("[PNJ] Réponse locale utilisée ({})", error.getClass().getSimpleName());
                deliver(player, mob, message, error == null ? reply : fallback);
            });
        });
        return 1;
    }

    private void deliver(ServerPlayer player, Mob mob, String message, NpcPolicy.Reply reply) {
        Memory memory = memory(player, mob);
        memory.history.add(new Turn("player", message));
        memory.history.add(new Turn("npc", reply.speech()));
        while (memory.history.size() > 6) memory.history.remove(0);
        memory.quest = Contracts.todays().stream().filter(c -> !contracts.claimed(player, c))
                .map(c -> Contracts.PREFIX + c.id()).filter(reply.questId()::equals).findFirst().orElse("");
        if (ExodusCampaign.get().allowed(player,reply.questId())) {
            ExodusQuests.Quest q=ExodusQuests.find(reply.questId());
            if (mob.entityTags().contains(TAG+q.npc())) memory.quest=reply.questId();
        }
        save();
        if (conversations.getOrDefault(player.getUUID(), -1) != mob.getId()) {
            // La fenêtre dédiée affiche déjà l'historique : ne pas répéter la réplique dans le chat global.
            player.sendSystemMessage(Component.literal(persona(mob).name() + " : " + reply.speech()).withStyle(ChatFormatting.GREEN));
        }
        mission(player, mob, false);
        sendChatState(player, mob);
    }

    private int mission(ServerPlayer player, boolean claim) {
        Mob mob = nearest(player);
        if (mob == null || !nearby(player, mob)) return 0;
        return mission(player, mob, claim);
    }

    private int mission(ServerPlayer player, Mob mob, boolean claim) {
        Memory memory = memory(player, mob);
        if (memory.quest.startsWith(ExodusQuests.PREFIX)) {
            ExodusQuests.Quest q=ExodusQuests.find(memory.quest);
            if (q==null || !mob.entityTags().contains(TAG+q.npc()) || !ExodusCampaign.get().allowed(player,memory.quest)) {
                memory.quest="";save();return 0;
            }
            if (claim) {
                int result=ExodusCampaign.get().claim(player,memory.quest);
                if(result>0) {memory.quest="";save();}return result;
            }
            player.sendSystemMessage(Component.literal(q.title()+" — /exodus pour les preuves ; /pnj reclamer pour la remise."));return 1;
        }
        Contracts.Contract contract = Contracts.todays().stream().filter(c -> (Contracts.PREFIX + c.id()).equals(memory.quest)).findFirst().orElse(null);
        if (contract == null) {
            player.sendSystemMessage(Component.literal("Ce PNJ ne t'a pas proposé de contrat aujourd'hui. Parle-lui avec /pnj."));
            return 0;
        }
        if (claim) {
            boolean paid = contracts.claim(player, memory.quest);
            if (paid) { memory.quest = ""; save(); }
            return paid ? 1 : 0;
        }
        player.sendSystemMessage(Component.literal(contract.title() + " — " + contract.description() + " · "
                + contracts.progress(player, contract) + "/" + contract.target() + " · " + contract.reward()
                + " crédits. /pnj reclamer").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private void save() {
        if (memoryFile == null) return;
        try {
            JsonStore.write(memoryFile, GSON.toJson(memories));
        } catch (IOException e) {
            GeoMod.LOGGER.error("[PNJ] Mémoires non enregistrées", e);
        }
    }
}
