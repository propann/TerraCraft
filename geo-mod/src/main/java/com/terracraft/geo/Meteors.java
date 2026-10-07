package com.terracraft.geo;

import com.terracraft.geo.content.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pluies de micrométéorites sur la Lune : annoncées 30 s à l'avance, elles durent une minute et frappent autour des
 * joueurs à découvert. Tout toit protège (station, base, module, sous-sol). Un impact ne creuse que le régolithe
 * naturel, jamais une construction, et laisse parfois des fragments. Une pluie toutes les 25 à 45 minutes environ,
 * seulement quand quelqu'un est sur la Lune.
 */
final class Meteors {
    private static final int WARNING = 30 * 20;
    private static final int SHOWER = 60 * 20;
    private static final RandomSource RANDOM = RandomSource.create();

    private static long nextShower = -1;
    private static long showerStart = -1;
    private static boolean wasNight;

    private Meteors() {
    }

    /** Commande d'administration : pluie dans 5 secondes. */
    static void startSoon(MinecraftServer server) {
        showerStart = server.overworld().getGameTime() + 5 * 20;
        warn(server, 5);
    }

    static boolean active(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        return showerStart >= 0 && now >= showerStart && now < showerStart + SHOWER;
    }

    static void tick(MinecraftServer server) {
        ServerLevel moon = Space.moon(server);
        if (moon == null || moon.players().isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        nightWarning(moon);
        if (showerStart < 0 || now >= showerStart + SHOWER) {
            if (showerStart >= 0) {
                GeoMod.LOGGER.info("[METEORES] fin de la pluie");
                showerStart = -1;
                for (ServerPlayer player : moon.players()) {
                    player.sendOverlayMessage(Component.literal("Fin de la pluie de micrométéorites.").withStyle(ChatFormatting.GREEN));
                }
            }
            if (nextShower < 0) {
                nextShower = now + (25 + RANDOM.nextInt(21)) * 60 * 20L;
            }
            if (now >= nextShower - WARNING) {
                nextShower = -1;
                showerStart = now + WARNING;
                warn(server, WARNING / 20);
            }
            return;
        }
        if (now == showerStart) {
            GeoMod.LOGGER.info("[METEORES] pluie de micrométéorites sur la Lune ({} joueur(s))", moon.players().size());
        }
        if (now < showerStart || now % 12 != 0) {
            return;
        }
        for (ServerPlayer player : moon.players()) {
            if (player.isSpectator() || player.getY() < 40) {
                continue;
            }
            // Une fois sur quatre l'impact vise le joueur de près : rester dehors devient vite dangereux.
            double spread = RANDOM.nextInt(4) == 0 ? 2.5 : 12;
            double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * spread;
            double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * spread;
            impact(moon, x, player.getY(), z);
        }
    }

    private static void warn(MinecraftServer server, int seconds) {
        ServerLevel moon = Space.moon(server);
        if (moon == null) {
            return;
        }
        GeoMod.LOGGER.info("[METEORES] pluie annoncée dans {} s", seconds);
        for (ServerPlayer player : moon.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 15));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Micrométéorites")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Abrite-toi sous un toit : impacts dans "
                    + seconds + " s").withStyle(ChatFormatting.GOLD)));
            moon.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 0.6f);
        }
    }

    private static void impact(ServerLevel level, double x, double near, double z) {
        BlockPos column = BlockPos.containing(x, near, z);
        if (!level.isLoaded(column)) {
            return;
        }
        // Premier bloc solide en descendant depuis un peu au-dessus du joueur (toit, plateforme ou sol).
        int ground = Integer.MIN_VALUE;
        for (int y = column.getY() + 24; y > column.getY() - 48 && y > level.getMinY(); y--) {
            if (!level.getBlockState(column.atY(y - 1)).getCollisionShape(level, column.atY(y - 1)).isEmpty()) {
                ground = y;
                break;
            }
        }
        if (ground == Integer.MIN_VALUE) {
            return;
        }
        Vec3 hit = new Vec3(x, ground, z);
        // Traînée : la météorite arrive en biais depuis le haut.
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.FLAME, x - i * 0.6, ground + i * 1.4, z - i * 0.3, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, x, ground + 0.5, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, ground + 0.5, z, 6, 0.4, 0.2, 0.4, 0.02);
        level.playSound(null, BlockPos.containing(hit), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.4f, 0.5f);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(hit, hit).inflate(2.5))) {
            if (sheltered(level, victim) || victim.getY() < ground - 1.5) {
                continue; // À l'abri sous un toit, ou sous le point d'impact.
            }
            if (victim instanceof ServerPlayer player) {
                GeoMod.LOGGER.info("[METEORES] {} touché à découvert", player.getName().getString());
                player.sendOverlayMessage(Component.literal("Impact ! Mets-toi à l'abri sous un toit.").withStyle(ChatFormatting.RED));
            }
            victim.hurtServer(level, level.damageSources().explosion(null, null), 3f);
        }
        // Petit cratère dans le régolithe naturel seulement.
        BlockPos top = BlockPos.containing(x, ground - 1, z);
        BlockState state = level.getBlockState(top);
        if (natural(state) && RANDOM.nextInt(3) == 0) {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
            if (RANDOM.nextInt(4) == 0) {
                ItemStack shard = RANDOM.nextInt(5) == 0 ? new ItemStack(ModBlocks.HELIUM3_SHARD)
                        : new ItemStack(Items.IRON_NUGGET, 1 + RANDOM.nextInt(3));
                level.addFreshEntity(new ItemEntity(level, x, ground, z, shard));
            }
        }
    }

    /** Un bloc au-dessus de la tête (jusqu'à 64 blocs) suffit à protéger : toit, module, coque, roche. */
    static boolean sheltered(ServerLevel level, LivingEntity entity) {
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        for (int y = eye.getY() + 1; y <= Math.min(level.getMaxY(), eye.getY() + 64); y++) {
            if (!level.getBlockState(eye.atY(y)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean natural(BlockState state) {
        return state.is(Blocks.GRAVEL) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.ANDESITE)
                || state.getBlock() instanceof net.minecraft.world.level.block.ConcretePowderBlock;
    }

    /** Annonce de la nuit lunaire : les rôdeurs à découvert deviennent plus forts (voir ModMobs.MoonCrawler). */
    private static void nightWarning(ServerLevel moon) {
        boolean night = moon.isDarkOutside();
        if (night && !wasNight) {
            for (ServerPlayer player : moon.players()) {
                player.sendSystemMessage(Component.literal("☾ La nuit lunaire tombe : les rôdeurs sont plus rapides et plus forts.")
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        wasNight = night;
    }
}
