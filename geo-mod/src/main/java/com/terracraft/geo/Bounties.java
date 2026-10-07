package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Primes sur les joueurs : n'importe qui peut mettre une tête à prix (/prime). L'argent quitte son compte et reste
 * bloqué (ni créé ni détruit, compté dans /eco stats) jusqu'à ce qu'un autre joueur abatte la cible ; comme le combat
 * entre joueurs n'existe qu'en zone PvP, les primes ne se gagnent que là. Données : primes.json.
 */
final class Bounties {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final long MINIMUM = 50;

    static final class Bounty {
        String name;
        long amount;
    }

    private final AuctionHouse bank;
    private final Map<String, Bounty> bounties = new LinkedHashMap<>();
    private Path file;

    Bounties(AuctionHouse bank) {
        this.bank = bank;
        bank.treasuries(() -> bounties.values().stream().mapToLong(b -> b.amount).sum());
    }

    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("primes.json");
        bounties.clear();
        Map<String, Bounty> stored = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Map<String, Bounty>>() { }.getType()));
        if (stored != null) {
            bounties.putAll(stored);
        }
    }

    private void save() {
        try {
            JsonStore.write(file, GSON.toJson(bounties));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    /** Mort d'un joueur tué par un autre : la prime revient au tueur. */
    void onPlayerKilled(ServerPlayer victim, ServerPlayer killer) {
        if (killer == victim) {
            return;
        }
        Bounty bounty = bounties.remove(victim.getUUID().toString());
        if (bounty == null) {
            return;
        }
        save();
        bank.deposit(killer.getUUID(), bounty.amount);
        GeoMod.LOGGER.info("[PRIME] {} touche {} crédits pour {}", killer.getName().getString(), bounty.amount, victim.getName().getString());
        victim.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("☠ " + killer.getName().getString()
                + " empoche la prime de " + bounty.amount + " crédits sur " + victim.getName().getString() + " !")
                .withStyle(ChatFormatting.GOLD), false);
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("primes").executes(c -> list(c.getSource())));
        dispatcher.register(Commands.literal("prime")
                .executes(c -> list(c.getSource()))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.argument("montant", IntegerArgumentType.integer((int) MINIMUM, 1_000_000))
                                .executes(c -> place(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "joueur"),
                                        IntegerArgumentType.getInteger(c, "montant"))))));
    }

    private int place(ServerPlayer sponsor, ServerPlayer target, long amount) {
        if (sponsor == target) {
            sponsor.sendSystemMessage(Component.literal("Pas de prime sur sa propre tête.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!bank.withdraw(sponsor.getUUID(), amount, null)) {
            sponsor.sendSystemMessage(Component.literal("Solde insuffisant pour cette prime.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Bounty bounty = bounties.computeIfAbsent(target.getUUID().toString(), k -> new Bounty());
        bounty.name = target.getName().getString();
        bounty.amount += amount;
        save();
        GeoMod.LOGGER.info("[PRIME] {} met {} crédits sur {} (total {})", sponsor.getName().getString(), amount, bounty.name, bounty.amount);
        sponsor.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("☠ Prime : " + bounty.amount
                + " crédits sur la tête de " + bounty.name + " (à gagner en zone PvP).").withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private int list(CommandSourceStack source) {
        if (bounties.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucune prime en cours. /prime <joueur> <montant> (minimum " + MINIMUM + ")")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Primes (à gagner en zone PvP, /pvp) :").withStyle(ChatFormatting.GOLD), false);
        bounties.values().stream().sorted((a, b) -> Long.compare(b.amount, a.amount)).forEach(b ->
                source.sendSuccess(() -> Component.literal("  ☠ " + b.name + " — " + b.amount + " crédits").withStyle(ChatFormatting.RED), false));
        return 1;
    }
}
