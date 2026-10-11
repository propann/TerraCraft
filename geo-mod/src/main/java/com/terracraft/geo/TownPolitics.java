package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vie politique des villes : le maire est élu. Un scrutin s'ouvre tous les {@link #TERM_DAYS} jours, plus tôt si un
 * tiers des habitants signe une motion de défiance ou si le maire le convoque. Pendant {@link #VOTE_HOURS} heures,
 * les habitants se présentent puis votent (boutons dans le chat, /ville election, ou registre de la mairie). Le plus
 * de voix l'emporte ; à égalité ou sans candidat, le maire sortant reste. Il faut au moins {@link #MIN_VOTERS}
 * habitants pour voter.
 */
public final class TownPolitics {
    static final int TERM_DAYS = 14;
    static final int VOTE_HOURS = 48;
    static final int MIN_VOTERS = 3;
    private static final long DAY_MS = 86_400_000L;
    private static TownPolitics instance;

    /** Scrutin en cours : candidats dans l'ordre de déclaration, voix (électeur → candidat, en UUID texte). */
    static final class Election {
        long opened;
        long closes;
        String reason;
        Set<UUID> candidates = new LinkedHashSet<>();
        Map<String, String> votes = new LinkedHashMap<>();
    }

    private final Towns towns;

    TownPolitics(Towns towns) {
        this.towns = towns;
        instance = this;
    }

    /** Pour le registre de la mairie (bloc). */
    public static void showFor(ServerPlayer player) {
        if (instance != null) {
            instance.status(player);
        }
    }

    // --- Cycle des scrutins ---------------------------------------------------------------------

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 1200 != 0) {
            return; // Une fois par minute suffit : les échéances se comptent en heures.
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Towns.Town town : towns.all()) {
            if (town.election != null && now >= town.election.closes) {
                close(town);
                changed = true;
            } else if (town.election == null && town.members.size() >= MIN_VOTERS
                    && now - Math.max(town.lastElection, town.founded) >= TERM_DAYS * DAY_MS) {
                open(town, "fin du mandat de " + TERM_DAYS + " jours");
                changed = true;
            }
        }
        if (changed) {
            towns.save();
        }
    }

    private void open(Towns.Town town, String reason) {
        Election election = new Election();
        election.opened = System.currentTimeMillis();
        election.closes = election.opened + VOTE_HOURS * 3_600_000L;
        election.reason = reason;
        town.election = election;
        town.defiance = new LinkedHashSet<>();
        GeoMod.LOGGER.info("[VILLE] scrutin ouvert à {} ({})", town.name, reason);
        towns.tellMembers(town, Component.literal("✦ Élection municipale à " + town.name + " (" + reason + ") : " + VOTE_HOURS
                        + " h pour se présenter et voter. ").withStyle(ChatFormatting.GOLD)
                .append(button("[Me présenter]", "/ville candidat", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("[Voir le scrutin]", "/ville election", ChatFormatting.AQUA)));
    }

    private void close(Towns.Town town) {
        Election election = town.election;
        town.election = null;
        town.lastElection = System.currentTimeMillis();
        Map<UUID, Integer> tally = tally(election);
        UUID winner = town.mayor;
        int best = tally.getOrDefault(town.mayor, 0);
        for (UUID candidate : election.candidates) {
            int votes = tally.getOrDefault(candidate, 0);
            if (votes > best && town.members.contains(candidate)) {
                winner = candidate;
                best = votes;
            }
        }
        String name = name(town, winner);
        GeoMod.LOGGER.info("[VILLE] scrutin clos à {} : {} élu avec {} voix ({} votants)", town.name, name, best, election.votes.size());
        if (!winner.equals(town.mayor)) {
            town.mayor = winner;
            town.deputies.remove(winner);
            towns.tellMembers(town, Component.literal("✦ " + name + " est élu maire de " + town.name + " avec " + best
                    + " voix sur " + election.votes.size() + ".").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            towns.tellMembers(town, Component.literal("✦ " + name + " reste maire de " + town.name + " (" + best + " voix sur "
                    + election.votes.size() + ").").withStyle(ChatFormatting.GOLD));
        }
    }

    private static Map<UUID, Integer> tally(Election election) {
        Map<UUID, Integer> tally = new LinkedHashMap<>();
        for (String choice : election.votes.values()) {
            tally.merge(UUID.fromString(choice), 1, Integer::sum);
        }
        return tally;
    }

    // --- Actions des habitants ------------------------------------------------------------------

    private int status(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null) {
            player.sendSystemMessage(Component.literal("Tu n'as pas de ville : pas de vote pour toi.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.literal("✦ Mairie de " + town.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Maire : " + name(town, town.mayor) + " · habitants : " + town.members.size()
                + " · trésorerie : " + town.treasury + " crédits").withStyle(ChatFormatting.GRAY));
        Election election = town.election;
        if (election == null) {
            long next = Math.max(town.lastElection, town.founded) + TERM_DAYS * DAY_MS - System.currentTimeMillis();
            int needed = defianceNeeded(town);
            player.sendSystemMessage(Component.literal(town.members.size() < MIN_VOTERS
                    ? "Pas d'élection en dessous de " + MIN_VOTERS + " habitants."
                    : "Prochaine élection dans " + Math.max(0, next / DAY_MS) + " jour(s). Motion de défiance : "
                    + defiance(town).size() + "/" + needed + " signature(s).").withStyle(ChatFormatting.GRAY));
            if (town.members.size() >= MIN_VOTERS) {
                player.sendSystemMessage(town.mayor.equals(player.getUUID())
                        ? button("[Convoquer une élection]", "/ville election convoquer", ChatFormatting.YELLOW)
                        : button("[Signer la motion de défiance]", "/ville defiance", ChatFormatting.RED));
            }
            return 1;
        }
        long left = Math.max(0, election.closes - System.currentTimeMillis());
        player.sendSystemMessage(Component.literal("Élection en cours (" + election.reason + ") : fin dans " + left / 3_600_000L + " h "
                + (left / 60_000L) % 60 + " min · " + election.votes.size() + " votant(s).").withStyle(ChatFormatting.YELLOW));
        Map<UUID, Integer> tally = tally(election);
        String mine = election.votes.get(player.getUUID().toString());
        List<UUID> running = new ArrayList<>(election.candidates);
        if (!running.contains(town.mayor)) {
            running.addFirst(town.mayor); // Le maire sortant est toujours sur la liste.
        }
        for (UUID candidate : running) {
            boolean chosen = candidate.toString().equals(mine);
            String label = name(town, candidate);
            player.sendSystemMessage(Component.literal((chosen ? "✔ " : "• ") + label + (candidate.equals(town.mayor) ? " (maire sortant)" : "")
                            + " — " + tally.getOrDefault(candidate, 0) + " voix ").withStyle(chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                    .append(chosen ? Component.empty() : button("[Voter]", "/ville voter " + label, ChatFormatting.AQUA)));
        }
        if (!election.candidates.contains(player.getUUID()) && !town.mayor.equals(player.getUUID())) {
            player.sendSystemMessage(button("[Me présenter]", "/ville candidat", ChatFormatting.GREEN));
        }
        return 1;
    }

    private int stand(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || town.election == null) {
            return error(player, "Aucune élection en cours dans ta ville.");
        }
        if (!town.election.candidates.add(player.getUUID())) {
            return error(player, "Tu es déjà candidat.");
        }
        towns.save();
        GeoMod.LOGGER.info("[VILLE] {} candidat à {}", player.getName().getString(), town.name);
        towns.tellMembers(town, Component.literal("✦ " + player.getName().getString() + " se présente à la mairie de " + town.name + ". ")
                .withStyle(ChatFormatting.GOLD).append(button("[Voter pour lui]", "/ville voter " + player.getName().getString(), ChatFormatting.AQUA)));
        return 1;
    }

    private int vote(ServerPlayer player, String candidateName) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || town.election == null) {
            return error(player, "Aucune élection en cours dans ta ville.");
        }
        UUID candidate = null;
        for (UUID id : town.members) {
            if (name(town, id).equalsIgnoreCase(candidateName)
                    && (town.election.candidates.contains(id) || id.equals(town.mayor))) {
                candidate = id;
            }
        }
        if (candidate == null) {
            return error(player, candidateName + " n'est pas candidat.");
        }
        town.election.votes.put(player.getUUID().toString(), candidate.toString());
        towns.save();
        GeoMod.LOGGER.info("[VILLE] {} vote à {}", player.getName().getString(), town.name);
        player.sendSystemMessage(Component.literal("Vote enregistré pour " + name(town, candidate)
                + " (tu peux changer d'avis jusqu'à la fin du scrutin).").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int signDefiance(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || town.mayor.equals(player.getUUID())) {
            return error(player, "Seuls les habitants (pas le maire) signent une motion de défiance.");
        }
        if (town.election != null) {
            return error(player, "Une élection est déjà en cours : vote !");
        }
        if (town.members.size() < MIN_VOTERS) {
            return error(player, "Il faut au moins " + MIN_VOTERS + " habitants pour une élection.");
        }
        if (!defiance(town).add(player.getUUID())) {
            return error(player, "Tu as déjà signé la motion.");
        }
        int needed = defianceNeeded(town);
        GeoMod.LOGGER.info("[VILLE] {} signe la motion de défiance à {} ({}/{})", player.getName().getString(), town.name,
                town.defiance.size(), needed);
        if (town.defiance.size() >= needed) {
            open(town, "motion de défiance");
        } else {
            towns.tellMembers(town, Component.literal("Motion de défiance contre le maire : " + town.defiance.size() + "/" + needed
                    + " signature(s). ").withStyle(ChatFormatting.RED).append(button("[Signer]", "/ville defiance", ChatFormatting.RED)));
        }
        towns.save();
        return 1;
    }

    private int summon(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || !town.mayor.equals(player.getUUID())) {
            return error(player, "Seul le maire convoque une élection anticipée.");
        }
        if (town.election != null) {
            return error(player, "Une élection est déjà en cours.");
        }
        if (town.members.size() < MIN_VOTERS) {
            return error(player, "Il faut au moins " + MIN_VOTERS + " habitants pour une élection.");
        }
        open(town, "élection anticipée convoquée par le maire");
        towns.save();
        return 1;
    }

    private int closeNow(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null || town.election == null) {
            return error(player, "Aucune élection en cours dans ta ville.");
        }
        close(town);
        towns.save();
        return 1;
    }

    private static Set<UUID> defiance(Towns.Town town) {
        if (town.defiance == null) {
            town.defiance = new LinkedHashSet<>();
        }
        return town.defiance;
    }

    /** Un tiers des habitants, maire non compris (au moins 1). */
    private static int defianceNeeded(Towns.Town town) {
        return Math.max(1, (town.members.size() + 2) / 3);
    }

    private static String name(Towns.Town town, UUID id) {
        return town.names.getOrDefault(id.toString(), "?");
    }

    private static MutableComponent button(String label, String command, ChatFormatting colour) {
        return Component.literal(label).withStyle(s -> s.withColor(colour).withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static int error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        return 0;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ville")
                .then(Commands.literal("election").executes(c -> status(c.getSource().getPlayerOrException()))
                        .then(Commands.literal("convoquer").executes(c -> summon(c.getSource().getPlayerOrException())))
                        // Administration (et test de scénario) : dépouille le scrutin de sa ville tout de suite.
                        .then(Commands.literal("clore").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(c -> closeNow(c.getSource().getPlayerOrException()))))
                .then(Commands.literal("candidat").executes(c -> stand(c.getSource().getPlayerOrException())))
                .then(Commands.literal("voter").then(Commands.argument("joueur", StringArgumentType.word())
                        .executes(c -> vote(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "joueur")))))
                .then(Commands.literal("defiance").executes(c -> signDefiance(c.getSource().getPlayerOrException()))));
    }
}
