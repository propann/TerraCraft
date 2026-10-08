package com.terracraft.geo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import com.terracraft.geo.content.LogisticsTerminalBlock;
import com.terracraft.geo.content.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock commun des villes : 54 cases par ville, ouvertes depuis n'importe quel terminal logistique (Terre, stations,
 * bases). Le terminal ouvre toujours le stock de la ville du joueur : pas de vol possible entre villes. Données :
 * logistique.json (objets complets, composants compris).
 */
final class Logistics {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int SIZE = 54;
    private static final int REACH = 5;

    private final Towns towns;
    private final Map<String, SimpleContainer> stocks = new HashMap<>();
    private Map<String, List<String>> stored = new HashMap<>();
    private Path file;
    private MinecraftServer server;

    Logistics(Towns towns) {
        this.towns = towns;
        LogisticsTerminalBlock.opener = this::open;
    }

    void load(MinecraftServer server) {
        this.server = server;
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("logistique.json");
        stocks.clear();
        Map<String, List<String>> data = JsonStore.load(file, json -> GSON.fromJson(json, new TypeToken<Map<String, List<String>>>() { }.getType()));
        stored = data == null ? new HashMap<>() : data;
    }

    void save() {
        if (file == null) {
            return;
        }
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        stocks.forEach((key, container) -> {
            List<String> items = new ArrayList<>();
            for (ItemStack stack : container.getItems()) {
                items.add(stack.isEmpty() ? "" : ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString());
            }
            stored.put(key, items);
        });
        try {
            JsonStore.write(file, GSON.toJson(stored));
        } catch (IOException e) {
            GeoMod.LOGGER.error("Impossible d'écrire {}", file, e);
        }
    }

    private SimpleContainer stock(Towns.Town town) {
        String key = town.name.toLowerCase(java.util.Locale.ROOT);
        return stocks.computeIfAbsent(key, k -> {
            SimpleContainer container = new SimpleContainer(SIZE) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    save();
                }
            };
            RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            List<String> items = stored.getOrDefault(k, List.of());
            for (int i = 0; i < Math.min(SIZE, items.size()); i++) {
                if (!items.get(i).isEmpty()) {
                    try {
                        container.setItem(i, ItemStack.CODEC.parse(ops, JsonParser.parseString(items.get(i))).getOrThrow());
                    } catch (RuntimeException e) {
                        GeoMod.LOGGER.error("Stock de {} : case {} illisible", k, i, e);
                    }
                }
            }
            return container;
        });
    }

    private Towns.Town townOrWarn(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town == null) {
            player.sendSystemMessage(Component.literal("Le terminal logistique sert le stock commun d'une ville : /ville pour en fonder ou en rejoindre une.")
                    .withStyle(ChatFormatting.GOLD));
        }
        return town;
    }

    void open(ServerPlayer player, BlockPos pos) {
        Towns.Town town = townOrWarn(player);
        if (town == null) {
            return;
        }
        SimpleContainer stock = stock(town);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.sixRows(id, inventory, stock),
                Component.literal("Stock de " + town.name)));
    }

    /** Un terminal à portée (les commandes de stock exigent d'être devant un terminal). */
    private static boolean nearTerminal(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH))) {
            if (player.level().getBlockState(pos).is(ModBlocks.LOGISTICS_TERMINAL)) {
                return true;
            }
        }
        return false;
    }

    int list(ServerPlayer player) {
        Towns.Town town = townOrWarn(player);
        if (town == null) {
            return 0;
        }
        SimpleContainer stock = stock(town);
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
            }
        }
        player.sendSystemMessage(Component.literal("Stock de " + town.name + " : " + (counts.isEmpty() ? "vide" : counts.entrySet().stream()
                .map(e -> e.getKey() + " ×" + e.getValue()).reduce((a, b) -> a + ", " + b).orElse(""))).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    int deposit(ServerPlayer player) {
        Towns.Town town = townOrWarn(player);
        if (town == null) {
            return 0;
        }
        if (!nearTerminal(player)) {
            player.sendSystemMessage(Component.literal("Approche-toi d'un terminal logistique.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tiens en main ce que tu veux déposer.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String label = held.getHoverName().getString() + " ×" + held.getCount();
        ItemStack rest = stock(town).addItem(held.copy());
        held.setCount(rest.getCount());
        GeoMod.LOGGER.info("[LOGISTIQUE] {} dépose {} dans le stock de {} ({})", player.getName().getString(), label, town.name,
                player.level().dimension().identifier());
        player.sendSystemMessage(Component.literal("Déposé dans le stock de " + town.name + (rest.isEmpty() ? "." : " (stock plein, reste en main).")).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    int withdraw(ServerPlayer player) {
        Towns.Town town = townOrWarn(player);
        if (town == null) {
            return 0;
        }
        if (!nearTerminal(player)) {
            player.sendSystemMessage(Component.literal("Approche-toi d'un terminal logistique.").withStyle(ChatFormatting.RED));
            return 0;
        }
        SimpleContainer stock = stock(town);
        for (int i = 0; i < stock.getContainerSize(); i++) {
            ItemStack stack = stock.getItem(i);
            if (!stack.isEmpty()) {
                ItemStack taken = stock.removeItemNoUpdate(i);
                stock.setChanged();
                String label = taken.getHoverName().getString() + " ×" + taken.getCount();
                if (!player.getInventory().add(taken)) {
                    player.spawnAtLocation(player.level(), taken);
                }
                GeoMod.LOGGER.info("[LOGISTIQUE] {} retire {} du stock de {} ({})", player.getName().getString(), label, town.name,
                        player.level().dimension().identifier());
                player.sendSystemMessage(Component.literal("Retiré : " + label).withStyle(ChatFormatting.GREEN));
                return 1;
            }
        }
        player.sendSystemMessage(Component.literal("Le stock de " + town.name + " est vide.").withStyle(ChatFormatting.GRAY));
        return 0;
    }
}
