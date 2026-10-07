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
    public static final ResourceKey<Level> MARS = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "mars"));
    public static final ResourceKey<Level> MARS_ORBIT = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "mars_orbit"));
    public static final ResourceKey<Level> MOON_ORBIT = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "moon_orbit"));
    public static final byte EARTH = 0;
    public static final byte MOON_ID = 1;
    public static final byte ORBIT_ID = 2;
    public static final byte MARS_ID = 3;
    public static final byte MARS_ORBIT_ID = 4;
    public static final byte MOON_ORBIT_ID = 11;
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
        return level.dimension() == MOON || isOrbit(level) || level.dimension() == MARS;
    }

    /** Orbite (terrestre, lunaire ou martienne) : vide, quai d'amarrage, stations. */
    public static boolean isOrbit(Level level) {
        return level.dimension() == ORBIT || level.dimension() == MOON_ORBIT || level.dimension() == MARS_ORBIT;
    }

    public static boolean isOrbitId(byte id) {
        return id == ORBIT_ID || id == MOON_ORBIT_ID || id == MARS_ORBIT_ID;
    }

    /**
     * Carburant de fusée nécessaire entre deux mondes : plus court chemin sur les trajets
     * Terre — orbite terrestre — orbite lunaire — Lune, et orbites — orbite martienne — Mars.
     * Quitter la Terre coûte cher, sauter d'une orbite à l'autre beaucoup moins : les stations
     * servent d'escales pour refaire le plein. Réservoir : {@link com.terracraft.geo.content.Rocket#MAX_FUEL}.
     */
    public static int travelCost(byte from, byte to) {
        byte[] nodes = {EARTH, ORBIT_ID, MOON_ORBIT_ID, MOON_ID, MARS_ORBIT_ID, MARS_ID};
        int[][] edges = {{EARTH, ORBIT_ID, 3}, {ORBIT_ID, MOON_ORBIT_ID, 2}, {MOON_ORBIT_ID, MOON_ID, 1},
                {ORBIT_ID, MARS_ORBIT_ID, 5}, {MOON_ORBIT_ID, MARS_ORBIT_ID, 4}, {MARS_ORBIT_ID, MARS_ID, 2}};
        java.util.Map<Byte, Integer> best = new java.util.HashMap<>();
        for (byte node : nodes) {
            best.put(node, Integer.MAX_VALUE / 2);
        }
        best.put(from, 0);
        for (int round = 0; round < nodes.length; round++) {
            for (int[] edge : edges) {
                byte a = (byte) edge[0];
                byte b = (byte) edge[1];
                best.put(b, Math.min(best.get(b), best.get(a) + edge[2]));
                best.put(a, Math.min(best.get(a), best.get(b) + edge[2]));
            }
        }
        return best.getOrDefault(to, Integer.MAX_VALUE / 2);
    }

    /** Destination réservée aux pilotes qui ont déjà marché sur la Lune (Mars et son orbite). */
    public static boolean requiresMoon(byte destination) {
        return destination == MARS_ID || destination == MARS_ORBIT_ID;
    }

    public static byte id(Level level) {
        if (level.dimension() == MOON) return MOON_ID;
        if (level.dimension() == ORBIT) return ORBIT_ID;
        if (level.dimension() == MARS) return MARS_ID;
        if (level.dimension() == MARS_ORBIT) return MARS_ORBIT_ID;
        if (level.dimension() == MOON_ORBIT) return MOON_ORBIT_ID;
        return EARTH;
    }

    public static String name(byte id) {
        return SolarSystem.byId(id).displayName();
    }

    public static byte defaultDestination(Level level) {
        return id(level) == EARTH ? ORBIT_ID : EARTH; // Depuis la Terre : la station orbitale d'abord.
    }

    /** Destination suivante, en sautant le monde où l'on se trouve. */
    public static byte nextDestination(Level level, byte current) {
        return SolarSystem.nextActive(current, id(level));
    }

    public static @Nullable ServerLevel level(MinecraftServer server, byte id) {
        return switch (id) {
            case MOON_ID -> server.getLevel(MOON);
            case ORBIT_ID -> server.getLevel(ORBIT);
            case MARS_ID -> server.getLevel(MARS);
            case MARS_ORBIT_ID -> server.getLevel(MARS_ORBIT);
            case MOON_ORBIT_ID -> server.getLevel(MOON_ORBIT);
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

    /** Vrai si le joueur porte une combinaison fonctionnelle pour le décollage. */
    public static boolean hasLifeSupport(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        return SpaceSuit.hasAirSupply(player);
    }

    public static @Nullable ServerLevel moon(MinecraftServer server) {
        return server.getLevel(MOON);
    }

    static void tick(MinecraftServer server) {
        boolean everySecond = server.getTickCount() % 20 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean inSpace = isSpace(player.level());
            boolean orbit = isOrbit(player.level());
            // Bottes magnétiques : en orbite, on colle au sol comme sur la Lune au lieu de flotter.
            double gravity = orbit ? (SpaceSuit.hasBoots(player) ? -0.75 : -0.92)
                    : player.level().dimension() == MARS ? -0.62 : -0.83;
            gravity(player, inSpace, gravity, SpaceSuit.hasBoots(player) ? 1000 : 15);
            if (inSpace && everySecond && !player.isCreative() && !player.isSpectator()) {
                breathe(player);
            }
            if (everySecond && player.level().dimension() == MOON && player.getBlockY() < 40
                    && !Progression.get().hasDiscovered(player, "alien_sanctuary")
                    && com.terracraft.geo.world.MoonUnderground.inHall(player.blockPosition())) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal("Sanctuaire extraterrestre").withStyle(ChatFormatting.LIGHT_PURPLE)));
                Progression.get().discover(player, "alien_sanctuary");
            }
        }
    }

    private static void gravity(ServerPlayer player, boolean onMoon, double amount, double safeFall) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        AttributeInstance fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (gravity == null || fall == null) {
            return;
        }
        AttributeModifier current = gravity.getModifier(MOON_GRAVITY);
        AttributeModifier currentFall = fall.getModifier(MOON_FALL);
        if (onMoon && (current == null || current.amount() != amount || currentFall == null || currentFall.amount() != safeFall)) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(MOON_GRAVITY, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            fall.addOrUpdateTransientModifier(new AttributeModifier(MOON_FALL, safeFall, AttributeModifier.Operation.ADD_VALUE));
        } else if (!onMoon && gravity.hasModifier(MOON_GRAVITY)) {
            gravity.removeModifier(MOON_GRAVITY);
            fall.removeModifier(MOON_FALL);
        }
    }

    private static void breathe(ServerPlayer player) {
        ItemStack helmet = SpaceSuit.helmet(player);
        if (hasAir(player)) {
            // Bulle d'air d'un distributeur : on respire sans consommer et le casque se recharge.
            if (!helmet.isEmpty() && helmet.getDamageValue() > 0) {
                helmet.setDamageValue(Math.max(0, helmet.getDamageValue() - SpaceSuit.DISTRIBUTOR_REFILL));
                SpaceSuit.changed(player);
                player.sendOverlayMessage(Component.literal("Recharge du casque au distributeur… "
                        + (100 - 100 * helmet.getDamageValue() / helmet.getMaxDamage()) + " %").withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        SpaceSuit.autoRefill(player);
        boolean suited = !helmet.isEmpty();
        if (suited && helmet.getDamageValue() < helmet.getMaxDamage() - 1) {
            int second = player.level().getServer().getTickCount() / 20;
            // Palier « Poumons d'acier » : une unité toutes les deux secondes ; combinaison : une seconde sur quatre gratuite.
            boolean skip = (Progression.get().hasSteelLungs(player) && second % 2 == 0)
                    || (SpaceSuit.hasSuit(player) && second % 4 == 1)
                    || Progression.get().saveOxygen(player);
            if (!skip) {
                helmet.setDamageValue(helmet.getDamageValue() + 1);
                SpaceSuit.changed(player);
            }
            int percent = 100 - 100 * helmet.getDamageValue() / helmet.getMaxDamage();
            if (percent <= 20 || player.getRandom().nextInt(5) == 0) {
                player.sendOverlayMessage(Component.literal("Oxygène " + percent + " %")
                        .withStyle(percent <= 20 ? ChatFormatting.RED : ChatFormatting.AQUA));
            }
            return;
        }
        player.sendOverlayMessage(Component.literal(suited ? "Oxygène épuisé ! Ajoute des bouteilles dans ta combinaison (touche J)." : "Pas d'air ! Porte un casque spatial dans ta combinaison (touche J).")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        player.hurtServer(player.level(), player.damageSources().drown(), 2.0f);
    }

    public static void announceArrival(ServerPlayer player, byte destination) {
        if (destination == MOON_ID) {
            Progression.get().discover(player, "moon");
        } else if (destination == ORBIT_ID) {
            Progression.get().discover(player, "orbit");
        } else if (destination == MOON_ORBIT_ID) {
            Progression.get().discover(player, "moon_orbit");
        } else if (destination == MARS_ID || destination == MARS_ORBIT_ID) {
            Progression.get().discover(player, "mars");
        }
        String title = switch (destination) {
            case MOON_ID -> "La Lune";
            case ORBIT_ID -> "Orbite terrestre";
            case MARS_ID -> "Mars";
            case MARS_ORBIT_ID -> "Orbite de Mars";
            case MOON_ORBIT_ID -> "Orbite lunaire";
            default -> "La Terre";
        };
        String subtitle = switch (destination) {
            case MOON_ID -> "Gravité 1/6 — surveille ton oxygène";
            case ORBIT_ID -> "Plateforme d'amarrage — construis ta station";
            case MARS_ID -> "Gravité 38 % — atmosphère irrespirable";
            case MARS_ORBIT_ID -> "Orbite de Mars — prépare la descente";
            case MOON_ORBIT_ID -> "La Lune sous tes pieds — 1 dose pour descendre";
            default -> "Bon retour parmi les ruines";
        };
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 80, 30));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle).withStyle(ChatFormatting.GRAY)));
    }
}
