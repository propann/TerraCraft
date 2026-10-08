package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Événement d'ouverture : la course à l'espace. Tant qu'elle est ouverte (/terracraft course demarrer), le premier
 * joueur à franchir chaque étape gagne une prime et tout le serveur le voit. /course affiche le tableau. Données :
 * course.json (vainqueurs conservés après la fin de la course).
 */
public final class SpaceRace {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    record Milestone(String id, String label, long reward) {
    }

    static final List<Milestone> MILESTONES = List.of(
            new Milestone("orbit", "Premier en orbite", 200),
            new Milestone("orbital_station", "Première station orbitale", 300),
            new Milestone("moon", "Premier pas sur la Lune", 300),
            new Milestone("lunar_base", "Première base lunaire", 300),
            new Milestone("alien_sanctuary", "Premier sanctuaire extraterrestre", 200),
            new Milestone("mars", "Premier sur Mars", 500));

    static final class Winner {
        String name;
        String uuid;
        String date;
        long reward;
    }

    static final class State {
        boolean active;
        Map<String, Winner> winners = new LinkedHashMap<>();
    }

    private static SpaceRace instance;
    private final AuctionHouse bank;
    private State state = new State();
    private Path file;
    private MinecraftServer server;

    SpaceRace(AuctionHouse bank) {
        this.bank = bank;
        instance = this;
    }

    void load(MinecraftServer server) {
        this.server = server;
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("course.json");
        State stored = JsonStore.load(file, json -> GSON.fromJson(json, State.class));
        state = stored == null ? new State() : stored;
        if (state.winners == null) {
            state.winners = new LinkedHashMap<>();
        }
    }

    private void save() {
        try {
            JsonStore.write(file, GSON.toJson(state));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    /** Étape franchie par un joueur (découverte, station déployée…) : s'il est le premier, il gagne. */
    public static void reached(ServerPlayer player, String id) {
        if (instance != null) {
            instance.onReached(player, id);
        }
    }

    private void onReached(ServerPlayer player, String id) {
        if (!state.active || state.winners.containsKey(id)) {
            return;
        }
        Milestone milestone = MILESTONES.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        if (milestone == null) {
            return;
        }
        Winner winner = new Winner();
        winner.name = player.getName().getString();
        winner.uuid = player.getUUID().toString();
        winner.date = LocalDateTime.now().format(DATE);
        winner.reward = milestone.reward();
        state.winners.put(id, winner);
        save();
        bank.credit(player, milestone.reward(), "course à l'espace : " + milestone.label());
        GeoMod.LOGGER.info("[COURSE] {} : {} (+{} crédits)", milestone.label(), winner.name, milestone.reward());
        for (ServerPlayer online : player.level().getServer().getPlayerList().getPlayers()) {
            online.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            online.connection.send(new ClientboundSetTitleTextPacket(Component.literal(milestone.label())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            online.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(winner.name + " remporte l'étape · +"
                    + milestone.reward() + " crédits").withStyle(ChatFormatting.WHITE)));
            online.level().playSound(null, online.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8f, 1f);
        }
        long left = MILESTONES.stream().filter(m -> !state.winners.containsKey(m.id())).count();
        player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("🚀 Course à l'espace : " + winner.name
                + " — " + milestone.label() + " ! " + (left == 0 ? "La course est terminée !" : left + " étape(s) restante(s), /course"))
                .withStyle(ChatFormatting.GOLD), false);
        if (left == 0) {
            state.active = false;
            save();
        }
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("course").executes(c -> board(c.getSource())));
    }

    /** Sous-commandes d'administration, accrochées à /terracraft course. */
    int start(CommandSourceStack source) {
        state.active = true;
        save();
        GeoMod.LOGGER.info("[COURSE] ouverte par {}", source.getTextName());
        source.getServer().getPlayerList().broadcastSystemMessage(Component.literal("🚀 La COURSE À L'ESPACE est ouverte ! "
                + "Le premier à chaque étape gagne une prime : orbite, station, Lune, base lunaire, sanctuaire, Mars. /course")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        return 1;
    }

    int stop(CommandSourceStack source) {
        state.active = false;
        save();
        GeoMod.LOGGER.info("[COURSE] fermée par {}", source.getTextName());
        source.sendSuccess(() -> Component.literal("Course à l'espace fermée (les vainqueurs restent au tableau)."), true);
        return 1;
    }

    private int board(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("🚀 Course à l'espace — " + (state.active ? "en cours" : "fermée"))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Milestone m : MILESTONES) {
            Winner w = state.winners.get(m.id());
            source.sendSuccess(() -> Component.literal((w == null ? "  ○ " : "  ★ ") + m.label() + " — "
                    + (w == null ? m.reward() + " crédits à gagner" : w.name + " (" + w.date + ")"))
                    .withStyle(w == null ? ChatFormatting.GRAY : ChatFormatting.YELLOW), false);
        }
        return 1;
    }
}
