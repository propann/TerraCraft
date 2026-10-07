package com.terracraft.geo.content;

import com.terracraft.geo.Progression;
import com.terracraft.geo.Space;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kit de module de station : clic droit sur le sol, dans l'espace, pour construire d'un coup un
 * module pressurisé de 7 × 5 × 7 devant soi (coque, hublots, plancher, lumières, distributeur
 * d'oxygène et une porte au milieu de chaque mur). Deux modules posés côte à côte se raccordent par
 * leurs portes. Seul l'air est remplacé : un module ne détruit jamais rien.
 */
public class StationModuleItem extends Item {
    private static final int HALF = 3;
    private static final int HEIGHT = 5;
    /** Blocs solides tolérés dans le volume (quai, morceau de station voisine…). */
    private static final int MAX_OBSTACLES = 24;

    public StationModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!Space.isSpace(level)) {
            player.sendOverlayMessage(Component.literal("Les modules de station se posent dans l'espace (orbite, Lune, Mars).")
                    .withStyle(ChatFormatting.GOLD));
            return InteractionResult.FAIL;
        }
        Direction facing = context.getHorizontalDirection();
        BlockPos floor = context.getClickedPos();
        BlockPos center = floor.relative(facing, HALF + 1);
        int obstacles = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-HALF, 1, -HALF), center.offset(HALF, HEIGHT - 1, HALF))) {
            if (!level.getBlockState(pos).isAir()) {
                obstacles++;
            }
        }
        if (obstacles > MAX_OBSTACLES) {
            player.sendOverlayMessage(Component.literal("Pas assez de place devant toi pour un module (7 × 5 × 7).")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        build(level, center);
        context.getItemInHand().consume(1, player);
        level.playSound(null, center, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 0.7f);
        Progression.get().count(player, "modules", 1, 15);
        com.terracraft.geo.GeoMod.LOGGER.info("[STATION] {} pose un module en {} {}", player.getName().getString(),
                level.dimension().identifier(), center);
        player.sendOverlayMessage(Component.literal("Module pressurisé construit : air respirable à l'intérieur.")
                .withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private static void build(ServerLevel level, BlockPos center) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = 0; dy < HEIGHT; dy++) {
                    boolean wallX = Math.abs(dx) == HALF;
                    boolean wallZ = Math.abs(dz) == HALF;
                    Block block;
                    if (dy == 0) {
                        block = ModBlocks.STATION_FLOOR;
                    } else if (dy == HEIGHT - 1) {
                        block = Math.abs(dx) == 2 && Math.abs(dz) == 2 ? ModBlocks.STATION_LIGHT : ModBlocks.STATION_HULL;
                    } else if (wallX || wallZ) {
                        boolean middle = wallX ? dz == 0 : dx == 0;
                        if (middle && dy <= 2) {
                            continue; // Porte de 1 × 2 au milieu de chaque mur.
                        }
                        boolean window = dy == 2 && (wallX ? Math.abs(dz) == 1 : Math.abs(dx) == 1);
                        block = window ? ModBlocks.STATION_WINDOW : ModBlocks.STATION_HULL;
                    } else if (dx == 0 && dz == 0 && dy == 1) {
                        block = ModBlocks.OXYGEN_DISTRIBUTOR;
                    } else {
                        continue; // Intérieur : laissé vide.
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState current = level.getBlockState(pos);
                    if (current.isAir()) {
                        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
