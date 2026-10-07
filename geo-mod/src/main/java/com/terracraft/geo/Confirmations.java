package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Confirmation des actions coûteuses (fonder une ville, la dissoudre, vendre très en dessous du prix moyen) : un
 * message avec [Confirmer] et [Annuler] cliquables ; l'action attend 30 s. Une seule action en attente par joueur ;
 * une nouvelle demande remplace la précédente. L'action est rejouée avec ses propres vérifications au moment de la
 * confirmation (solde, nom libre…).
 */
final class Confirmations {
    private static final long TIMEOUT_MS = 30_000;

    private record Pending(String label, Runnable action, long expires) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private Confirmations() {
    }

    /** Demande confirmation ; {@code action} sera exécutée par /confirmer. */
    static void ask(ServerPlayer player, String label, String details, Runnable action) {
        PENDING.put(player.getUUID(), new Pending(label, action, System.currentTimeMillis() + TIMEOUT_MS));
        player.sendSystemMessage(Component.literal("⚠ " + label).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(details).withStyle(ChatFormatting.GRAY));
        MutableComponent buttons = button("[Confirmer]", "/confirmer", ChatFormatting.GREEN, "Valider : " + label);
        buttons.append(Component.literal("   ")).append(button("[Annuler]", "/annuler", ChatFormatting.RED, "Ne rien faire"));
        buttons.append(Component.literal("   (30 s)").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(buttons);
    }

    private static MutableComponent button(String label, String command, ChatFormatting colour, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("confirmer").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            Pending pending = PENDING.remove(player.getUUID());
            if (pending == null || pending.expires() < System.currentTimeMillis()) {
                player.sendSystemMessage(Component.literal("Rien à confirmer (ou délai de 30 s dépassé).").withStyle(ChatFormatting.RED));
                return 0;
            }
            pending.action().run();
            return 1;
        }));
        dispatcher.register(Commands.literal("annuler").executes(c -> {
            ServerPlayer player = c.getSource().getPlayerOrException();
            Pending pending = PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(pending == null ? "Rien à annuler." : "Annulé : " + pending.label())
                    .withStyle(ChatFormatting.GRAY));
            return pending == null ? 0 : 1;
        }));
    }
}
