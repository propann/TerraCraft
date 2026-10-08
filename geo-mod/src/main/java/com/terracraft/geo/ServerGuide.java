package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Accueil et aide courte : le joueur comprend quoi faire sans quitter Minecraft. */
final class ServerGuide {
    private ServerGuide() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aide").executes(c -> show(c.getSource().getPlayerOrException(), null))
                .then(Commands.argument("theme", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(THEMES.keySet(), b))
                        .executes(c -> show(c.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "theme")))));
        dispatcher.register(Commands.literal("guide").executes(c -> show(c.getSource().getPlayerOrException(), null)));
    }

    private record Entry(String label, String command, String description) {
    }

    /** Pages de l'aide : un thème par page, pour tenir dans le chat. Une entrée sans commande est un simple conseil. */
    private static final java.util.Map<String, java.util.List<Entry>> THEMES = new java.util.LinkedHashMap<>();

    static {
        THEMES.put("survie", java.util.List.of(
                new Entry("[Position]", "/terracraft ou", "Ta latitude et ta longitude réelles"),
                new Entry("[Lieux]", "/lieux", "Hôpital, pharmacie, commissariat, gare, supermarché… les plus proches"),
                new Entry("[Maison]", "/sethome", "Enregistrer ta maison ; /home pour y revenir"),
                new Entry("[Retour]", "/back", "Revenir au lieu de ta dernière mort"),
                new Entry("[Ami]", "/tpa ", "Demander une téléportation à un joueur"),
                new Entry("[Premiers pas]", "/tuto", "Ton objectif en cours"),
                new Entry("[Missions]", "/missions", "Contrats du jour et missions récompensées"),
                new Entry("[Métier]", "/metier", "Mécanicien, éclaireur, récupérateur, combattant, pilote"),
                new Entry("", "", "Carburant : détecteur de pétrole → pompe sur un gisement (panneaux solaires, câbles) → tuyaux → "
                        + "raffinerie → réservoir → pompe à essence (bidon vide ; Maj : carburant de fusée)."),
                new Entry("", "", "Dangers : convois militaires, zones contaminées ☢ (combinaison requise), bunkers gardés.")));
        THEMES.put("commerce", java.util.List.of(
                new Entry("[Solde]", "/argent", "Tes crédits"),
                new Entry("[Marché]", "/hdv", "Hôtel des ventes : vendre et acheter entre joueurs"),
                new Entry("[Comptoir]", "/comptoir", "Carburant, oxygène, munitions et vivres à prix fixe"),
                new Entry("[Étal]", "/etal", "Ta boutique, même hors ligne : /etal prix, /etal ajouter, /etal acheter"),
                new Entry("[Confirmer]", "/confirmer", "Valider une action coûteuse en attente (/annuler sinon)")));
        THEMES.put("villes", java.util.List.of(
                new Entry("[Ville]", "/ville", "Fonder, rejoindre, trésorerie, /ville tp"),
                new Entry("[Adjoint]", "/ville adjoint ", "Le maire nomme des adjoints"),
                new Entry("", "", "Claims : touche M, clic droit sur les chunks ; les claims des habitants agrandissent la ville."),
                new Entry("", "", "Certaines nuits, des hordes attaquent les villes : tenez jusqu'à l'aube !")));
        THEMES.put("combat", java.util.List.of(
                new Entry("[Règles]", "/regles", "Les règles du serveur"),
                new Entry("[PvP]", "/pvp", "PvE partout ; le combat entre joueurs n'existe que dans les zones PvP"),
                new Entry("[Primes]", "/primes", "Têtes mises à prix ; /prime <joueur> <montant>"),
                new Entry("[Signaler]", "/signaler ", "Signaler un bug, une perte ou un comportement")));
        THEMES.put("espace", java.util.List.of(
                new Entry("[Course]", "/course", "Course à l'espace : primes pour les premiers"),
                new Entry("[Carte des étoiles]", "/fusee carte", "Destinations, coûts et obstacles (Maj + clic droit sur la fusée)"),
                new Entry("[Stations]", "/station", "Tes stations et bases (protégées autour de leur balise)"),
                new Entry("[Plans]", "/plans", "Plans de fusée et matériaux (atelier de station)"),
                new Entry("", "", "Terre → orbite avec un kit de station ; la Lune et Mars partent du quai de la station."),
                new Entry("", "", "Lune : sanctuaires sous la surface, micrométéorites (abrite-toi), rover en caisse.")));
        THEMES.put("touches", java.util.List.of(
                new Entry("", "", "O menu · J combinaison · V coffre du véhicule · K fiche · M carte et claims · ' menu des claims"),
                new Entry("", "", "E : inventaire avec le panneau de la combinaison (casque, combinaison, bottes, jetpack, O₂)"),
                new Entry("", "", "Avion : Z/S gaz · Q/D tourner · Espace monter · Ctrl descendre"),
                new Entry("", "", "Rover : clic droit avec la caisse ; Maj + clic gauche pour le remballer"),
                new Entry("[Véhicule]", "/terracraft vehicule partager ", "Partager ton véhicule ; retirer, liberer")));
    }

    private static int show(ServerPlayer player, String theme) {
        java.util.List<Entry> entries = theme == null ? null : THEMES.get(theme.toLowerCase(java.util.Locale.ROOT));
        if (entries == null) {
            player.sendSystemMessage(Component.literal("✦ Guide TerraCraft — choisis un thème :").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            var menu = Component.literal("  ");
            for (String name : THEMES.keySet()) {
                menu.append(Component.literal("[" + name + "]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/aide " + name)))).append(Component.literal("  "));
            }
            player.sendSystemMessage(menu);
            player.sendSystemMessage(Component.literal("Touche O : menu TerraCraft · /regles · /signaler en cas de souci").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        player.sendSystemMessage(Component.literal("✦ Guide — " + theme.toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (Entry entry : entries) {
            if (entry.command().isEmpty()) {
                player.sendSystemMessage(Component.literal("  " + entry.description()).withStyle(ChatFormatting.GRAY));
            } else {
                line(player, entry.label(), entry.command(), entry.description());
            }
        }
        player.sendSystemMessage(Component.literal("[← sommaire]").withStyle(style -> style.withColor(ChatFormatting.DARK_AQUA)
                .withClickEvent(new ClickEvent.RunCommand("/aide"))));
        return 1;
    }

    private static void line(ServerPlayer player, String label, String command, String description) {
        player.sendSystemMessage(Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand(command)))
                .append(Component.literal(" " + description).withStyle(ChatFormatting.WHITE)));
    }
}
