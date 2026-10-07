package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Plans de fusée : des améliorations que chaque joueur débloque en explorant, puis installe sur
 * sa fusée à l'atelier de station. Un plan ne s'achète pas : il se gagne (orbite, orbite lunaire,
 * premier module, Lune + titane).
 */
public final class Plans {
    public record Material(Supplier<Item> item, int count, String label) {
    }

    public enum Plan {
        TANK("Réservoir étendu", "Réservoir de 12 doses au lieu de 8", "Atteindre une orbite",
                p -> has(p, "orbit") || has(p, "moon_orbit"),
                List.of(new Material(() -> ModBlocks.TITANIUM_INGOT, 4, "lingots de titane"),
                        new Material(() -> ModContent.ROCKET_TANK, 1, "réservoir de fusée"))),
        ION("Moteur ionique", "Chaque trajet coûte 1 dose de moins (minimum 1)", "Atteindre l'orbite lunaire",
                p -> has(p, "moon_orbit"),
                List.of(new Material(() -> ModBlocks.TITANIUM_INGOT, 6, "lingots de titane"),
                        new Material(() -> ModBlocks.HELIUM3_SHARD, 4, "éclats d'hélium-3"))),
        CARGO("Soute", "Coffre de 27 cases dans la fusée (touche V)", "Poser un premier module de station",
                p -> Progression.get().stat(p, "modules") > 0,
                List.of(new Material(() -> Items.IRON_INGOT, 8, "lingots de fer"),
                        new Material(() -> Items.CHEST, 2, "coffres"))),
        MARS_NAV("Navigation martienne", "Indispensable pour rejoindre Mars et son orbite",
                "Marcher sur la Lune et miner 5 minerais de titane",
                p -> has(p, "moon") && Progression.get().stat(p, "titanium") >= 5,
                List.of(new Material(() -> ModBlocks.HELIUM3_SHARD, 2, "éclats d'hélium-3"),
                        new Material(() -> Items.REDSTONE_BLOCK, 1, "bloc de redstone")));

        public final String label;
        public final String effect;
        public final String unlock;
        final Predicate<ServerPlayer> condition;
        public final List<Material> materials;

        Plan(String label, String effect, String unlock, Predicate<ServerPlayer> condition, List<Material> materials) {
            this.label = label;
            this.effect = effect;
            this.unlock = unlock;
            this.condition = condition;
            this.materials = materials;
        }

        /** Bit de l'amélioration sur la fusée. */
        public int flag() {
            return 1 << ordinal();
        }

        public static Plan parse(String name) {
            for (Plan plan : values()) {
                if (plan.name().equalsIgnoreCase(name)) {
                    return plan;
                }
            }
            return null;
        }
    }

    private Plans() {
    }

    private static boolean has(ServerPlayer player, String discovery) {
        return Progression.get().hasDiscovered(player, discovery);
    }

    public static boolean knows(ServerPlayer player, Plan plan) {
        return player.isCreative() || Progression.get().knowsPlan(player, plan.name());
    }

    /** Appelé après chaque découverte ou compteur : annonce les plans nouvellement débloqués. */
    static void check(ServerPlayer player) {
        for (Plan plan : Plan.values()) {
            if (!Progression.get().knowsPlan(player, plan.name()) && plan.condition.test(player)) {
                Progression.get().learnPlan(player, plan.name());
                player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Nouveau plan")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(plan.label)
                        .withStyle(ChatFormatting.WHITE)));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                        SoundSource.PLAYERS, 1f, 1f);
                player.sendSystemMessage(Component.literal("✦ Plan de fusée débloqué : " + plan.label + " — " + plan.effect
                        + ". À installer à l'atelier de station.").withStyle(ChatFormatting.AQUA));
                GeoMod.LOGGER.info("[PLAN] {} débloque {}", player.getName().getString(), plan.label);
            }
        }
    }

    // --- Matériaux -----------------------------------------------------------------------------

    public static boolean hasMaterials(ServerPlayer player, Plan plan) {
        return player.isCreative() || plan.materials.stream().allMatch(m -> count(player, m.item().get()) >= m.count());
    }

    public static void takeMaterials(ServerPlayer player, Plan plan) {
        if (player.isCreative()) {
            return;
        }
        for (Material material : plan.materials) {
            int left = material.count();
            for (int slot = 0; slot < player.getInventory().getContainerSize() && left > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(material.item().get())) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        player.getInventory().setChanged();
    }

    public static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // --- Commande --------------------------------------------------------------------------------

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("plans").executes(c -> show(c.getSource().getPlayerOrException())));
    }

    private static int show(ServerPlayer player) {
        check(player);
        player.sendSystemMessage(Component.literal("✦ Plans de fusée").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (Plan plan : Plan.values()) {
            boolean known = knows(player, plan);
            StringBuilder cost = new StringBuilder();
            for (Material material : plan.materials) {
                cost.append(cost.isEmpty() ? "" : ", ").append(material.count()).append(' ').append(material.label());
            }
            player.sendSystemMessage(Component.literal((known ? "✓ " : "🔒 ") + plan.label + " — " + plan.effect)
                    .withStyle(known ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("   " + (known ? "Atelier de station : " + cost : "Pour le débloquer : " + plan.unlock))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }
}
