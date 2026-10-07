package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Économie légère et hôtel des ventes persistant, sans dépendance client supplémentaire. */
public final class AuctionHouse {
    private static final long STARTING_BALANCE = 1_000;
    private static final int PAGE_SIZE = 8;
    private static final int MAX_LISTINGS_PER_PLAYER = 12;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Listing(long id, UUID seller, String sellerName, String itemId, int count, long price) {
    }

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private Path balanceFile;
    private Path listingFile;
    private long nextId = 1;

    void load(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID);
        balanceFile = root.resolve("balances.json");
        listingFile = root.resolve("hotel-des-ventes.json");
        balances.clear();
        listings.clear();
        if (Files.isRegularFile(balanceFile)) {
            try {
                Map<String, Double> stored = GSON.fromJson(Files.readString(balanceFile), new TypeToken<Map<String, Double>>() { }.getType());
                if (stored != null) {
                    stored.forEach((uuid, amount) -> balances.put(UUID.fromString(uuid), Math.max(0, amount.longValue())));
                }
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.error("Impossible de lire {}", balanceFile, e);
            }
        }
        if (Files.isRegularFile(listingFile)) {
            try {
                List<Listing> stored = GSON.fromJson(Files.readString(listingFile), new TypeToken<List<Listing>>() { }.getType());
                if (stored != null) {
                    for (Listing listing : stored) {
                        listings.put(listing.id(), listing);
                        nextId = Math.max(nextId, listing.id() + 1);
                    }
                }
            } catch (IOException | RuntimeException e) {
                GeoMod.LOGGER.error("Impossible de lire {}", listingFile, e);
            }
        }
    }

    void save() {
        if (balanceFile == null || listingFile == null) {
            return;
        }
        try {
            Files.createDirectories(balanceFile.getParent());
            Map<String, Long> storedBalances = new LinkedHashMap<>();
            balances.forEach((uuid, amount) -> storedBalances.put(uuid.toString(), amount));
            Files.writeString(balanceFile, GSON.toJson(storedBalances));
            Files.writeString(listingFile, GSON.toJson(new ArrayList<>(listings.values())));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'enregistrer l'économie TerraCraft", e);
        }
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("argent")
                .executes(c -> balance(c.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("hdv")
                .executes(c -> show(c.getSource().getPlayerOrException(), 1))
                .then(Commands.literal("voir").executes(c -> show(c.getSource().getPlayerOrException(), 1)))
                .then(Commands.literal("page").then(Commands.argument("numero", IntegerArgumentType.integer(1))
                        .executes(c -> show(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "numero")))))
                .then(Commands.literal("vendre").then(Commands.argument("prix", IntegerArgumentType.integer(1, 1_000_000_000))
                        .executes(c -> sell(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "prix")))))
                .then(Commands.literal("acheter").then(Commands.argument("annonce", LongArgumentType.longArg(1))
                        .executes(c -> buy(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "annonce")))))
                .then(Commands.literal("retirer").then(Commands.argument("annonce", LongArgumentType.longArg(1))
                        .executes(c -> remove(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "annonce"))))));
        dispatcher.register(Commands.literal("eco")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("donner")
                        .then(Commands.argument("joueur", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("montant", IntegerArgumentType.integer(1, 1_000_000_000))
                                        .executes(c -> give(c.getSource().getPlayerOrException(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "joueur"),
                                                IntegerArgumentType.getInteger(c, "montant")))))));
    }

    private long balanceOf(UUID uuid) {
        return balances.computeIfAbsent(uuid, ignored -> STARTING_BALANCE);
    }

    void credit(ServerPlayer player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        balances.put(player.getUUID(), balanceOf(player.getUUID()) + amount);
        save();
        player.sendSystemMessage(Component.literal("✦ +" + amount + " crédits — " + reason)
                .withStyle(ChatFormatting.GREEN));
    }

    private int balance(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Compte TerraCraft : " + balanceOf(player.getUUID()) + " crédits")
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private int show(ServerPlayer player, int page) {
        List<Listing> all = new ArrayList<>(listings.values());
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        final int shownPage = page;
        int from = (page - 1) * PAGE_SIZE;
        player.sendSystemMessage(Component.literal("✦ Hôtel des ventes  ·  page " + page + "/" + pages)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (all.isEmpty()) {
            player.sendSystemMessage(Component.literal("Aucune annonce pour le moment. Tiens un objet et fais /hdv vendre <prix>.")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }
        for (int index = from; index < Math.min(all.size(), from + PAGE_SIZE); index++) {
            Listing listing = all.get(index);
            String label = displayName(listing.itemId()) + " x" + listing.count() + " · " + listing.price() + " crédits · " + listing.sellerName();
            var row = Component.literal("[#" + listing.id() + "] " + label + " ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("[Acheter]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/hdv acheter " + listing.id()))));
            if (listing.seller().equals(player.getUUID())) {
                row.append(Component.literal(" [Retirer]").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.RunCommand("/hdv retirer " + listing.id()))));
            }
            player.sendSystemMessage(row);
        }
        Component navigation = Component.literal("[« précédent]").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.RunCommand("/hdv page " + Math.max(1, shownPage - 1))))
                .append(Component.literal("  "))
                .append(Component.literal("[suivant »]").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                        .withClickEvent(new ClickEvent.RunCommand("/hdv page " + Math.min(pages, shownPage + 1)))));
        player.sendSystemMessage(navigation);
        return 1;
    }

    private int sell(ServerPlayer player, long price) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tiens la ressource à vendre dans ta main.").withStyle(ChatFormatting.RED));
            return 0;
        }
        long count = listings.values().stream().filter(l -> l.seller().equals(player.getUUID())).count();
        if (count >= MAX_LISTINGS_PER_PLAYER) {
            player.sendSystemMessage(Component.literal("Tu as atteint la limite de " + MAX_LISTINGS_PER_PLAYER + " annonces.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int amount = held.getCount();
        held.setCount(0);
        Listing listing = new Listing(nextId++, player.getUUID(), player.getName().getString(), itemId, amount, price);
        listings.put(listing.id(), listing);
        save();
        player.sendSystemMessage(Component.literal("Annonce #" + listing.id() + " créée : " + displayName(itemId) + " x" + amount
                + " pour " + price + " crédits.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int buy(ServerPlayer buyer, long id) {
        Listing listing = listings.get(id);
        if (listing == null) {
            buyer.sendSystemMessage(Component.literal("Cette annonce n'existe plus.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (listing.seller().equals(buyer.getUUID())) {
            buyer.sendSystemMessage(Component.literal("Tu ne peux pas acheter ta propre annonce.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (balanceOf(buyer.getUUID()) < listing.price()) {
            buyer.sendSystemMessage(Component.literal("Solde insuffisant : il te faut " + listing.price() + " crédits.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack item = item(listing);
        if (!buyer.getInventory().add(item)) {
            buyer.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
            return 0;
        }
        balances.put(buyer.getUUID(), balanceOf(buyer.getUUID()) - listing.price());
        balances.put(listing.seller(), balanceOf(listing.seller()) + listing.price());
        listings.remove(id);
        save();
        buyer.sendSystemMessage(Component.literal("Achat confirmé : " + displayName(listing.itemId()) + " x" + listing.count() + ".")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    void buyFromClient(ServerPlayer buyer, long id) {
        buy(buyer, id);
    }

    void removeFromClient(ServerPlayer player, long id) {
        remove(player, id);
    }

    void sendMarket(ServerPlayer player) {
        sendMarket(player, 1);
    }

    void sendMarket(ServerPlayer player, int page) {
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("balance", balanceOf(player.getUUID()));
        int pages = Math.max(1, (listings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        root.addProperty("page", page);
        root.addProperty("pages", pages);
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        listings.values().stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(listing -> {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", listing.id());
            row.addProperty("item", displayName(listing.itemId()));
            row.addProperty("count", listing.count());
            row.addProperty("price", listing.price());
            row.addProperty("seller", listing.sellerName());
            row.addProperty("own", listing.seller().equals(player.getUUID()));
            rows.add(row);
        });
        root.add("listings", rows);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new MarketPayload(GSON.toJson(root)));
    }

    private int remove(ServerPlayer player, long id) {
        Listing listing = listings.get(id);
        if (listing == null || !listing.seller().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Tu ne peux retirer que tes propres annonces.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!player.getInventory().add(item(listing))) {
            player.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
            return 0;
        }
        listings.remove(id);
        save();
        player.sendSystemMessage(Component.literal("Annonce #" + id + " retirée, objet rendu.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int give(ServerPlayer source, ServerPlayer target, long amount) {
        balances.put(target.getUUID(), balanceOf(target.getUUID()) + amount);
        save();
        target.sendSystemMessage(Component.literal("Tu reçois " + amount + " crédits.").withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal("" + amount + " crédits ajoutés à " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static ItemStack item(Listing listing) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(listing.itemId)), listing.count());
        return stack;
    }

    private static String displayName(String itemId) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId))).getHoverName().getString();
        } catch (RuntimeException ignored) {
            return itemId;
        }
    }
}
