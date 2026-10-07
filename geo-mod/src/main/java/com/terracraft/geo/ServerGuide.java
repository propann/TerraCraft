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
        dispatcher.register(Commands.literal("aide").executes(c -> show(c.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("guide").executes(c -> show(c.getSource().getPlayerOrException())));
    }

    private static int show(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Guide TerraCraft").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        line(player, "[Position]", "/terracraft ou", "Voir ta latitude et ta longitude");
        line(player, "[Maison]", "/sethome", "Enregistrer ta maison");
        line(player, "[Retour]", "/back", "Revenir à ton dernier lieu de mort");
        line(player, "[Ami]", "/tpa <joueur>", "Demander une téléportation");
        line(player, "[Marché]", "/hdv", "Vendre et acheter des ressources");
        line(player, "[Solde]", "/argent", "Voir tes crédits");
        player.sendSystemMessage(Component.literal("Claims : M ouvre la carte Xaero ; clic droit sur les chunks puis « Claim selected ». La touche ' ouvre le menu des claims.")
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Armes : clic droit maintenu avec le sniper pour zoomer · Véhicules : complète les pièces puis monte dedans.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Avion : Z/S gaz · Q/D tourner · Espace monter · Ctrl descendre · bidon d'essence : clic droit")
                .withStyle(ChatFormatting.GRAY));
        line(player, "[Comptoir]", "/comptoir", "Carburant, oxygène, munitions et vivres à prix fixe");
        player.sendSystemMessage(Component.literal("Véhicule partagé : /terracraft vehicule partager <joueur> · retirer <joueur> · liberer")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Touches : O menu · J combinaison spatiale · V coffre du véhicule · K fiche · M carte et claims")
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Combinaison (touche J ou panneau de l'inventaire E) : casque spatial, combinaison, bottes, jetpack, 2 réserves d'O₂")
                .withStyle(ChatFormatting.GRAY));
        line(player, "[Premiers pas]", "/tuto", "Revoir ton objectif en cours");
        line(player, "[Ville]", "/ville", "Fonder ou rejoindre une ville, trésorerie commune, /ville tp");
        line(player, "[Métier]", "/metier", "Choisir une spécialité (mécanicien, éclaireur, pilote…)");
        player.sendSystemMessage(Component.literal("Espace : Terre → orbite avec un kit de station orbitale ; la Lune et Mars partent du quai de la station.")
                .withStyle(ChatFormatting.GRAY));
        line(player, "[Stations]", "/station", "Tes stations spatiales (balise = point d'arrivée de tes fusées)");
        line(player, "[Plans]", "/plans", "Plans de fusée débloqués et matériaux (atelier de station)");
        line(player, "[Missions]", "/missions", "Contrats du jour et missions récompensées");
        line(player, "[Signaler]", "/signaler ", "Signaler un bug ou une perte d'objet aux administrateurs");
        return 1;
    }

    private static void line(ServerPlayer player, String label, String command, String description) {
        player.sendSystemMessage(Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand(command)))
                .append(Component.literal(" " + description).withStyle(ChatFormatting.WHITE)));
    }
}
