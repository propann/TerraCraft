package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Prediction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
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
    private static final int PAGE_SIZE = 6;
    private static final int MAX_LISTINGS_PER_PLAYER = 12;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Annonce. {@code stack} contient l'objet complet (enchantements, usure, contenu d'une
     * boîte…) ; les anciennes annonces n'avaient que {@code itemId} et {@code count}.
     */
    private record Listing(long id, UUID seller, String sellerName, String itemId, int count, long price, String stack) {
    }

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private Path balanceFile;
    private Path listingFile;
    private long nextId = 1;
    private MinecraftServer server;
    private final Economy economy = new Economy();

    void load(MinecraftServer server) {
        this.server = server;
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID);
        balanceFile = root.resolve("balances.json");
        listingFile = root.resolve("hotel-des-ventes.json");
        balances.clear();
        listings.clear();
        economy.load(server);
        Map<String, Long> storedBalances = JsonStore.load(balanceFile,
                json -> GSON.fromJson(json, new TypeToken<Map<String, Long>>() { }.getType()));
        if (storedBalances != null) {
            storedBalances.forEach((uuid, amount) -> balances.put(UUID.fromString(uuid), Math.max(0, amount)));
        }
        List<Listing> storedListings = JsonStore.load(listingFile,
                json -> GSON.fromJson(json, new TypeToken<List<Listing>>() { }.getType()));
        if (storedListings != null) {
            for (Listing listing : storedListings) {
                listings.put(listing.id(), listing);
                nextId = Math.max(nextId, listing.id() + 1);
            }
        }
    }

    void save() {
        if (balanceFile == null || listingFile == null) {
            return;
        }
        try {
            Map<String, Long> storedBalances = new LinkedHashMap<>();
            balances.forEach((uuid, amount) -> storedBalances.put(uuid.toString(), amount));
            JsonStore.write(balanceFile, GSON.toJson(storedBalances));
            JsonStore.write(listingFile, GSON.toJson(new ArrayList<>(listings.values())));
            economy.save();
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
                        .executes(c -> sell(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "prix"), false))))
                .then(Commands.literal("acheter").then(Commands.argument("annonce", LongArgumentType.longArg(1))
                        .executes(c -> buy(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "annonce")))))
                .then(Commands.literal("retirer").then(Commands.argument("annonce", LongArgumentType.longArg(1))
                        .executes(c -> remove(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "annonce"))))));
        dispatcher.register(Commands.literal("eco")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("donner")
                        .then(Commands.argument("joueur", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("montant", IntegerArgumentType.integer(1, 1_000_000_000))
                                        .executes(c -> give(c.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "joueur"),
                                                IntegerArgumentType.getInteger(c, "montant"))))))
                .then(Commands.literal("stats").executes(c -> stats(c.getSource()))));
        dispatcher.register(Commands.literal("comptoir").executes(c -> showShop(c.getSource().getPlayerOrException()))
                .then(Commands.argument("offre", IntegerArgumentType.integer(1, Economy.SHOP.size()))
                        .executes(c -> buyShop(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "offre") - 1))));
    }

    private long balanceOf(UUID uuid) {
        return balances.computeIfAbsent(uuid, ignored -> {
            economy.created("depart", STARTING_BALANCE);
            return STARTING_BALANCE;
        });
    }

    long balance(UUID uuid) {
        return balanceOf(uuid);
    }

    /**
     * Retire des crédits d'un compte sans les détruire (dépôt en trésorerie de ville…) ;
     * {@code sink} non nul : les crédits sortent de l'économie (frais). Faux si le solde manque.
     */
    boolean withdraw(UUID uuid, long amount, String sink) {
        if (amount <= 0 || balanceOf(uuid) < amount) {
            return false;
        }
        balances.put(uuid, balanceOf(uuid) - amount);
        if (sink != null) {
            economy.destroyed(sink, amount);
        }
        save();
        return true;
    }

    /** Création monétaire hors des comptes (récompense versée à une trésorerie de ville). */
    void createCredits(String source, long amount) {
        economy.created(source, amount);
        save();
    }

    /** Verse des crédits venant d'une trésorerie (pas de création monétaire). */
    void deposit(UUID uuid, long amount) {
        if (amount > 0) {
            balances.put(uuid, balanceOf(uuid) + amount);
            save();
        }
    }

    /** Crédits gardés hors des comptes (trésoreries des villes), pour /eco stats. */
    /** Argent bloqué hors des comptes : trésoreries des villes, primes en cours. */
    private final java.util.List<java.util.function.LongSupplier> held = new java.util.ArrayList<>();

    void treasuries(java.util.function.LongSupplier supplier) {
        held.add(supplier);
    }

    private long heldTotal() {
        return held.stream().mapToLong(java.util.function.LongSupplier::getAsLong).sum();
    }

    void credit(ServerPlayer player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        balances.put(player.getUUID(), balanceOf(player.getUUID()) + amount);
        economy.created("recompenses", amount);
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
        Tutorial.get().mark(player, "market");
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

    /** Confirmation d'une vente : refusée si l'objet en main n'est plus exactement celui annoncé. */
    private Runnable confirmSameItem(ServerPlayer player, ItemStack expected, long price) {
        return () -> {
            ItemStack now = player.getMainHandItem();
            if (now.getCount() != expected.getCount() || !ItemStack.isSameItemSameComponents(now, expected)) {
                player.sendSystemMessage(Component.literal("Vente annulée : l'objet en main a changé.").withStyle(ChatFormatting.RED));
                return;
            }
            sell(player, price, true);
        };
    }

    /** Sous ce rapport au prix moyen, la vente demande confirmation (faute de frappe, objet bradé). */
    private static final double CHEAP_RATIO = 0.5;

    private int sell(ServerPlayer player, long price, boolean confirmed) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tiens la ressource à vendre dans ta main.").withStyle(ChatFormatting.RED));
            return 0;
        }
        long average = economy.averageUnitPrice(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        long fair = average * held.getCount();
        if (!confirmed && average > 0 && price < fair * CHEAP_RATIO) {
            Confirmations.ask(player, "Vendre " + held.getHoverName().getString() + " x" + held.getCount() + " pour " + price + " crédits ?",
                    "C'est moins de la moitié du prix moyen (" + fair + " crédits pour cette quantité).", confirmSameItem(player, held.copy(), price));
            return 1;
        }
        long count = listings.values().stream().filter(l -> l.seller().equals(player.getUUID())).count();
        if (count >= MAX_LISTINGS_PER_PLAYER) {
            player.sendSystemMessage(Component.literal("Tu as atteint la limite de " + MAX_LISTINGS_PER_PLAYER + " annonces.").withStyle(ChatFormatting.RED));
            return 0;
        }
        long fee = Economy.listingFee(price);
        if (balanceOf(player.getUUID()) < fee) {
            player.sendSystemMessage(Component.literal("Il te faut " + fee + " crédits pour les frais de mise en vente (2 %).")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int amount = held.getCount();
        String encoded = encode(held);
        if (encoded == null) {
            player.sendSystemMessage(Component.literal("Cet objet ne peut pas être mis en vente.").withStyle(ChatFormatting.RED));
            return 0;
        }
        held.setCount(0);
        balances.put(player.getUUID(), balanceOf(player.getUUID()) - fee);
        economy.destroyed("frais_hdv", fee);
        Listing listing = new Listing(nextId++, player.getUUID(), player.getName().getString(), itemId, amount, price, encoded);
        listings.put(listing.id(), listing);
        save();
        Progression.get().count(player, "listings", 1, 0);
        GeoMod.LOGGER.info("[HDV] {} met en vente #{} : {} x{} pour {} crédits",
                player.getName().getString(), listing.id(), itemId, amount, price);
        player.sendSystemMessage(Component.literal("Annonce #" + listing.id() + " créée : " + displayName(itemId) + " x" + amount
                + " pour " + price + " crédits (frais : " + fee + ").").withStyle(ChatFormatting.GREEN));
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
        if (item.isEmpty()) {
            buyer.sendSystemMessage(Component.literal("Cet objet n'existe plus sur le serveur : annonce bloquée.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!hasRoom(buyer, item)) {
            buyer.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
            return 0;
        }
        // Débit, crédit et retrait de l'annonce avant de donner l'objet : aucune sortie possible
        // entre les deux, donc ni objet ni crédit dupliqué.
        balances.put(buyer.getUUID(), balanceOf(buyer.getUUID()) - listing.price());
        balances.put(listing.seller(), balanceOf(listing.seller()) + listing.price());
        listings.remove(id);
        give(buyer, item);
        economy.recordSale(listing.itemId(), listing.count(), listing.price());
        save();
        GeoMod.LOGGER.info("[HDV] {} achète #{} ({} x{}) à {} pour {} crédits", buyer.getName().getString(), id,
                listing.itemId(), listing.count(), listing.sellerName(), listing.price());
        ServerPlayer seller = server == null ? null : server.getPlayerList().getPlayer(listing.seller());
        if (seller != null) {
            seller.sendSystemMessage(Component.literal("✦ " + buyer.getName().getString() + " a acheté ton annonce #" + id
                    + " : +" + listing.price() + " crédits.").withStyle(ChatFormatting.GREEN));
        }
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

    /** Dernière vue (page, catégorie) de chaque joueur : un achat ne le renvoie pas à la page 1. */
    private final Map<UUID, Object[]> views = new HashMap<>();

    void onLeave(ServerPlayer player) {
        views.remove(player.getUUID());
    }

    void sendMarket(ServerPlayer player) {
        Object[] view = views.getOrDefault(player.getUUID(), new Object[]{1, "TOUT"});
        sendMarket(player, (int) view[0], (String) view[1]);
    }

    void sellFromClient(ServerPlayer player, long price) {
        if (price >= 1 && price <= 1_000_000_000) {
            sell(player, price, true); // L'écran du marché a déjà demandé confirmation.
        }
    }

    void buyShopFromClient(ServerPlayer player, int index) {
        if (index >= 0 && index < Economy.SHOP.size()) {
            buyShop(player, index);
        }
    }

    /** Écran de l'hôtel des ventes : annonces filtrées, prix moyens, comptoir et objet en main. */
    void sendMarket(ServerPlayer player, int page, String categoryName) {
        Tutorial.get().mark(player, "market");
        Economy.Category category = Economy.Category.parse(categoryName);
        views.put(player.getUUID(), new Object[]{page, category.name()});
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("balance", balanceOf(player.getUUID()));
        root.addProperty("category", category.name());
        root.addProperty("feePerMille", Economy.LISTING_FEE_PER_MILLE);

        Map<Economy.Category, Integer> counts = new java.util.EnumMap<>(Economy.Category.class);
        List<Listing> shown = new ArrayList<>();
        for (Listing listing : listings.values()) {
            Economy.Category of = Economy.categoryOf(sample(listing));
            counts.merge(of, 1, Integer::sum);
            if (category == Economy.Category.TOUT || category == of) {
                shown.add(listing);
            }
        }
        com.google.gson.JsonArray categories = new com.google.gson.JsonArray();
        for (Economy.Category c : Economy.Category.values()) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("key", c.name());
            o.addProperty("label", c.label);
            o.addProperty("count", c == Economy.Category.TOUT ? listings.size() : counts.getOrDefault(c, 0));
            categories.add(o);
        }
        root.add("categories", categories);

        int pages = Math.max(1, (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        root.addProperty("page", page);
        root.addProperty("pages", pages);
        com.google.gson.JsonArray rows = new com.google.gson.JsonArray();
        shown.stream().skip((long) (page - 1) * PAGE_SIZE).limit(PAGE_SIZE).forEach(listing -> {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", listing.id());
            row.addProperty("item", displayName(listing.itemId()));
            row.addProperty("itemId", listing.itemId());
            row.addProperty("count", listing.count());
            row.addProperty("price", listing.price());
            row.addProperty("seller", listing.sellerName());
            row.addProperty("own", listing.seller().equals(player.getUUID()));
            row.addProperty("average", economy.averageUnitPrice(listing.itemId()));
            rows.add(row);
        });
        root.add("listings", rows);

        com.google.gson.JsonArray shop = new com.google.gson.JsonArray();
        for (int i = 0; i < Economy.SHOP.size(); i++) {
            Economy.Offer offer = Economy.SHOP.get(i);
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("index", i);
            o.addProperty("label", offer.label());
            o.addProperty("itemId", BuiltInRegistries.ITEM.getKey(offer.item().get()).toString());
            o.addProperty("count", offer.count());
            o.addProperty("price", offer.price());
            String locked = offer.locked(player);
            if (offer.job() != null) {
                o.addProperty("label", (locked == null ? "★ " : "🔒 ") + offer.label() + " (" + offer.job().label + " " + offer.level() + ")");
            }
            if (locked != null) {
                o.addProperty("locked", locked);
            }
            shop.add(o);
        }
        root.add("shop", shop);

        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            String heldId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            root.addProperty("held", held.getHoverName().getString());
            root.addProperty("heldId", heldId);
            root.addProperty("heldCount", held.getCount());
            root.addProperty("heldAverage", economy.averageUnitPrice(heldId));
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new MarketPayload(GSON.toJson(root)));
    }

    /** Objet type d'une annonce, pour sa catégorie (sans décoder tous ses composants). */
    private static ItemStack sample(Listing listing) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(listing.itemId())));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    // --- Comptoir du serveur -------------------------------------------------------------------

    private int showShop(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("✦ Comptoir TerraCraft · solde " + balanceOf(player.getUUID()) + " crédits")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (int i = 0; i < Economy.SHOP.size(); i++) {
            Economy.Offer offer = Economy.SHOP.get(i);
            int number = i + 1;
            player.sendSystemMessage(Component.literal("[Acheter] ").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/comptoir " + number)))
                    .append(Component.literal((offer.job() == null ? "" : (offer.locked(player) == null ? "★ " : "🔒 ")) + offer.label()
                            + " x" + offer.count() + " — " + offer.price() + " crédits"
                            + (offer.job() == null ? "" : " (" + offer.job().label + " " + offer.level() + ")"))
                            .withStyle(offer.locked(player) == null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)));
        }
        return 1;
    }

    private int buyShop(ServerPlayer player, int index) {
        Economy.Offer offer = Economy.SHOP.get(index);
        String locked = offer.locked(player);
        if (locked != null) {
            player.sendSystemMessage(Component.literal("Offre " + locked + ".").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (balanceOf(player.getUUID()) < offer.price()) {
            player.sendSystemMessage(Component.literal("Solde insuffisant : il te faut " + offer.price() + " crédits.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack stack = offer.stack();
        if (!hasRoom(player, stack)) {
            player.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
            return 0;
        }
        balances.put(player.getUUID(), balanceOf(player.getUUID()) - offer.price());
        economy.destroyed("comptoir", offer.price());
        give(player, stack);
        save();
        Progression.get().count(player, "shop", 1, 0);
        GeoMod.LOGGER.info("[ECO] {} achète au comptoir : {} x{} pour {} crédits", player.getName().getString(),
                offer.label(), offer.count(), offer.price());
        player.sendSystemMessage(Component.literal("Comptoir : " + offer.label() + " x" + offer.count() + " pour "
                + offer.price() + " crédits.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    // --- Journal économique (opérateurs) -------------------------------------------------------

    private int stats(CommandSourceStack source) {
        long circulation = balances.values().stream().mapToLong(Long::longValue).sum();
        long created = economy.createdTotals().values().stream().mapToLong(Long::longValue).sum();
        long destroyed = economy.destroyedTotals().values().stream().mapToLong(Long::longValue).sum();
        long listed = listings.values().stream().mapToLong(Listing::price).sum();
        source.sendSuccess(() -> Component.literal("✦ Économie TerraCraft").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        source.sendSuccess(() -> Component.literal("Comptes : " + balances.size() + " · en circulation : " + circulation
                + " crédits · trésoreries des villes et primes : " + heldTotal()), false);
        source.sendSuccess(() -> Component.literal("Créés : " + created + " " + economy.createdTotals()).withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("Détruits : " + destroyed + " " + economy.destroyedTotals()).withStyle(ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("Annonces : " + listings.size() + " pour " + listed + " crédits demandés"), false);
        return 1;
    }

    private int remove(ServerPlayer player, long id) {
        Listing listing = listings.get(id);
        if (listing == null || !listing.seller().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Tu ne peux retirer que tes propres annonces.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack item = item(listing);
        if (!item.isEmpty() && !hasRoom(player, item)) {
            player.sendSystemMessage(Component.literal("Ton inventaire est plein.").withStyle(ChatFormatting.RED));
            return 0;
        }
        listings.remove(id);
        give(player, item);
        save();
        GeoMod.LOGGER.info("[HDV] {} retire son annonce #{}", player.getName().getString(), id);
        player.sendSystemMessage(Component.literal("Annonce #" + id + " retirée, objet rendu.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int give(CommandSourceStack source, ServerPlayer target, long amount) {
        balances.put(target.getUUID(), balanceOf(target.getUUID()) + amount);
        economy.created("admin", amount);
        save();
        GeoMod.LOGGER.info("[ECO] {} donne {} crédits à {}", source.getTextName(), amount, target.getName().getString());
        target.sendSystemMessage(Component.literal("Tu reçois " + amount + " crédits.").withStyle(ChatFormatting.GOLD));
        source.sendSuccess(() -> Component.literal(amount + " crédits ajoutés à " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Place pour au moins une partie de l'objet ; le reste éventuel tombe aux pieds du joueur. */
    private static boolean hasRoom(ServerPlayer player, ItemStack stack) {
        return player.getInventory().getFreeSlot() >= 0 || player.getInventory().getSlotWithRemainingSpace(stack) >= 0;
    }

    /** Donne tout l'objet : ce qui ne rentre pas est jeté aux pieds du joueur, jamais perdu ni dupliqué. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }

    private String encode(ItemStack stack) {
        try {
            return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), stack)
                    .getOrThrow().toString();
        } catch (RuntimeException e) {
            GeoMod.LOGGER.error("Objet impossible à enregistrer pour l'hôtel des ventes", e);
            return null;
        }
    }

    private ItemStack item(Listing listing) {
        if (listing.stack() != null) {
            try {
                return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
                        JsonParser.parseString(listing.stack())).getOrThrow();
            } catch (RuntimeException e) {
                GeoMod.LOGGER.error("Annonce #{} illisible", listing.id(), e);
                return ItemStack.EMPTY;
            }
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(listing.itemId())), listing.count());
    }

    private static String displayName(String itemId) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId))).getHoverName().getString();
        } catch (RuntimeException ignored) {
            return itemId;
        }
    }
}
