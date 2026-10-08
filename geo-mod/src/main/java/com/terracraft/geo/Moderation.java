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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modération sans être opérateur : les administrateurs nomment des modérateurs (moderateurs.json), qui disposent de
 * /mod (expulser, réduire au silence, avertir, rejoindre un joueur, lire les signalements). Tout est journalisé
 * [MOD]. Règles du serveur : /regles, montrées à chaque nouveau joueur.
 */
final class Moderation {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final List<String> RULES = List.of(
            "1. Respect : pas d'insultes, de harcèlement ni de propos haineux.",
            "2. PvE partout : on ne combat d'autres joueurs que dans les zones PvP (/pvp).",
            "3. Pas de vol ni de destruction sur le terrain d'autrui ; protège le tien avec les claims (touche M).",
            "4. Pas de triche : clients modifiés, X-ray, exploitation de bugs ou duplication. Un bug ? /signaler.",
            "5. Pas de constructions offensantes, ni de lag volontaire (machines géantes, spam d'entités).",
            "6. Les décisions des modérateurs s'appliquent ; contestation calme via /signaler.");

    private final Reports reports;
    /** Modérateurs : UUID → pseudo. */
    private final Map<String, String> moderators = new LinkedHashMap<>();
    /** Joueurs réduits au silence : UUID → fin (ms). En mémoire seulement (redémarrage = levée). */
    private final Map<String, Long> muted = new LinkedHashMap<>();
    private Path file;

    Moderation(Reports reports) {
        this.reports = reports;
    }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("moderateurs.json");
        moderators.clear();
        Map<String, String> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Map<String, String>>() { }.getType()));
        if (stored != null) {
            moderators.putAll(stored);
        }
    }

    private void save() {
        try {
            JsonStore.write(file, GSON.toJson(moderators));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    private boolean isStaff(CommandSourceStack source) {
        if (source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        return player != null && moderators.containsKey(player.getUUID().toString());
    }

    /** ALLOW_CHAT_MESSAGE : un joueur réduit au silence ne peut plus écrire dans le chat. */
    boolean allowChat(ServerPlayer player) {
        Long until = muted.get(player.getUUID().toString());
        if (until == null || until < System.currentTimeMillis()) {
            muted.remove(player.getUUID().toString());
            return true;
        }
        player.sendSystemMessage(Component.literal("Tu es réduit au silence encore " + Math.max(1, (until - System.currentTimeMillis()) / 60_000)
                + " min.").withStyle(ChatFormatting.RED));
        return false;
    }

    void sendRules(ServerPlayer player, boolean welcome) {
        player.sendSystemMessage(Component.literal("━━ Règles de TerraCraft ━━").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (String rule : RULES) {
            player.sendSystemMessage(Component.literal(rule).withStyle(ChatFormatting.WHITE));
        }
        if (welcome) {
            player.sendSystemMessage(Component.literal("En jouant, tu acceptes ces règles. Relis-les avec ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/regles").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.RunCommand("/regles")))));
        }
    }

    private static void log(CommandSourceStack source, String action) {
        GeoMod.LOGGER.info("[MOD] {} : {}", source.getTextName(), action);
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("regles").executes(c -> {
            sendRules(c.getSource().getPlayerOrException(), false);
            return 1;
        }));
        dispatcher.register(Commands.literal("mod").requires(this::isStaff)
                .then(Commands.literal("expulser").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("raison", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                            String reason = StringArgumentType.getString(c, "raison");
                            log(c.getSource(), "expulse " + target.getName().getString() + " (" + reason + ")");
                            target.connection.disconnect(Component.literal("Expulsé par la modération : " + reason));
                            return 1;
                        }))))
                .then(Commands.literal("silence").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 24 * 60)).executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                            int minutes = IntegerArgumentType.getInteger(c, "minutes");
                            if (minutes == 0) {
                                muted.remove(target.getUUID().toString());
                            } else {
                                muted.put(target.getUUID().toString(), System.currentTimeMillis() + minutes * 60_000L);
                            }
                            log(c.getSource(), (minutes == 0 ? "lève le silence de " : "réduit au silence " + minutes + " min ")
                                    + target.getName().getString());
                            target.sendSystemMessage(Component.literal(minutes == 0 ? "Tu peux de nouveau écrire dans le chat."
                                    : "Tu es réduit au silence pendant " + minutes + " min.").withStyle(ChatFormatting.RED));
                            c.getSource().sendSuccess(() -> Component.literal("Fait."), false);
                            return 1;
                        }))))
                .then(Commands.literal("avertir").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                            String message = StringArgumentType.getString(c, "message");
                            log(c.getSource(), "avertit " + target.getName().getString() + " : " + message);
                            target.sendSystemMessage(Component.literal("⚠ Avertissement de la modération : " + message)
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                            c.getSource().sendSuccess(() -> Component.literal("Avertissement envoyé."), false);
                            return 1;
                        }))))
                .then(Commands.literal("aller").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer self = c.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    log(c.getSource(), "se rend auprès de " + target.getName().getString());
                    self.teleportTo(target.level(), target.getX(), target.getY(), target.getZ(), java.util.Set.of(),
                            target.getYRot(), target.getXRot(), true);
                    return 1;
                })))
                .then(Commands.literal("signalements").executes(c -> {
                    List<String> lines = reports.recent(10);
                    c.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "Aucun signalement." : "10 derniers signalements :")
                            .withStyle(ChatFormatting.GOLD), false);
                    lines.forEach(line -> c.getSource().sendSuccess(() -> Component.literal("  " + line), false));
                    return 1;
                })));
        dispatcher.register(Commands.literal("moderateurs")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(moderators.isEmpty() ? "Aucun modérateur."
                            : "Modérateurs : " + String.join(", ", moderators.values())), false);
                    return 1;
                })
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    moderators.put(target.getUUID().toString(), target.getName().getString());
                    save();
                    log(c.getSource(), "nomme modérateur " + target.getName().getString());
                    target.sendSystemMessage(Component.literal("Tu es modérateur : /mod pour les outils.").withStyle(ChatFormatting.GREEN));
                    c.getSource().getServer().getCommands().sendCommands(target);
                    c.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " est modérateur."), true);
                    return 1;
                })))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", EntityArgument.player()).executes(c -> {
                    ServerPlayer target = EntityArgument.getPlayer(c, "joueur");
                    moderators.remove(target.getUUID().toString());
                    save();
                    log(c.getSource(), "retire le rôle de modérateur à " + target.getName().getString());
                    c.getSource().getServer().getCommands().sendCommands(target);
                    c.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " n'est plus modérateur."), true);
                    return 1;
                }))));
    }
}
