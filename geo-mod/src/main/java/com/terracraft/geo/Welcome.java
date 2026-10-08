package com.terracraft.geo;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vitrine du serveur : icône et MOTD dans la liste des serveurs, en-tête et pied de la liste des
 * joueurs (Tab), et accueil des joueurs qui reviennent (titre, état de leur partie, nouveautés
 * depuis leur dernière visite). Les nouveaux joueurs passent par la carte de départ et Arrival.
 */
final class Welcome {
    /** Nouveautés par version (la plus récente en premier), montrées aux joueurs qui reviennent. */
    private record Change(String version, String summary) {
    }

    private static final List<Change> CHANGES = List.of(
            new Change("0.33", "Lampes électriques, réseaux de tuyaux et câbles jusqu'à ~1 000 blocs"),
            new Change("0.32", "Groupe électrogène, tableau de bord des véhicules (vitesse, carburant, autonomie)"),
            new Change("0.31", "Stations-service en ruine (vraies adresses), batteries pour la nuit, écran des machines"),
            new Change("0.30", "Industrie du carburant : pétrole, pompes, raffinerie, panneaux solaires, bidons à remplir"),
            new Change("0.29", "Moto réparée (affichage), nouvelles armes en pixel art"),
            new Change("0.28", "Stations et bases protégées autour de leur balise ; serveur mesuré jusqu'à 10 joueurs"),
            new Change("0.27", "Course à l'espace : le premier à chaque étape gagne une prime (/course)"),
            new Change("0.26", "Règles du serveur (/regles) et modérateurs ; bêta prête"),
            new Change("0.25", "Les claims des habitants agrandissent leur ville ; adjoints de maire (/ville adjoint)"),
            new Change("0.24", "Étals de marché : vends tes objets à prix fixe, même hors ligne (/etal)"),
            new Change("0.23", "Commandants de bunker (boss, insigne, prime de 300), primes sur les joueurs (/prime)"),
            new Change("0.22", "Vagues nocturnes sur les villes, PvE partout et PvP seulement dans les zones affichées (/pvp)"),
            new Change("0.21", "Convois militaires en panne à attaquer, zones contaminées (combinaison requise)"),
            new Change("0.20", "Confirmation avant de fonder ou dissoudre une ville, ou de vendre à prix cassé"),
            new Change("0.19", "Jour et nuit normaux, barres de vie, chat et menu O refaits, villes affichées devant le pseudo"),
            new Change("0.18", "Dangers lunaires : pluies de micrométéorites (abrite-toi), rôdeurs plus forts la nuit"),
            new Change("0.17", "Carte des étoiles : destinations, coûts et obstacles (Maj + clic droit sur la fusée, menu O)"),
            new Change("0.16", "Sous-sol lunaire : cavernes géantes, sanctuaires et pyramides extraterrestres"),
            new Change("0.15", "Bases lunaire et martienne en kit, rover lunaire solaire"),
            new Change("0.14", "Station orbitale d'abord (kit), fusées à 2 et 4 réservoirs, sas et quai d'amarrage"),
            new Change("0.13", "Accueil refait, liste des joueurs, icône du serveur"),
            new Change("0.12", "Plans de fusée (/plans) et atelier de station"),
            new Change("0.11", "Orbite lunaire, carburant par trajet, stations en kit (/station)"),
            new Change("0.10", "Contrats du jour, métiers (/metier), ravitaillements militaires, /signaler"),
            new Change("0.9", "Villes (/ville) avec maire et trésorerie"),
            new Change("0.8", "Inventaire spatial (E), jetpack, avion, hôtel des ventes par catégories"));

    private final AuctionHouse bank;
    private final Towns towns;
    private final Contracts contracts;
    private final StartPoints startPoints;

    Welcome(AuctionHouse bank, Towns towns, Contracts contracts, StartPoints startPoints) {
        this.bank = bank;
        this.towns = towns;
        this.contracts = contracts;
        this.startPoints = startPoints;
    }

    // --- Liste des serveurs ------------------------------------------------------------------

    /** Au démarrage du mod : installe l'icône TerraCraft si le serveur n'en a pas. */
    static void installIcon() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        Path icon = FabricLoader.getInstance().getGameDir().resolve("server-icon.png");
        if (Files.exists(icon)) {
            return; // Icône personnalisée : on la garde.
        }
        try (InputStream in = Welcome.class.getResourceAsStream("/assets/" + GeoMod.MOD_ID + "/server-icon.png")) {
            if (in != null) {
                Files.copy(in, icon);
                GeoMod.LOGGER.info("Icône TerraCraft installée : {}", icon);
            }
        } catch (IOException e) {
            GeoMod.LOGGER.warn("Icône du serveur impossible à écrire", e);
        }
    }

    /** MOTD sur deux lignes avec la version ; un MOTD personnalisé est respecté. */
    static void motd(MinecraftServer server) {
        String current = server.getMotd() == null ? "" : server.getMotd().strip();
        String lower = current.toLowerCase(Locale.ROOT);
        if (!current.isEmpty() && !lower.contains("minecraft server") && !lower.contains("falix") && !lower.startsWith("terracraft")) {
            return;
        }
        server.setMotd("§6§lTerraCraft §r§7· §fla Terre réelle après la chute\n§b" + GeoMod.version()
                + " §8| §7villes · métiers · fusées · stations lunaires");
    }

    // --- Liste des joueurs (Tab) -------------------------------------------------------------

    void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        towns.syncTeams(server);
        int online = server.getPlayerList().getPlayerCount();
        Component header = Component.literal("\n").append(Component.literal("TerraCraft").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\nla Terre réelle après la chute\n").withStyle(ChatFormatting.GRAY));
        Component footer = Component.literal("\n" + online + (online > 1 ? " survivants" : " survivant") + " en ligne · "
                        + GeoMod.version()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("\nO : menu  ·  J : combinaison  ·  /aide  ·  /regles  ·  /signaler\n").withStyle(ChatFormatting.DARK_GRAY));
        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    // --- Accueil -----------------------------------------------------------------------------

    void onJoin(ServerPlayer player) {
        towns.syncTeams(player.level().getServer());
        boolean returning = startPoints.choice(player.getUUID()) != null;
        String version = GeoMod.version();
        String lastSeen = Progression.get().lastVersion(player);
        Progression.get().setLastVersion(player, version);
        if (!returning) {
            // Nouveau joueur : la carte de départ s'ouvre ; une ligne suffit en attendant.
            player.sendSystemMessage(Component.literal("✦ Bienvenue sur TerraCraft, " + player.getName().getString()
                    + " ! Choisis ton point de départ sur la carte du monde réel.").withStyle(ChatFormatting.GOLD));
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("TerraCraft")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Bon retour, " + player.getName().getString())
                .withStyle(ChatFormatting.GRAY)));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8f, 1.2f);

        player.sendSystemMessage(Component.literal("━━━━━━━━ TerraCraft " + version + " ━━━━━━━━")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        int online = player.level().getServer().getPlayerList().getPlayerCount();
        player.sendSystemMessage(Component.literal("Bon retour, " + player.getName().getString() + " ! ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(online + (online > 1 ? " survivants" : " survivant") + " en ligne · solde "
                        + bank.balance(player.getUUID()) + " crédits").withStyle(ChatFormatting.GRAY)));

        int done = 0;
        int ready = 0;
        for (Contracts.Contract contract : Contracts.todays()) {
            if (contracts.claimed(player, contract)) {
                done++;
            } else if (contracts.progress(player, contract) >= contract.target()) {
                ready++;
            }
        }
        Towns.Town town = towns.townOf(player.getUUID());
        Jobs.Job job = Jobs.of(player);
        String status = "Contrats du jour : " + done + "/3" + (ready > 0 ? " (" + ready + " à réclamer)" : "")
                + " · " + (town == null ? "sans ville" : "ville : " + town.name)
                + " · " + (job == null ? "sans métier" : job.label);
        player.sendSystemMessage(Component.literal(status).withStyle(ready > 0 ? ChatFormatting.GREEN : ChatFormatting.AQUA));

        List<String> news = news(lastSeen);
        if (!news.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nouveau depuis ta dernière visite :").withStyle(ChatFormatting.YELLOW));
            for (String line : news) {
                player.sendSystemMessage(Component.literal("  • " + line).withStyle(ChatFormatting.WHITE));
            }
        }

        MutableComponent actions = button("[Missions]", "/missions", ChatFormatting.GREEN);
        actions.append(Component.literal("  ")).append(button("[Ma ville]", "/ville", ChatFormatting.GOLD));
        actions.append(Component.literal("  ")).append(button("[Marché]", "/hdv", ChatFormatting.YELLOW));
        actions.append(Component.literal("  ")).append(button("[Aide]", "/aide", ChatFormatting.AQUA));
        actions.append(Component.literal("   touche O : menu").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(actions);
    }

    /** Nouveautés publiées depuis la version que le joueur avait vue (3 au plus). */
    private static List<String> news(String lastSeen) {
        List<String> lines = new ArrayList<>();
        if (lastSeen == null) {
            return lines; // Premier passage avec ce suivi : pas d'historique à montrer.
        }
        for (Change change : CHANGES) {
            if (lines.size() == 3 || compare(change.version(), lastSeen) <= 0) {
                break;
            }
            lines.add(change.summary() + " (" + change.version() + ")");
        }
        return lines;
    }

    /** Compare deux versions « 0.12.0 » par leurs nombres (0.10 est plus récent que 0.9). */
    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < 2; i++) {
            int u = i < x.length ? parse(x[i]) : 0;
            int v = i < y.length ? parse(y[i]) : 0;
            if (u != v) {
                return Integer.compare(u, v);
            }
        }
        return 0;
    }

    private static int parse(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal(label).withStyle(style -> style.withColor(color).withClickEvent(new ClickEvent.RunCommand(command)));
    }
}
