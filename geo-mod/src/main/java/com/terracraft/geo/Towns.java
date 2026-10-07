package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Villes : un groupe de joueurs autour d'un centre-ville, avec un maire, des membres, une
 * trésorerie commune et un retour au centre (/ville tp). Les claims restent gérés par Open
 * Parties and Claims ; la ville donne une identité, un point de ralliement et une caisse commune.
 */
final class Towns {
    static final long CREATION_COST = 500;
    static final int RADIUS = 64;
    /** Distance minimale entre deux centres-villes. */
    private static final int MIN_DISTANCE = 2 * RADIUS + 32;
    private static final int MAX_MEMBERS = 30;
    private static final long INVITE_TIMEOUT_MS = 120_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Town {
        String name;
        UUID mayor;
        Set<UUID> members = new LinkedHashSet<>();
        Map<String, String> names = new LinkedHashMap<>();
        Survival.Place center;
        long treasury;
        long founded;
    }

    private record Invite(String town, long time) {
    }

    private final Map<String, Town> towns = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    /** Ville où se trouve chaque joueur, pour les titres d'entrée et de sortie. */
    private final Map<UUID, String> inside = new HashMap<>();
    private final AuctionHouse bank;
    private final RateLimit teleportRate = new RateLimit(30_000);
    private Path file;
    private MinecraftServer server;

    Towns(AuctionHouse bank) {
        this.bank = bank;
        bank.treasuries(() -> towns.values().stream().mapToLong(t -> t.treasury).sum());
    }

    void load(MinecraftServer server) {
        this.server = server;
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("villes.json");
        towns.clear();
        invites.clear();
        inside.clear();
        List<Town> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<List<Town>>() { }.getType()));
        if (stored != null) {
            stored.forEach(town -> towns.put(key(town.name), town));
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            JsonStore.write(file, GSON.toJson(new ArrayList<>(towns.values())));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    Town townOf(UUID player) {
        for (Town town : towns.values()) {
            if (town.members.contains(player)) {
                return town;
            }
        }
        return null;
    }

    // --- Commandes -----------------------------------------------------------------------------

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ville")
                .executes(c -> info(c.getSource().getPlayerOrException()))
                .then(Commands.literal("creer").then(Commands.argument("nom", StringArgumentType.greedyString())
                        .executes(c -> create(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))))
                .then(Commands.literal("inviter").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("rejoindre").executes(c -> join(c.getSource().getPlayerOrException())))
                .then(Commands.literal("quitter").executes(c -> leave(c.getSource().getPlayerOrException())))
                .then(Commands.literal("exclure").then(Commands.argument("joueur", StringArgumentType.word())
                        .executes(c -> kick(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "joueur")))))
                .then(Commands.literal("maire").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(c -> transfer(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("centre").executes(c -> moveCenter(c.getSource().getPlayerOrException())))
                .then(Commands.literal("tp").executes(c -> teleport(c.getSource().getPlayerOrException())))
                .then(Commands.literal("deposer").then(Commands.argument("montant", IntegerArgumentType.integer(1, 1_000_000_000))
                        .executes(c -> depositTo(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "montant")))))
                .then(Commands.literal("payer").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("montant", IntegerArgumentType.integer(1, 1_000_000_000))
                                .executes(c -> pay(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur"),
                                        IntegerArgumentType.getInteger(c, "montant"))))))
                .then(Commands.literal("liste").executes(c -> list(c.getSource().getPlayerOrException()))));
    }

    private static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private int create(ServerPlayer player, String rawName) {
        String name = rawName.strip().replaceAll("\\s+", " ");
        if (name.length() < 3 || name.length() > 24 || !name.matches("[\\p{L}0-9 '\\-]+")) {
            error(player, "Nom de ville : 3 à 24 caractères (lettres, chiffres, espaces, apostrophes, tirets).");
            return 0;
        }
        if (townOf(player.getUUID()) != null) {
            error(player, "Tu fais déjà partie d'une ville : /ville quitter d'abord.");
            return 0;
        }
        if (towns.containsKey(key(name))) {
            error(player, "Une ville porte déjà ce nom.");
            return 0;
        }
        Survival.Place here = here(player);
        Town tooClose = nearest(here, MIN_DISTANCE);
        if (tooClose != null) {
            error(player, "Trop près de " + tooClose.name + " : éloigne-toi d'au moins " + MIN_DISTANCE + " blocs de son centre.");
            return 0;
        }
        if (!bank.withdraw(player.getUUID(), CREATION_COST, "creation_ville")) {
            error(player, "Fonder une ville coûte " + CREATION_COST + " crédits.");
            return 0;
        }
        Town town = new Town();
        town.name = name;
        town.mayor = player.getUUID();
        town.members.add(player.getUUID());
        town.names.put(player.getUUID().toString(), player.getName().getString());
        town.center = here;
        town.founded = System.currentTimeMillis();
        towns.put(key(name), town);
        save();
        GeoMod.LOGGER.info("[VILLE] {} fonde {} en {}", player.getName().getString(), name, here);
        server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + player.getName().getString()
                + " fonde la ville de " + name + " !").withStyle(ChatFormatting.GOLD), false);
        player.sendSystemMessage(Component.literal("Ta ville est centrée ici (rayon " + RADIUS + " blocs). Pense à revendiquer "
                + "tes chunks (touche M). /ville inviter <joueur> pour recruter.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int invite(ServerPlayer mayor, ServerPlayer target) {
        Town town = townOf(mayor.getUUID());
        if (town == null || !town.mayor.equals(mayor.getUUID())) {
            error(mayor, "Seul le maire peut inviter.");
            return 0;
        }
        if (townOf(target.getUUID()) != null) {
            error(mayor, target.getName().getString() + " fait déjà partie d'une ville.");
            return 0;
        }
        if (town.members.size() >= MAX_MEMBERS) {
            error(mayor, "La ville est complète (" + MAX_MEMBERS + " habitants).");
            return 0;
        }
        invites.put(target.getUUID(), new Invite(key(town.name), System.currentTimeMillis()));
        mayor.sendSystemMessage(Component.literal("Invitation envoyée à " + target.getName().getString() + ".").withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(Component.literal(mayor.getName().getString() + " t'invite à rejoindre " + town.name + ". ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Rejoindre]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/ville rejoindre")))));
        return 1;
    }

    private int join(ServerPlayer player) {
        Invite invite = invites.remove(player.getUUID());
        Town town = invite == null ? null : towns.get(invite.town());
        if (town == null || System.currentTimeMillis() - invite.time() > INVITE_TIMEOUT_MS) {
            error(player, "Aucune invitation en attente (elles expirent après 2 minutes).");
            return 0;
        }
        if (townOf(player.getUUID()) != null) {
            error(player, "Tu fais déjà partie d'une ville.");
            return 0;
        }
        town.members.add(player.getUUID());
        town.names.put(player.getUUID().toString(), player.getName().getString());
        save();
        tellMembers(town, Component.literal("✦ " + player.getName().getString() + " rejoint " + town.name + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int leave(ServerPlayer player) {
        Town town = townOf(player.getUUID());
        if (town == null) {
            error(player, "Tu ne fais partie d'aucune ville.");
            return 0;
        }
        if (town.mayor.equals(player.getUUID()) && town.members.size() > 1) {
            error(player, "Tu es maire : passe la main d'abord (/ville maire <joueur>).");
            return 0;
        }
        town.members.remove(player.getUUID());
        if (town.members.isEmpty()) {
            // Dernier habitant : la trésorerie lui revient, la ville disparaît.
            bank.deposit(player.getUUID(), town.treasury);
            towns.remove(key(town.name));
            GeoMod.LOGGER.info("[VILLE] {} dissoute par {} ({} crédits rendus)", town.name, player.getName().getString(), town.treasury);
            player.sendSystemMessage(Component.literal("La ville de " + town.name + " est dissoute ; sa trésorerie ("
                    + town.treasury + " crédits) t'est rendue.").withStyle(ChatFormatting.GOLD));
        } else {
            tellMembers(town, Component.literal(player.getName().getString() + " quitte " + town.name + ".").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("Tu as quitté " + town.name + ".").withStyle(ChatFormatting.GRAY));
        }
        save();
        return 1;
    }

    private int kick(ServerPlayer mayor, String name) {
        Town town = townOf(mayor.getUUID());
        if (town == null || !town.mayor.equals(mayor.getUUID())) {
            error(mayor, "Seul le maire peut exclure un habitant.");
            return 0;
        }
        UUID target = null;
        for (Map.Entry<String, String> entry : town.names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name) && town.members.contains(UUID.fromString(entry.getKey()))) {
                target = UUID.fromString(entry.getKey());
            }
        }
        if (target == null || target.equals(mayor.getUUID())) {
            error(mayor, "Aucun autre habitant ne porte ce nom.");
            return 0;
        }
        town.members.remove(target);
        save();
        tellMembers(town, Component.literal(name + " a été exclu de " + town.name + ".").withStyle(ChatFormatting.GRAY));
        ServerPlayer online = server.getPlayerList().getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(Component.literal("Tu as été exclu de " + town.name + ".").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private int transfer(ServerPlayer mayor, ServerPlayer target) {
        Town town = townOf(mayor.getUUID());
        if (town == null || !town.mayor.equals(mayor.getUUID()) || !town.members.contains(target.getUUID())) {
            error(mayor, "Le nouveau maire doit être un habitant de ta ville.");
            return 0;
        }
        town.mayor = target.getUUID();
        save();
        tellMembers(town, Component.literal("✦ " + target.getName().getString() + " est le nouveau maire de " + town.name + ".")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private int moveCenter(ServerPlayer mayor) {
        Town town = townOf(mayor.getUUID());
        if (town == null || !town.mayor.equals(mayor.getUUID())) {
            error(mayor, "Seul le maire peut déplacer le centre-ville.");
            return 0;
        }
        Survival.Place here = here(mayor);
        Town other = nearest(here, MIN_DISTANCE);
        if (other != null && other != town) {
            error(mayor, "Trop près de " + other.name + ".");
            return 0;
        }
        town.center = here;
        save();
        mayor.sendSystemMessage(Component.literal("Centre-ville déplacé ici.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int teleport(ServerPlayer player) {
        Town town = townOf(player.getUUID());
        if (town == null) {
            error(player, "Tu ne fais partie d'aucune ville.");
            return 0;
        }
        if (Survival.inCombat(player)) {
            return 0;
        }
        long wait = teleportRate.remainingMs(player);
        if (wait > 0) {
            error(player, "Attends encore " + (wait + 999) / 1000 + " s avant de retourner au centre-ville.");
            return 0;
        }
        teleportRate.mark(player);
        return Survival.go(player, town.center, "", "Bienvenue au centre de " + town.name + ".");
    }

    private int depositTo(ServerPlayer player, long amount) {
        Town town = townOf(player.getUUID());
        if (town == null) {
            error(player, "Tu ne fais partie d'aucune ville.");
            return 0;
        }
        if (!bank.withdraw(player.getUUID(), amount, null)) {
            error(player, "Solde insuffisant.");
            return 0;
        }
        town.treasury += amount;
        save();
        GeoMod.LOGGER.info("[VILLE] {} dépose {} crédits dans la trésorerie de {}", player.getName().getString(), amount, town.name);
        tellMembers(town, Component.literal(player.getName().getString() + " dépose " + amount + " crédits dans la trésorerie ("
                + town.treasury + ").").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int pay(ServerPlayer mayor, ServerPlayer target, long amount) {
        Town town = townOf(mayor.getUUID());
        if (town == null || !town.mayor.equals(mayor.getUUID())) {
            error(mayor, "Seul le maire peut puiser dans la trésorerie.");
            return 0;
        }
        if (!town.members.contains(target.getUUID())) {
            error(mayor, "La trésorerie ne paie que les habitants de la ville.");
            return 0;
        }
        if (town.treasury < amount) {
            error(mayor, "Trésorerie insuffisante (" + town.treasury + " crédits).");
            return 0;
        }
        town.treasury -= amount;
        bank.deposit(target.getUUID(), amount);
        save();
        GeoMod.LOGGER.info("[VILLE] {} paie {} crédits à {} depuis {}", mayor.getName().getString(), amount,
                target.getName().getString(), town.name);
        tellMembers(town, Component.literal("La trésorerie de " + town.name + " verse " + amount + " crédits à "
                + target.getName().getString() + ".").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private int info(ServerPlayer player) {
        Town town = townOf(player.getUUID());
        if (town == null) {
            player.sendSystemMessage(Component.literal("✦ Villes").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal("Tu n'as pas de ville. Fonde la tienne pour " + CREATION_COST
                    + " crédits : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[/ville creer <nom>]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.SuggestCommand("/ville creer ")))));
            player.sendSystemMessage(Component.literal("Ou demande une invitation à un maire. ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[Liste des villes]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.RunCommand("/ville liste")))));
            return 1;
        }
        boolean mayor = town.mayor.equals(player.getUUID());
        player.sendSystemMessage(Component.literal("✦ " + town.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Maire : " + town.names.getOrDefault(town.mayor.toString(), "?")
                + " · habitants : " + town.members.size() + " · trésorerie : " + town.treasury + " crédits"));
        List<String> names = new ArrayList<>();
        town.members.forEach(id -> names.add(town.names.getOrDefault(id.toString(), "?")));
        player.sendSystemMessage(Component.literal("Habitants : " + String.join(", ", names)).withStyle(ChatFormatting.GRAY));
        var actions = Component.literal("[Centre-ville]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand("/ville tp")));
        actions.append(Component.literal("  "));
        actions.append(Component.literal("[Déposer]").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.SuggestCommand("/ville deposer "))));
        if (mayor) {
            actions.append(Component.literal("  "));
            actions.append(Component.literal("[Inviter]").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent.SuggestCommand("/ville inviter "))));
            actions.append(Component.literal("  "));
            actions.append(Component.literal("[Payer]").withStyle(s -> s.withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent.SuggestCommand("/ville payer "))));
        }
        actions.append(Component.literal("  "));
        actions.append(Component.literal("[Quitter]").withStyle(s -> s.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent.SuggestCommand("/ville quitter"))));
        player.sendSystemMessage(actions);
        return 1;
    }

    private int list(ServerPlayer player) {
        if (towns.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucune ville pour le moment.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        player.sendSystemMessage(Component.literal("✦ Villes (" + towns.size() + ")").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (Town town : towns.values()) {
            player.sendSystemMessage(Component.literal(town.name + " — maire " + town.names.getOrDefault(town.mayor.toString(), "?")
                    + ", " + town.members.size() + " habitant(s), centre " + Math.round(town.center.x()) + " / "
                    + Math.round(town.center.z())));
        }
        return 1;
    }

    // --- Entrée et sortie des villes -----------------------------------------------------------

    /** Deux fois par seconde : titre quand on entre dans une ville ou qu'on la quitte. */
    void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || towns.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Town town = nearest(here(player), RADIUS);
            String now = town == null ? null : key(town.name);
            String before = inside.get(player.getUUID());
            if (java.util.Objects.equals(now, before)) {
                continue;
            }
            if (now == null) {
                inside.remove(player.getUUID());
                Town left = towns.get(before);
                if (left != null) {
                    player.sendOverlayMessage(Component.literal("Tu quittes " + left.name).withStyle(ChatFormatting.GRAY));
                }
            } else {
                inside.put(player.getUUID(), now);
                boolean home = town.members.contains(player.getUUID());
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(town.name)
                        .withStyle(home ? ChatFormatting.GREEN : ChatFormatting.GOLD, ChatFormatting.BOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(home ? "Bon retour chez toi"
                        : "Ville de " + town.names.getOrDefault(town.mayor.toString(), "?") + " · " + town.members.size()
                        + " habitant(s)").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    void onLeave(ServerPlayer player) {
        inside.remove(player.getUUID());
        teleportRate.forget(player);
    }

    private Town nearest(Survival.Place place, double within) {
        Town best = null;
        double bestDistance = within * within;
        for (Town town : towns.values()) {
            if (!town.center.dimension().equals(place.dimension())) {
                continue;
            }
            double dx = town.center.x() - place.x();
            double dz = town.center.z() - place.z();
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = town;
            }
        }
        return best;
    }

    private static Survival.Place here(ServerPlayer player) {
        return new Survival.Place(player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
                player.getYRot());
    }

    private void tellMembers(Town town, Component message) {
        if (server == null) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (town.members.contains(online.getUUID())) {
                online.sendSystemMessage(message);
            }
        }
    }
}
