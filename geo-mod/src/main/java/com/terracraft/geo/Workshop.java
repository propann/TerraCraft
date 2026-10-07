package com.terracraft.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.Rocket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;

/**
 * Atelier de station : installe sur la fusée garée à côté les améliorations dont le joueur
 * connaît le plan, contre des matériaux. Écran graphique (clic droit sur l'atelier) ou /atelier.
 */
public final class Workshop {
    private static final int WORKSHOP_RANGE = 6;
    private static final double ROCKET_RANGE = 12;

    private Workshop() {
    }

    static boolean nearWorkshop(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-WORKSHOP_RANGE, -WORKSHOP_RANGE, -WORKSHOP_RANGE),
                center.offset(WORKSHOP_RANGE, WORKSHOP_RANGE, WORKSHOP_RANGE))) {
            if (player.level().getBlockState(pos).is(ModBlocks.STATION_WORKSHOP)) {
                return true;
            }
        }
        return false;
    }

    /** Fusée au sol la plus proche (celle où l'on est assis en priorité). */
    static Rocket nearestRocket(ServerPlayer player) {
        if (player.getVehicle() instanceof Rocket rocket) {
            return rocket;
        }
        return player.level().getEntitiesOfClass(Rocket.class, player.getBoundingBox().inflate(ROCKET_RANGE)).stream()
                .filter(rocket -> rocket.phase() == Rocket.IDLE)
                .min(Comparator.comparingDouble(rocket -> rocket.distanceToSqr(player)))
                .orElse(null);
    }

    /** Ouvre l'écran de l'atelier (clic droit sur le bloc). */
    public static void open(ServerPlayer player) {
        Plans.check(player);
        Rocket rocket = nearestRocket(player);
        JsonObject root = new JsonObject();
        root.addProperty("rocket", rocket != null);
        root.addProperty("status", rocket == null ? "Aucune fusée à moins de 12 blocs : gare-la à côté de l'atelier."
                : rocket.status().getString());
        JsonArray plans = new JsonArray();
        for (Plans.Plan plan : Plans.Plan.values()) {
            JsonObject json = new JsonObject();
            boolean known = Plans.knows(player, plan);
            boolean installed = rocket != null && rocket.hasUpgrade(plan);
            json.addProperty("id", plan.name());
            json.addProperty("label", plan.label);
            json.addProperty("effect", plan.effect);
            json.addProperty("unlock", plan.unlock);
            json.addProperty("known", known);
            json.addProperty("installed", installed);
            JsonArray materials = new JsonArray();
            for (Plans.Material material : plan.materials) {
                JsonObject m = new JsonObject();
                m.addProperty("label", material.count() + " " + material.label());
                m.addProperty("have", Plans.count(player, material.item().get()));
                m.addProperty("need", material.count());
                materials.add(m);
            }
            json.add("materials", materials);
            json.addProperty("canInstall", rocket != null && known && !installed && Plans.hasMaterials(player, plan));
            plans.add(json);
        }
        root.add("plans", plans);
        ServerPlayNetworking.send(player, new WorkshopPayload(root.toString()));
    }

    /** Installe une amélioration (écran ou commande) ; renvoie vrai si c'est fait. */
    static boolean install(ServerPlayer player, Plans.Plan plan) {
        if (!nearWorkshop(player)) {
            player.sendSystemMessage(Component.literal("Il faut être à moins de 6 blocs d'un atelier de station.").withStyle(ChatFormatting.RED));
            return false;
        }
        Rocket rocket = nearestRocket(player);
        if (rocket == null) {
            player.sendSystemMessage(Component.literal("Aucune fusée garée à moins de 12 blocs.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (rocket.hasUpgrade(plan)) {
            player.sendSystemMessage(Component.literal(plan.label + " est déjà installé sur cette fusée.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!Plans.knows(player, plan)) {
            player.sendSystemMessage(Component.literal("Plan inconnu. Pour le débloquer : " + plan.unlock + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!Plans.hasMaterials(player, plan)) {
            player.sendSystemMessage(Component.literal("Matériaux insuffisants (/plans pour la liste).").withStyle(ChatFormatting.RED));
            return false;
        }
        Plans.takeMaterials(player, plan);
        rocket.installUpgrade(plan);
        player.level().playSound(null, rocket.getX(), rocket.getY(), rocket.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1f, 0.9f);
        Progression.get().count(player, "upgrades", 1, 20);
        GeoMod.LOGGER.info("[ATELIER] {} installe {} sur une fusée en {}", player.getName().getString(), plan.label,
                player.level().dimension().identifier());
        player.sendSystemMessage(Component.literal("✦ " + plan.label + " installé : " + plan.effect + ".").withStyle(ChatFormatting.GREEN));
        return true;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atelier")
                .then(Commands.literal("installer").then(Commands.argument("plan", StringArgumentType.word())
                        .suggests((c, builder) -> {
                            for (Plans.Plan plan : Plans.Plan.values()) {
                                builder.suggest(plan.name().toLowerCase(java.util.Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(c -> {
                            Plans.Plan plan = Plans.Plan.parse(StringArgumentType.getString(c, "plan"));
                            if (plan == null) {
                                c.getSource().sendFailure(Component.literal("Plan inconnu : tank, ion, cargo ou mars_nav."));
                                return 0;
                            }
                            return install(c.getSource().getPlayerOrException(), plan) ? 1 : 0;
                        }))));
    }
}
