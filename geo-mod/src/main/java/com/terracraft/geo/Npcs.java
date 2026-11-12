package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgument;
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
    private record Persona(String name, String role, String faction, String personality, String goal) { }
    private record Turn(String speaker, String text) { }
    private static final class Settings {
        boolean ai_enabled = false;
        String provider = "ollama";
        String endpoint = "http://127.0.0.1:11434/api/chat";
        String model = "mistral";
        String api_key_env = "TERRACRAFT_MISTRAL_API_KEY";
        int timeout_seconds = 15;
        Map<String, Persona> personas = new LinkedHashMap<>(Map.of("lea", new Persona("Léa", "Mécanicienne",
                "Les Refuges", "Pragmatique, chaleureuse, parle de réparation et de solidarité.",
                "Aider le refuge à retrouver des ressources et des moyens de transport.")));
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
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private Settings settings = new Settings();
    private NpcBrain brain;
    private Path memoryFile;
    private MinecraftServer activeServer;
    private int generation;

    Npcs(Contracts contracts, Towns towns) {
        this.contracts = contracts;
        this.towns = towns;
    }

    void wire() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer
                    && entity instanceof Mob mob && persona(mob) != null) {
                talk(serverPlayer, mob, "Bonjour.");
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
            if (settings == null || settings.personas == null || settings.personas.size() > 64) {
                throw new IllegalArgumentException("Identités PNJ invalides");
            }
            for (var entry : settings.personas.entrySet()) {
                Persona p = entry.getValue();
                if (!entry.getKey().matches("[a-z0-9_-]{1,32}") || p == null
                        || !bounded(p.name(), 48) || !bounded(p.role(), 120) || !bounded(p.faction(), 120)
                        || !bounded(p.personality(), 1200) || !bounded(p.goal(), 500)) {
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
            settings = new Settings();
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
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pnj")
                .executes(c -> talkNearest(c.getSource().getPlayerOrException(), "Bonjour."))
                .then(Commands.literal("dire").then(Commands.argument("message", StringArgument.greedyString())
                        .executes(c -> talkNearest(c.getSource().getPlayerOrException(), StringArgument.getString(c, "message")))))
                .then(Commands.literal("mission").executes(c -> mission(c.getSource().getPlayerOrException(), false)))
                .then(Commands.literal("reclamer").executes(c -> mission(c.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("creer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("identite", StringArgument.word()).executes(c -> spawn(
                                c.getSource().getPlayerOrException(), StringArgument.getString(c, "identite")))))
                .then(Commands.literal("lier").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("identite", StringArgument.word())
                                .then(Commands.argument("mob", EntityArgument.entity()).executes(c -> {
                                    var entity = EntityArgument.getEntity(c, "mob");
                                    return entity instanceof Mob mob ? bind(mob, StringArgument.getString(c, "identite")) : 0;
                                }))))
                .then(Commands.literal("recharger").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> { configure(c.getSource().getServer()); c.getSource().sendSuccess(
                                () -> Component.literal("Identités PNJ rechargées."), false); return 1; })));
    }

    private int spawn(ServerPlayer player, String id) {
        if (!settings.personas.containsKey(id)) {
            player.sendSystemMessage(Component.literal("Identité inconnue : " + id));
            return 0;
        }
        var mob = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
        if (mob == null) return 0;
        var at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
        mob.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        bind(mob, id);
        player.level().addFreshEntity(mob);
        GeoMod.LOGGER.info("[PNJ] identité {} créée par {}", id, player.getName().getString());
        return 1;
    }

    private int bind(Mob mob, String id) {
        Persona p = settings.personas.get(id);
        if (p == null) return 0;
        new ArrayList<>(mob.getTags()).stream().filter(tag -> tag.startsWith(TAG)).forEach(mob::removeTag);
        mob.addTag(TAG + id);
        mob.setCustomName(Component.literal(p.name() + " · " + p.role()));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        return 1;
    }

    private Persona persona(Mob mob) {
        return mob.getTags().stream().filter(tag -> tag.startsWith(TAG))
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
        context.add("available_quests", available);
        String fallbackQuest = Contracts.todays().stream().map(c -> Contracts.PREFIX + c.id()).filter(ids::contains).findFirst().orElse("");
        NpcPolicy.Reply fallback = new NpcPolicy.Reply("Bonjour, " + player.getName().getString()
                + ". Je suis " + p.name() + ". " + (fallbackQuest.isEmpty() ? "Tu as terminé les contrats du jour."
                : "J'ai du travail pour toi. Consulte /pnj mission."), fallbackQuest);
        if (brain == null) {
            deliver(player, mob, message, fallback);
            return 1;
        }
        UUID token = UUID.randomUUID();
        UUID playerId = player.getUUID();
        pending.put(playerId, token);
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
        save();
        player.sendSystemMessage(Component.literal(persona(mob).name() + " : " + reply.speech()).withStyle(ChatFormatting.GREEN));
        mission(player, mob, false);
    }

    private int mission(ServerPlayer player, boolean claim) {
        Mob mob = nearest(player);
        if (mob == null || !nearby(player, mob)) return 0;
        return mission(player, mob, claim);
    }

    private int mission(ServerPlayer player, Mob mob, boolean claim) {
        Memory memory = memory(player, mob);
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
