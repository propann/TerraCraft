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

    static void welcome(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("━━━━━━━━ TerraCraft ━━━━━━━━")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Terre réelle, survie, exploration et départ vers l'espace.")
                .withStyle(ChatFormatting.GRAY));
        var actions = Component.literal("[Aide complète]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/aide")));
        actions.append(Component.literal("  "));
        actions.append(Component.literal("[Marché]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/hdv"))));
        actions.append(Component.literal("  "));
        actions.append(Component.literal("[Solde]").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.RunCommand("/argent"))));
        player.sendSystemMessage(actions);
    }

    private static int show(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Guide TerraCraft").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        line(player, "[Position]", "/terracraft ou", "Voir ta latitude et ta longitude");
        line(player, "[Maison]", "/sethome", "Enregistrer ta maison");
        line(player, "[Retour]", "/back", "Revenir à ton dernier lieu de mort");
        line(player, "[Ami]", "/tpa <joueur>", "Demander une téléportation");
        line(player, "[Marché]", "/hdv", "Vendre et acheter des ressources");
        player.sendSystemMessage(Component.literal("Espace : fusée · Lune : casque-combinaison chargé · K : fiche de personnage")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static void line(ServerPlayer player, String label, String command, String description) {
        player.sendSystemMessage(Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.SuggestCommand(command)))
                .append(Component.literal(" " + description).withStyle(ChatFormatting.WHITE)));
    }
}
