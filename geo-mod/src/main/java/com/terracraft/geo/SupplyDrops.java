package com.terracraft.geo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Ravitaillements militaires : régulièrement, une caisse de butin est larguée près d'un joueur
 * sur Terre. Sa position est annoncée à tous et une colonne de fumée la signale pendant
 * 10 minutes : premier arrivé, premier servi. Les caisses sont posées dans des chunks déjà chargés
 * (jamais de génération forcée, lente sur la Terre réelle).
 */
final class SupplyDrops {
    /** Délai entre deux largages (ticks) : 45 à 75 minutes. */
    private static final int MIN_DELAY = 45 * 60 * 20;
    private static final int MAX_DELAY = 75 * 60 * 20;
    private static final int SIGNAL_TICKS = 10 * 60 * 20;
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "chests/supply_drop"));

    private record Drop(ServerLevel level, BlockPos pos, int expires) {
    }

    private final List<Drop> active = new ArrayList<>();
    private long next = -1;

    void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (next < 0) {
            next = now + 20L * 60 * 20; // Premier largage 20 minutes après le démarrage.
        }
        if (now >= next) {
            next = now + MIN_DELAY + server.overworld().getRandom().nextInt(MAX_DELAY - MIN_DELAY);
            drop(server, null);
        }
        if (now % 20 == 0) {
            signal(now);
        }
    }

    /** Fumée et étincelles au-dessus des caisses actives ; retire les caisses expirées ou pillées. */
    private void signal(int now) {
        Iterator<Drop> it = active.iterator();
        while (it.hasNext()) {
            Drop drop = it.next();
            boolean opened = !(drop.level().getBlockEntity(drop.pos()) instanceof RandomizableContainer chest)
                    || chest.getLootTable() == null;
            if (opened || now > drop.expires()) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 6; i++) {
                drop.level().sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, drop.pos().getX() + 0.5,
                        drop.pos().getY() + 1.5 + i * 3, drop.pos().getZ() + 0.5, 1, 0.1, 0.5, 0.1, 0.01);
            }
            drop.level().sendParticles(ParticleTypes.FIREWORK, drop.pos().getX() + 0.5, drop.pos().getY() + 1.2,
                    drop.pos().getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Largue une caisse près de {@code target} (ou d'un joueur au hasard). Renvoie la position, ou
     * null si aucun endroit chargé ne convient.
     */
    BlockPos drop(MinecraftServer server, ServerPlayer target) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> candidates = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (target == null) {
            if (candidates.isEmpty()) {
                return null;
            }
            target = candidates.get(level.getRandom().nextInt(candidates.size()));
        }
        RandomSource random = level.getRandom();
        // Assez loin pour devoir chercher, mais dans la zone simulée autour du joueur (sinon le
        // coffre tomberait dans un chunk non chargé) : jusqu'à 120 blocs, moins si le serveur simule peu.
        int reach = Math.max(24, Math.min(120, server.getPlayerList().getSimulationDistance() * 16 - 24));
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = reach / 2 + random.nextInt(reach / 2 + 1);
            int x = target.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.isPositionEntityTicking(new BlockPos(x, target.getBlockY(), z))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos).canBeReplaced()) {
                continue; // Pas dans l'eau ni dans un bloc.
            }
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, LOOT);
            active.add(new Drop(level, pos.immutable(), server.getTickCount() + SIGNAL_TICKS));
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 0.6f);
            server.getPlayerList().broadcastSystemMessage(Component.literal("✈ Ravitaillement militaire largué en "
                    + x + " / " + y + " / " + z + " (près de " + target.getName().getString()
                    + "). Suivez la fumée, premier arrivé, premier servi !").withStyle(ChatFormatting.GOLD), false);
            GeoMod.LOGGER.info("[LARGAGE] Caisse en {} {} {} près de {}", x, y, z, target.getName().getString());
            return pos;
        }
        return null;
    }

    /** Coffre fouillé : s'il s'agit d'une caisse de ravitaillement, on le compte. */
    boolean isSupply(ServerLevel level, BlockPos pos) {
        return active.stream().anyMatch(d -> d.level() == level && d.pos().equals(pos));
    }
}
