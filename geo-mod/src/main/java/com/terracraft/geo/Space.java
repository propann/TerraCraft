package com.terracraft.geo;

import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * La Lune côté serveur : gravité réduite (≈ 1/6) et oxygène. Le casque spatial porte la
 * réserve d'oxygène (sa durabilité, une unité par seconde) ; sans casque ou réserve vide,
 * le joueur suffoque. Les bouteilles d'oxygène rechargent le casque porté.
 */
public final class Space {
    public static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "moon"));
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "orbit"));
    public static final byte EARTH = 0;
    public static final byte MOON_ID = 1;
    public static final byte ORBIT_ID = 2;
    /** Altitude de la plateforme d'amarrage en orbite. */
    public static final int DOCK_Y = 150;
    /** Rayon de la bulle d'air d'un distributeur d'oxygène. */
    private static final int AIR_RADIUS = 8;
    private static final Identifier MOON_GRAVITY = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "moon_gravity");
    private static final Identifier MOON_FALL = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "moon_fall");

    private Space() {
    }

    public static boolean isMoon(Level level) {
        return level.dimension() == MOON;
    }

    public static boolean isSpace(Level level) {
        return level.dimension() == MOON || level.dimension() == ORBIT;
    }

    public static byte id(Level level) {
        return level.dimension() == MOON ? MOON_ID : level.dimension() == ORBIT ? ORBIT_ID : EARTH;
    }

    public static String name(byte id) {
        return id == MOON_ID ? "la Lune" : id == ORBIT_ID ? "l'orbite" : "la Terre";
    }

    public static byte defaultDestination(Level level) {
        return id(level) == EARTH ? MOON_ID : EARTH;
    }

    /** Destination suivante, en sautant le monde où l'on se trouve. */
    public static byte nextDestination(Level level, byte current) {
        byte next = current;
        do {
            next = (byte) ((next + 1) % 3);
        } while (next == id(level));
        return next;
    }

    public static @Nullable ServerLevel level(MinecraftServer server, byte id) {
        return switch (id) {
            case MOON_ID -> server.getLevel(MOON);
            case ORBIT_ID -> server.getLevel(ORBIT);
            default -> server.overworld();
        };
    }

    /** Plateforme d'amarrage 9×9 en orbite (construite une fois), avec lampes et distributeur d'oxygène. */
    public static void buildDock(ServerLevel orbit, int x, int z) {
        orbit.getChunk(x >> 4, z >> 4);
        if (!orbit.getBlockState(new BlockPos(x, DOCK_Y, z)).isAir()) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                orbit.setBlock(new BlockPos(x + dx, DOCK_Y, z + dz),
                        (corner ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_FLOOR).defaultBlockState(), 3);
            }
        }
        orbit.setBlock(new BlockPos(x + 3, DOCK_Y + 1, z), ModBlocks.OXYGEN_DISTRIBUTOR.defaultBlockState(), 3);
    }

    /** Vrai si un distributeur d'oxygène est à moins de 8 blocs. */
    public static boolean hasAir(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-AIR_RADIUS, -AIR_RADIUS, -AIR_RADIUS),
                center.offset(AIR_RADIUS, AIR_RADIUS, AIR_RADIUS))) {
            if (player.level().getBlockState(pos).is(ModBlocks.OXYGEN_DISTRIBUTOR)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable ServerLevel moon(MinecraftServer server) {
        return server.getLevel(MOON);
    }

    static void tick(MinecraftServer server) {
        boolean everySecond = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inSpace = isSpace(player.level());
            gravity(player, inSpace, player.level().dimension() == ORBIT ? -0.92 : -0.83);
            if (inSpace && everySecond && !player.isCreative() && !player.isSpectator()) {
                breathe(player);
            }
        }
    }

    private static void gravity(ServerPlayer player, boolean onMoon, double amount) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        AttributeInstance fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (gravity == null || fall == null) {
            return;
        }
        AttributeModifier current = gravity.getModifier(MOON_GRAVITY);
        if (onMoon && (current == null || current.amount() != amount)) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(MOON_GRAVITY, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            fall.addOrUpdateTransientModifier(new AttributeModifier(MOON_FALL, 15, AttributeModifier.Operation.ADD_VALUE));
        } else if (!onMoon && gravity.hasModifier(MOON_GRAVITY)) {
            gravity.removeModifier(MOON_GRAVITY);
            fall.removeModifier(MOON_FALL);
        }
    }

    private static void breathe(ServerPlayer player) {
        if (hasAir(player)) {
            return; // Bulle d'air d'un distributeur : on respire sans consommer.
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean suited = helmet.is(ModContent.SPACE_HELMET);
        if (suited && helmet.getDamageValue() < helmet.getMaxDamage() - 1) {
            // Palier « Poumons d'acier » : une unité toutes les deux secondes.
            boolean skip = Progression.get().hasSteelLungs(player) && (player.level().getServer().getTickCount() / 20) % 2 == 0;
            if (!skip) {
                helmet.setDamageValue(helmet.getDamageValue() + 1);
            }
            int percent = 100 - 100 * helmet.getDamageValue() / helmet.getMaxDamage();
            if (percent <= 20 || player.getRandom().nextInt(5) == 0) {
                player.sendOverlayMessage(Component.literal("Oxygène " + percent + " %")
                        .withStyle(percent <= 20 ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
            return;
        }
        player.sendOverlayMessage(Component.literal(suited ? "Plus d'oxygène ! Recharge ton casque." : "Pas d'air ! Il faut un casque spatial.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        player.hurtServer(player.level(), player.damageSources().drown(), 2.0f);
    }

    public static void announceArrival(ServerPlayer player, byte destination) {
        if (destination == MOON_ID) {
            Progression.get().discover(player, "moon");
        } else if (destination == ORBIT_ID) {
            Progression.get().discover(player, "orbit");
        }
        String title = switch (destination) {
            case MOON_ID -> "La Lune";
            case ORBIT_ID -> "Orbite terrestre";
            default -> "La Terre";
        };
        String subtitle = switch (destination) {
            case MOON_ID -> "Gravité 1/6 — surveille ton oxygène";
            case ORBIT_ID -> "Plateforme d'amarrage — construis ta station";
            default -> "Bon retour parmi les ruines";
        };
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
    }
}
