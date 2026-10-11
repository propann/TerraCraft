package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat de TerraCraft : canaux global (par défaut), ville ({@code !message} ou /v) et local (100 blocs, /l) ;
 * {@code /canal} choisit le canal des messages ordinaires. Mentions {@code @Pseudo} surlignées avec un signal sonore,
 * messages privés /mp et réponse /r, anti-spam (rafales et répétitions), silence imposé par les modérateurs respecté
 * partout. Le canal global garde le chat signé vanilla ;
 * ville, local et privé sont des messages système envoyés aux seuls destinataires. Journal [CHAT].
 */
final class Chat {
    enum Channel { GLOBAL, TOWN, LOCAL }

    static final int LOCAL_RADIUS = 100;
    /** Plus de {@link #BURST} messages en {@link #BURST_MS} ms : ralentir. */
    private static final int BURST = 5;
    private static final long BURST_MS = 6_000;
    /** Le même message trois fois en {@link #REPEAT_MS} ms : bloqué. */
    private static final long REPEAT_MS = 30_000;
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_]{3,16})");

    private final Towns towns;
    /** Modération : faux si le joueur est réduit au silence (il est prévenu). */
    private final java.util.function.Predicate<ServerPlayer> mayTalk;
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Deque<Long>> times = new HashMap<>();
    private final Map<UUID, String> lastText = new HashMap<>();
    private final Map<UUID, Integer> repeats = new HashMap<>();
    private final Map<UUID, UUID> replyTo = new HashMap<>();

    Chat(Towns towns, java.util.function.Predicate<ServerPlayer> mayTalk) {
        this.towns = towns;
        this.mayTalk = mayTalk;
    }

    void onLeave(ServerPlayer player) {
        UUID id = player.getUUID();
        channels.remove(id);
        times.remove(id);
        lastText.remove(id);
        repeats.remove(id);
        replyTo.remove(id);
    }

    // --- Messages ordinaires (ALLOW_CHAT_MESSAGE) -----------------------------------------------

    /** Vrai : le message part dans le chat global vanilla ; faux : il a été bloqué ou envoyé sur un autre canal. */
    boolean allow(PlayerChatMessage message, ServerPlayer sender) {
        String text = message.signedContent().strip();
        if (!passesSpamFilter(sender, text)) {
            return false;
        }
        if (text.startsWith("!") && text.length() > 1) {
            send(sender, Channel.TOWN, text.substring(1).strip());
            return false;
        }
        Channel channel = channels.getOrDefault(sender.getUUID(), Channel.GLOBAL);
        if (channel != Channel.GLOBAL) {
            send(sender, channel, text);
            return false;
        }
        GeoMod.LOGGER.info("[CHAT] global {} : {}", sender.getName().getString(), text);
        notifyMentions(sender, text);
        return true;
    }

    private boolean passesSpamFilter(ServerPlayer sender, String text) {
        if (!mayTalk.test(sender)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Deque<Long> recent = times.computeIfAbsent(sender.getUUID(), k -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() > BURST_MS) {
            recent.pollFirst();
        }
        if (recent.size() >= BURST) {
            sender.sendSystemMessage(Component.literal("Doucement : un message toutes les quelques secondes.").withStyle(ChatFormatting.RED));
            return false;
        }
        String normalised = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean same = normalised.equals(lastText.get(sender.getUUID())) && !recent.isEmpty() && now - recent.peekLast() < REPEAT_MS;
        int count = same ? repeats.merge(sender.getUUID(), 1, Integer::sum) : 0;
        if (!same) {
            repeats.remove(sender.getUUID());
        }
        if (count >= 2) {
            sender.sendSystemMessage(Component.literal("Message répété : il n'est pas envoyé.").withStyle(ChatFormatting.RED));
            return false;
        }
        lastText.put(sender.getUUID(), normalised);
        recent.addLast(now);
        return true;
    }

    /** Message de ville ou local : envoyé en message système aux seuls destinataires. */
    private void send(ServerPlayer sender, Channel channel, String text) {
        if (text.isEmpty()) {
            return;
        }
        List<ServerPlayer> recipients;
        MutableComponent tag;
        if (channel == Channel.TOWN) {
            Towns.Town town = towns.townOf(sender.getUUID());
            if (town == null) {
                sender.sendSystemMessage(Component.literal("Tu n'as pas de ville : message non envoyé (/canal global).").withStyle(ChatFormatting.RED));
                return;
            }
            recipients = sender.level().getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> town.members.contains(p.getUUID())).toList();
            tag = Component.literal("[" + town.name + "] ").withStyle(ChatFormatting.GREEN);
        } else {
            recipients = sender.level().getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> p.level() == sender.level() && p.distanceToSqr(sender) <= (double) LOCAL_RADIUS * LOCAL_RADIUS).toList();
            tag = Component.literal("[Local] ").withStyle(ChatFormatting.GRAY);
        }
        Component line = Component.empty().append(tag).append(sender.getDisplayName())
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY)).append(highlight(text));
        for (ServerPlayer recipient : recipients) {
            recipient.sendSystemMessage(line);
        }
        if (channel == Channel.LOCAL && recipients.size() <= 1) {
            sender.sendSystemMessage(Component.literal("Personne à moins de " + LOCAL_RADIUS + " blocs ne t'entend.").withStyle(ChatFormatting.DARK_GRAY));
        }
        GeoMod.LOGGER.info("[CHAT] {} {} ({} destinataires) : {}", channel.name().toLowerCase(Locale.ROOT), sender.getName().getString(),
                recipients.size(), text);
        notifyMentions(sender, text);
    }

    /** Texte avec les @Pseudo en jaune. */
    private static Component highlight(String text) {
        MutableComponent out = Component.empty();
        Matcher m = MENTION.matcher(text);
        int from = 0;
        while (m.find()) {
            out.append(Component.literal(text.substring(from, m.start())));
            out.append(Component.literal(m.group()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            from = m.end();
        }
        return out.append(Component.literal(text.substring(from)));
    }

    /** Signal sonore et petit bandeau pour chaque joueur mentionné. */
    private static void notifyMentions(ServerPlayer sender, String text) {
        Matcher m = MENTION.matcher(text);
        while (m.find()) {
            ServerPlayer target = sender.level().getServer().getPlayerList().getPlayerByName(m.group(1));
            if (target != null && target != sender) {
                ding(target, SoundEvents.NOTE_BLOCK_PLING, 1.6f);
                target.sendOverlayMessage(Component.literal(sender.getName().getString() + " te mentionne dans le chat")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /** Signal sonore entendu par ce seul joueur. */
    private static void ding(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float pitch) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound, SoundSource.PLAYERS,
                player.getX(), player.getEyeY(), player.getZ(), 0.8f, pitch, player.getRandom().nextLong()));
    }

    // --- Commandes -------------------------------------------------------------------------------

    private int privateMessage(ServerPlayer sender, @Nullable ServerPlayer target, String text) {
        if (target == null) {
            sender.sendSystemMessage(Component.literal("Personne à qui répondre (ou ce joueur est parti).").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target == sender || !passesSpamFilter(sender, text)) {
            return 0;
        }
        target.sendSystemMessage(Component.literal("✉ ").withStyle(ChatFormatting.LIGHT_PURPLE).append(sender.getDisplayName())
                .append(Component.literal(" → toi : ").withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal(text))
                .withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/r "))));
        sender.sendSystemMessage(Component.literal("✉ toi → ").withStyle(ChatFormatting.LIGHT_PURPLE).append(target.getDisplayName())
                .append(Component.literal(" : ").withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal(text)));
        ding(target, SoundEvents.NOTE_BLOCK_CHIME, 1.4f);
        replyTo.put(target.getUUID(), sender.getUUID());
        replyTo.put(sender.getUUID(), target.getUUID());
        GeoMod.LOGGER.info("[CHAT] privé {} → {}", sender.getName().getString(), target.getName().getString());
        return 1;
    }

    private int setChannel(ServerPlayer player, Channel channel) {
        if (channel == Channel.TOWN && towns.townOf(player.getUUID()) == null) {
            player.sendSystemMessage(Component.literal("Tu n'as pas de ville.").withStyle(ChatFormatting.RED));
            return 0;
        }
        channels.put(player.getUUID(), channel);
        player.sendSystemMessage(Component.literal("Canal de discussion : " + switch (channel) {
            case GLOBAL -> "global (tout le serveur)";
            case TOWN -> "ville (tes habitants seulement)";
            case LOCAL -> "local (" + LOCAL_RADIUS + " blocs autour de toi)";
        } + ". Astuce : « !message » écrit à ta ville.").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("canal")
                .executes(c -> setChannel(c.getSource().getPlayerOrException(), Channel.GLOBAL))
                .then(Commands.literal("global").executes(c -> setChannel(c.getSource().getPlayerOrException(), Channel.GLOBAL)))
                .then(Commands.literal("ville").executes(c -> setChannel(c.getSource().getPlayerOrException(), Channel.TOWN)))
                .then(Commands.literal("local").executes(c -> setChannel(c.getSource().getPlayerOrException(), Channel.LOCAL))));
        dispatcher.register(Commands.literal("v").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    if (passesSpamFilter(player, StringArgumentType.getString(c, "message"))) {
                        send(player, Channel.TOWN, StringArgumentType.getString(c, "message"));
                    }
                    return 1;
                })));
        dispatcher.register(Commands.literal("l").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    if (passesSpamFilter(player, StringArgumentType.getString(c, "message"))) {
                        send(player, Channel.LOCAL, StringArgumentType.getString(c, "message"));
                    }
                    return 1;
                })));
        dispatcher.register(Commands.literal("mp").then(Commands.argument("joueur", EntityArgument.player())
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(c -> privateMessage(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur"),
                                StringArgumentType.getString(c, "message"))))));
        dispatcher.register(Commands.literal("r").then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(c -> {
                    ServerPlayer player = c.getSource().getPlayerOrException();
                    UUID to = replyTo.get(player.getUUID());
                    ServerPlayer target = to == null ? null : player.level().getServer().getPlayerList().getPlayer(to);
                    return privateMessage(player, target, StringArgumentType.getString(c, "message"));
                })));
    }
}
