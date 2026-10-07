package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Jetpack (module dorsal de la combinaison) : tant que le joueur maintient saut en l'air, il est
 * poussé vers le haut. La poussée est appliquée par le client (mouvement fluide), le serveur décompte
 * le carburant, annule les dégâts de chute et montre les flammes à tout le monde.
 */
public final class Jetpack {
    /** Accélération verticale par tick et vitesse de montée maximale (blocs/tick). */
    public static final double THRUST = 0.11;
    public static final double MAX_RISE = 0.42;
    /** Carburant rendu par un bidon d'essence (ticks de poussée). */
    private static final int REFUEL = 300;

    private Jetpack() {
    }

    /** Le joueur peut-il pousser en ce moment (côté client comme serveur) ? */
    public static boolean canThrust(Player player, boolean jumpHeld) {
        return jumpHeld && !player.onGround() && !player.isPassenger() && !player.isFallFlying()
                && !player.getAbilities().flying && !player.isSpectator() && !player.isInWater()
                && SpaceSuit.hasJetpackFuel(player);
    }

    /** Vitesse après une poussée d'un tick. */
    public static Vec3 thrust(Vec3 velocity) {
        return new Vec3(velocity.x, Math.min(MAX_RISE, Math.max(velocity.y, -0.2) + THRUST), velocity.z);
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canThrust(player, player.getLastClientInput().jump())) {
                continue;
            }
            ItemStack jetpack = SpaceSuit.jetpack(player);
            // Métier pilote : un tick de poussée sur quatre est gratuit.
            boolean saved = Jobs.is(player, Jobs.Job.PILOTE) && server.getTickCount() % 4 == 0;
            if (!player.isCreative() && !saved) {
                jetpack.setDamageValue(jetpack.getDamageValue() + 1);
            }
            player.resetFallDistance();
            int tick = server.getTickCount();
            if (tick % 10 == 0 || !SpaceSuit.hasJetpackFuel(player)) {
                SpaceSuit.changed(player); // Synchronise le carburant (HUD, poussée côté client).
            }
            if (tick % 2 == 0) {
                Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-0.35);
                player.level().sendParticles(ParticleTypes.FLAME, player.getX() + back.x, player.getY() + 0.7,
                        player.getZ() + back.z, 2, 0.08, 0.05, 0.08, 0.01);
                player.level().sendParticles(ParticleTypes.SMOKE, player.getX() + back.x, player.getY() + 0.5,
                        player.getZ() + back.z, 1, 0.1, 0.05, 0.1, 0.01);
            }
            if (tick % 8 == 0) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE,
                        SoundSource.PLAYERS, 0.25f, 1.6f);
            }
            if (!SpaceSuit.hasJetpackFuel(player)) {
                player.sendOverlayMessage(Component.literal("Jetpack à sec ! Recharge-le avec un bidon d'essence.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }
    }

    /** Clic droit avec un bidon d'essence : recharge le jetpack porté. */
    public static void refuel(Player player, InteractionHand hand) {
        ItemStack jetpack = SpaceSuit.jetpack(player);
        if (jetpack.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Bidon d'essence : clic droit sur un véhicule, ou porte un jetpack (touche J).")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        if (jetpack.getDamageValue() == 0) {
            player.sendOverlayMessage(Component.literal("Le jetpack est déjà plein.").withStyle(ChatFormatting.GRAY));
            return;
        }
        jetpack.setDamageValue(Math.max(0, jetpack.getDamageValue() - REFUEL));
        SpaceSuit.changed(player);
        player.getItemInHand(hand).consume(1, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7f, 1.2f);
        int percent = 100 - 100 * jetpack.getDamageValue() / jetpack.getMaxDamage();
        player.sendOverlayMessage(Component.literal("Jetpack rechargé : " + percent + " %").withStyle(ChatFormatting.AQUA));
    }

    static boolean isJetpack(ItemStack stack) {
        return stack.is(ModContent.JETPACK);
    }
}
