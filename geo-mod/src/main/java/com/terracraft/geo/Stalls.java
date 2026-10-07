package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.terracraft.geo.content.MarketStallBlock;
import com.terracraft.geo.content.MarketStallBlockEntity;
import com.terracraft.geo.content.ModBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Magasins de joueurs : l'étal vend l'objet de son stock à prix fixe, même vendeur hors ligne. Le paiement passe du
 * compte de l'acheteur à celui du vendeur (aucun crédit créé). Propriétaire : clic droit = stock, /etal prix, /etal
 * ajouter. Client : clic droit = offre, Maj + clic droit = achat d'un exemplaire, /etal acheter <n>. Seul le
 * propriétaire (ou un opérateur en créatif) peut casser l'étal ; son stock tombe alors au sol.
 */
final class Stalls {
    private static final int REACH = 4;

    private final AuctionHouse bank;

    Stalls(AuctionHouse bank) {
        this.bank = bank;
        MarketStallBlock.interaction = this::interact;
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(blockEntity instanceof MarketStallBlockEntity stall) || !(player instanceof ServerPlayer server)) {
                return true;
            }
            boolean allowed = stall.owner() == null || stall.owner().equals(player.getUUID())
                    || player.isCreative() && server.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
            if (!allowed) {
                server.sendOverlayMessage(Component.literal("Cet étal appartient à " + stall.ownerName() + ".").withStyle(ChatFormatting.RED));
                return false;
            }
            Containers.dropContents(level, pos, stall.stock());
            return true;
        });
    }

    private void interact(ServerPlayer player, MarketStallBlockEntity stall) {
        if (stall.owner() == null) {
            stall.setOwner(player.getUUID(), player.getName().getString());
            player.sendSystemMessage(Component.literal("Cet étal est maintenant à toi : clic droit pour le garnir, /etal prix <crédits>.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        if (stall.owner().equals(player.getUUID())) {
            if (player.isShiftKeyDown()) {
                player.sendSystemMessage(status(stall));
                return;
            }
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, stall.stock()),
                    Component.literal("Stock de l'étal")));
            return;
        }
        if (player.isShiftKeyDown()) {
            buy(player, stall, 1);
        } else {
            player.sendSystemMessage(status(stall));
        }
    }

    private static Component status(MarketStallBlockEntity stall) {
        ItemStack offer = stall.offer();
        if (offer.isEmpty() || stall.price() <= 0) {
            return Component.literal("Étal de " + stall.ownerName() + " : " + (offer.isEmpty() ? "vide." : "pas encore de prix."))
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.literal("Étal de " + stall.ownerName() + " : " + offer.getHoverName().getString() + " à " + stall.price()
                        + " crédits l'unité (" + stall.available() + " en stock). Maj + clic droit : acheter 1 · /etal acheter <n>")
                .withStyle(ChatFormatting.GOLD);
    }

    private int buy(ServerPlayer buyer, MarketStallBlockEntity stall, int amount) {
        if (stall.owner() == null || stall.owner().equals(buyer.getUUID())) {
            buyer.sendSystemMessage(Component.literal("Tu ne peux pas acheter à ton propre étal.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack offer = stall.offer();
        if (offer.isEmpty() || stall.price() <= 0) {
            buyer.sendSystemMessage(status(stall));
            return 0;
        }
        if (stall.available() < amount) {
            buyer.sendSystemMessage(Component.literal("Stock insuffisant : " + stall.available() + " disponible(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        long total = stall.price() * amount;
        if (!bank.withdraw(buyer.getUUID(), total, null)) {
            buyer.sendSystemMessage(Component.literal("Il te faut " + total + " crédits.").withStyle(ChatFormatting.RED));
            return 0;
        }
        bank.deposit(stall.owner(), total);
        ItemStack bought = stall.take(amount);
        String label = bought.getHoverName().getString() + " x" + bought.getCount();
        if (!buyer.getInventory().add(bought)) {
            buyer.spawnAtLocation(buyer.level(), bought); // Inventaire plein : l'achat tombe aux pieds de l'acheteur.
        }
        buyer.level().playSound(null, stall.getBlockPos(), SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.8f, 1.2f);
        GeoMod.LOGGER.info("[ETAL] {} achète {} à {} pour {} crédits", buyer.getName().getString(), label, stall.ownerName(), total);
        buyer.sendSystemMessage(Component.literal("Acheté : " + label + " pour " + total + " crédits.").withStyle(ChatFormatting.GREEN));
        ServerPlayer seller = buyer.level().getServer().getPlayerList().getPlayer(stall.owner());
        if (seller != null) {
            seller.sendSystemMessage(Component.literal("💰 " + buyer.getName().getString() + " t'achète " + label + " : +" + total
                    + " crédits.").withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    /** Étal le plus proche à moins de 4 blocs (les commandes n'exigent pas de viser). */
    private static MarketStallBlockEntity nearest(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        MarketStallBlockEntity best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-REACH, -REACH, -REACH), center.offset(REACH, REACH, REACH))) {
            if (level.getBlockState(pos).is(ModBlocks.MARKET_STALL) && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
                double d = pos.distSqr(center);
                if (d < distance) {
                    distance = d;
                    best = stall;
                }
            }
        }
        return best;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("etal")
                .executes(c -> withStall(c.getSource(), (player, stall) -> {
                    player.sendSystemMessage(status(stall));
                    return 1;
                }))
                .then(Commands.literal("prix").then(Commands.argument("credits", IntegerArgumentType.integer(1, 1_000_000))
                        .executes(c -> withOwnStall(c.getSource(), (player, stall) -> {
                            stall.setPrice(IntegerArgumentType.getInteger(c, "credits"));
                            GeoMod.LOGGER.info("[ETAL] {} fixe le prix à {} en {}", player.getName().getString(), stall.price(), stall.getBlockPos());
                            player.sendSystemMessage(status(stall));
                            return 1;
                        }))))
                .then(Commands.literal("ajouter").executes(c -> withOwnStall(c.getSource(), (player, stall) -> {
                    ItemStack held = player.getMainHandItem();
                    if (held.isEmpty()) {
                        player.sendSystemMessage(Component.literal("Tiens en main ce que tu veux mettre en vente.").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    ItemStack rest = stall.stock().addItem(held.copy());
                    held.setCount(rest.getCount());
                    player.sendSystemMessage(status(stall));
                    return 1;
                })))
                .then(Commands.literal("acheter").executes(c -> withStall(c.getSource(), (player, stall) -> buy(player, stall, 1)))
                        .then(Commands.argument("quantite", IntegerArgumentType.integer(1, 64 * 27))
                                .executes(c -> withStall(c.getSource(), (player, stall) ->
                                        buy(player, stall, IntegerArgumentType.getInteger(c, "quantite")))))));
    }

    private interface StallAction {
        int run(ServerPlayer player, MarketStallBlockEntity stall);
    }

    private static int withStall(CommandSourceStack source, StallAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketStallBlockEntity stall = nearest(player);
        if (stall == null) {
            source.sendFailure(Component.literal("Aucun étal à moins de " + REACH + " blocs."));
            return 0;
        }
        return action.run(player, stall);
    }

    private static int withOwnStall(CommandSourceStack source, StallAction action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return withStall(source, (player, stall) -> {
            if (stall.owner() == null) {
                stall.setOwner(player.getUUID(), player.getName().getString());
            }
            if (!stall.owner().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Cet étal appartient à " + stall.ownerName() + ".").withStyle(ChatFormatting.RED));
                return 0;
            }
            return action.run(player, stall);
        });
    }
}
