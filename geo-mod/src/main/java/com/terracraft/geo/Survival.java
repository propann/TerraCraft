package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Règles de survie du serveur : on garde son inventaire en mourant mais on perd 25 % de son
 * expérience ; téléportations entre joueurs (/tpa), maison (/sethome, /home) et retour au
 * lieu de la mort (/back).
 */
public final class Survival {
    /** Part de l'expérience perdue à chaque mort. */
    static final double DEATH_XP_LOSS = 0.25;
    private static final long REQUEST_TIMEOUT_MS = 60_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Place(String dimension, double x, double y, double z, float yaw) {
    }

    private record Request(UUID from, long time) {
    }

    private final Map<UUID, Place> homes = new HashMap<>();
    private final Map<UUID, Place> deaths = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();
    /** Une demande /tpa toutes les 10 s par joueur : pas de spam de sons et de messages. */
    private final RateLimit tpaRate = new RateLimit(10_000);
    private Path file;

    void load(MinecraftServer server) {
        server.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("homes.json");
        homes.clear();
        Map<String, Place> stored = JsonStore.load(file,
                json -> GSON.fromJson(json, new TypeToken<Map<String, Place>>() { }.getType()));
        if (stored != null) {
            stored.forEach((uuid, place) -> homes.put(UUID.fromString(uuid), place));
        }
    }

    private void saveHomes() {
        Map<String, Place> stored = new HashMap<>();
        homes.forEach((uuid, place) -> stored.put(uuid.toString(), place));
        try {
            JsonStore.write(file, GSON.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    boolean hasHome(ServerPlayer player) {
        return homes.containsKey(player.getUUID());
    }

    // --- Mort ---------------------------------------------------------------------------------

    void onDeath(ServerPlayer player) {
        deaths.put(player.getUUID(), here(player));
    }

    /** Après la réapparition : l'inventaire est conservé, l'expérience amputée de 25 %. */
    void onRespawn(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
        if (alive) {
            return;
        }
        Progression.get().onDeath(player);
        int total = totalExperience(oldPlayer.experienceLevel, oldPlayer.experienceProgress);
        int kept = (int) Math.floor(total * (1 - DEATH_XP_LOSS));
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.giveExperiencePoints(kept);
        if (total > 0) {
            player.sendSystemMessage(Component.literal("Tu as gardé ton inventaire mais perdu " + Math.round(DEATH_XP_LOSS * 100)
                    + " % de ton expérience (et 10 % de la progression de tes compétences) (niveau " + oldPlayer.experienceLevel + " → " + player.experienceLevel
                    + "). /back pour retourner où tu es mort.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Points d'expérience totaux pour un niveau et une progression (formules vanilla). */
    static int totalExperience(int level, float progress) {
        double base;
        int next;
        if (level <= 16) {
            base = level * level + 6.0 * level;
            next = 2 * level + 7;
        } else if (level <= 31) {
            base = 2.5 * level * level - 40.5 * level + 360;
            next = 5 * level - 38;
        } else {
            base = 4.5 * level * level - 162.5 * level + 2220;
            next = 9 * level - 158;
        }
        return (int) Math.round(base + progress * next);
    }

    // --- Commandes ------------------------------------------------------------------------------

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("joueur", EntityArgument.player()).executes(c ->
                        request(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur")))));
        dispatcher.register(Commands.literal("tpaccept").executes(c -> answer(c.getSource().getPlayerOrException(), true)));
        dispatcher.register(Commands.literal("tpdeny").executes(c -> answer(c.getSource().getPlayerOrException(), false)));
        dispatcher.register(Commands.literal("sethome").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            homes.put(player.getUUID(), here(player));
            saveHomes();
            player.sendSystemMessage(Component.literal("Maison enregistrée ici. /home pour y revenir.").withStyle(ChatFormatting.GREEN));
            return 1;
        }));
        dispatcher.register(Commands.literal("home").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (inCombat(player)) {
                return 0;
            }
            return go(player, homes.get(player.getUUID()), "Pas encore de maison : /sethome là où tu veux revenir.", "Bienvenue chez toi.");
        }));
        dispatcher.register(Commands.literal("back").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            if (inCombat(player)) {
                return 0;
            }
            return go(player, deaths.get(player.getUUID()), "Aucun lieu de mort enregistré.", "Retour au lieu de ta mort.");
        }));
    }

    void onLeave(ServerPlayer player) {
        requests.remove(player.getUUID());
        requests.values().removeIf(request -> request.from().equals(player.getUUID()));
        tpaRate.forget(player);
    }

    /** Blessé par une créature ou un joueur dans les 5 dernières secondes. */
    private static boolean inCombat(ServerPlayer player) {
        if (player.getLastHurtByMob() == null) {
            return false;
        }
        player.sendSystemMessage(Component.literal("Impossible en plein combat : attends quelques secondes sans être touché.")
                .withStyle(ChatFormatting.RED));
        return true;
    }

    private int request(ServerPlayer from, ServerPlayer to) {
        if (from == to) {
            from.sendSystemMessage(Component.literal("Tu ne peux pas te téléporter à toi-même.").withStyle(ChatFormatting.RED));
            return 0;
        }
        long wait = tpaRate.remainingMs(from);
        if (wait > 0) {
            from.sendSystemMessage(Component.literal("Attends " + (wait + 999) / 1000 + " s avant une nouvelle demande.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        tpaRate.mark(from);
        requests.put(to.getUUID(), new Request(from.getUUID(), System.currentTimeMillis()));
        from.sendSystemMessage(Component.literal("Demande envoyée à " + to.getName().getString() + " (60 s).").withStyle(ChatFormatting.GRAY));
        to.sendSystemMessage(Component.literal(from.getName().getString() + " veut se téléporter à toi. ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Accepter]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/tpaccept"))))
                .append(Component.literal(" "))
                .append(Component.literal("[Refuser]").withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand("/tpdeny")))));
        to.level().playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
        return 1;
    }

    private int answer(ServerPlayer target, boolean accept) throws CommandSyntaxException {
        Request request = requests.remove(target.getUUID());
        ServerPlayer from = request == null ? null : target.level().getServer().getPlayerList().getPlayer(request.from());
        if (from == null || System.currentTimeMillis() - request.time() > REQUEST_TIMEOUT_MS) {
            target.sendSystemMessage(Component.literal("Aucune demande de téléportation en attente.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (!accept) {
            from.sendSystemMessage(Component.literal(target.getName().getString() + " a refusé ta demande.").withStyle(ChatFormatting.RED));
            target.sendSystemMessage(Component.literal("Demande refusée.").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        if (inCombat(from)) {
            target.sendSystemMessage(Component.literal(from.getName().getString() + " est en combat : téléportation annulée.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        go(from, here(target), "", "Téléporté à " + target.getName().getString() + ".");
        target.sendSystemMessage(Component.literal(from.getName().getString() + " t'a rejoint.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static Place here(ServerPlayer player) {
        return new Place(player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot());
    }

    private static int go(ServerPlayer player, Place place, String missing, String done) {
        if (place == null) {
            player.sendSystemMessage(Component.literal(missing).withStyle(ChatFormatting.GRAY));
            return 0;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(place.dimension()));
        ServerLevel level = player.level().getServer().getLevel(key);
        if (level == null) {
            return 0;
        }
        player.teleportTo(level, place.x(), place.y(), place.z(), Set.of(), place.yaw(), 0, true);
        player.level().playSound(null, place.x(), place.y(), place.z(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1f);
        player.sendSystemMessage(Component.literal(done).withStyle(ChatFormatting.AQUA));
        return 1;
    }
}
