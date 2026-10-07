package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Parcours « Premiers pas » : une suite d'objectifs courts qui apprend la boucle de jeu et les
 * touches (abri, butin, claim, marché, fiche, missions). L'objectif en cours s'affiche à droite
 * de l'écran ; chaque étape rapporte des crédits. Une étape déjà accomplie se valide toute seule.
 */
public final class Tutorial {
    private static final long STEP_REWARD = 50;
    private static final long FINAL_REWARD = 200;

    private record Step(String id, String title, String hint, BiPredicate<Tutorial, ServerPlayer> done) {
    }

    private static final List<Step> STEPS = List.of(
            new Step("home", "Pose ta maison", "Trouve un abri sûr puis tape /sethome. /home t'y ramènera.",
                    (t, p) -> t.survival != null && t.survival.hasHome(p)),
            new Step("loot", "Fouille un coffre", "Les immeubles et les bunkers cachent du butin. Ouvre un coffre jamais fouillé.",
                    (t, p) -> Progression.get().stat(p, "loot") > 0),
            new Step("claim", "Protège ton secteur", "Touche M : carte, clic droit sur ton chunk puis « Claim selected ».",
                    (t, p) -> Claims.claimCount(p) != 0),
            new Step("market", "Visite l'hôtel des ventes", "Touche O puis « Hôtel des ventes » (ou /hdv) : vends ton surplus.",
                    (t, p) -> t.flag(p, "market")),
            new Step("sheet", "Ouvre ta fiche", "Touche K : niveau, compétences, découvertes et paliers.",
                    (t, p) -> t.flag(p, "sheet")),
            new Step("mission", "Réclame une mission", "Touche O puis « Missions » : termine-en une et réclame ta récompense.",
                    (t, p) -> t.missions != null && t.missions.hasClaimedAny(p)));

    private static final Tutorial INSTANCE = new Tutorial();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Saved(int step, Set<String> flags) {
    }

    private final Map<UUID, Integer> steps = new HashMap<>();
    private final Map<UUID, Set<String>> flags = new HashMap<>();
    private final Map<UUID, Integer> sent = new HashMap<>();
    private Path file;
    private Survival survival;
    private Missions missions;
    private AuctionHouse auctionHouse;
    private StartPoints startPoints;

    public static Tutorial get() {
        return INSTANCE;
    }

    void wire(Survival survival, Missions missions, AuctionHouse auctionHouse, StartPoints startPoints) {
        this.survival = survival;
        this.missions = missions;
        this.auctionHouse = auctionHouse;
        this.startPoints = startPoints;
    }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("tutorial.json");
        steps.clear();
        flags.clear();
        sent.clear();
        Map<String, Saved> stored = JsonStore.load(file,
                json -> GSON.fromJson(json, new TypeToken<Map<String, Saved>>() { }.getType()));
        if (stored != null) {
            stored.forEach((uuid, saved) -> {
                steps.put(UUID.fromString(uuid), saved.step());
                flags.put(UUID.fromString(uuid), new HashSet<>(saved.flags() == null ? Set.of() : saved.flags()));
            });
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        Map<String, Saved> stored = new HashMap<>();
        steps.forEach((uuid, step) -> stored.put(uuid.toString(), new Saved(step, flags.getOrDefault(uuid, Set.of()))));
        try {
            JsonStore.write(file, GSON.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tuto")
                .executes(c -> show(c.getSource().getPlayerOrException()))
                .then(Commands.literal("passer").executes(c -> skip(c.getSource().getPlayerOrException()))));
    }

    /** Action faite côté client (écran ouvert…) qui valide une étape. */
    public void mark(ServerPlayer player, String flag) {
        if (flags.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(flag)) {
            save();
        }
    }

    private boolean flag(ServerPlayer player, String flag) {
        return flags.getOrDefault(player.getUUID(), Set.of()).contains(flag);
    }

    void onLeave(ServerPlayer player) {
        sent.remove(player.getUUID());
    }

    /** Une fois par seconde : valide les étapes accomplies et met à jour l'encart du joueur. */
    void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || startPoints == null || startPoints.choice(player.getUUID()) == null) {
                continue; // Pas encore arrivé dans le monde.
            }
            int step = steps.getOrDefault(player.getUUID(), 0);
            while (step < STEPS.size() && STEPS.get(step).done().test(this, player)) {
                step++;
                complete(player, step);
            }
            sync(player, step);
        }
    }

    private void complete(ServerPlayer player, int reached) {
        steps.put(player.getUUID(), reached);
        save();
        Step done = STEPS.get(reached - 1);
        auctionHouse.credit(player, STEP_REWARD, "Premiers pas : " + done.title());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        if (reached == STEPS.size()) {
            auctionHouse.credit(player, FINAL_REWARD, "Parcours « Premiers pas » terminé");
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Survivant prêt")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Explore, commerce… et vise la Lune")
                    .withStyle(ChatFormatting.GRAY)));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1f, 1f);
        } else {
            Step next = STEPS.get(reached);
            player.sendSystemMessage(Component.literal("✦ Objectif suivant : " + next.title() + " — " + next.hint())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    private void sync(ServerPlayer player, int step) {
        Integer previous = sent.put(player.getUUID(), step);
        if (previous != null && previous == step) {
            return;
        }
        if (!ServerPlayNetworking.canSend(player, TutorialPayload.TYPE)) {
            return;
        }
        if (step >= STEPS.size()) {
            ServerPlayNetworking.send(player, new TutorialPayload(STEPS.size(), STEPS.size(), "", ""));
        } else {
            Step current = STEPS.get(step);
            ServerPlayNetworking.send(player, new TutorialPayload(step, STEPS.size(), current.title(), current.hint()));
        }
    }

    private int show(ServerPlayer player) {
        int step = steps.getOrDefault(player.getUUID(), 0);
        if (step >= STEPS.size()) {
            player.sendSystemMessage(Component.literal("Parcours « Premiers pas » terminé. /aide liste toutes les commandes.")
                    .withStyle(ChatFormatting.GREEN));
            return 1;
        }
        player.sendSystemMessage(Component.literal("✦ Premiers pas " + (step + 1) + "/" + STEPS.size())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
            ChatFormatting color = i < step ? ChatFormatting.DARK_GREEN : i == step ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            player.sendSystemMessage(Component.literal((i < step ? "✓ " : i == step ? "➜ " : "· ") + s.title()
                    + (i == step ? " — " + s.hint() : "")).withStyle(color));
        }
        player.sendSystemMessage(Component.literal("[Masquer le parcours]").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/tuto passer"))));
        return 1;
    }

    private int skip(ServerPlayer player) {
        steps.put(player.getUUID(), STEPS.size());
        save();
        sync(player, STEPS.size());
        player.sendSystemMessage(Component.literal("Parcours « Premiers pas » masqué. /aide liste toutes les commandes.")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }
}
