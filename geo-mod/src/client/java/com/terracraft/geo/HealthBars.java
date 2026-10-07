package com.terracraft.geo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Barres de vie au-dessus des créatures : toujours pour les ennemis à moins de 24 blocs, et pour les animaux blessés
 * ou visés. Dix cases colorées (vert, jaune, rouge) et les points de vie restants. Affichées sous le nom, à la place
 * de la ligne de score (inutilisée sur ce serveur).
 */
public final class HealthBars {
    private static final double RANGE_SQ = 24 * 24;

    private HealthBars() {
    }

    public static Component bar(LivingEntity entity, double distanceSq) {
        if (entity instanceof Player || entity instanceof ArmorStand || !entity.isAlive() || entity.isInvisible()
                || distanceSq > RANGE_SQ) {
            return null;
        }
        float health = entity.getHealth();
        float max = Math.max(1, entity.getMaxHealth());
        boolean enemy = entity instanceof Enemy;
        boolean aimed = Minecraft.getInstance().crosshairPickEntity == entity;
        if (!enemy && !aimed && health >= max) {
            return null;
        }
        float ratio = Mth.clamp(health / max, 0, 1);
        int filled = Math.max(1, Math.round(ratio * 10));
        int colour = ratio > 0.6f ? 0x55E06A : ratio > 0.3f ? 0xF2C230 : 0xF04848;
        MutableComponent bar = Component.literal(enemy ? "☠ " : "❤ ").withColor(enemy ? 0xF04848 : 0xF07A9A);
        bar.append(Component.literal("■".repeat(filled)).withColor(colour));
        bar.append(Component.literal("■".repeat(10 - filled)).withColor(0x3A3A3A));
        bar.append(Component.literal(" " + Mth.ceil(health)).withColor(0xE6E6E6));
        return bar;
    }
}
