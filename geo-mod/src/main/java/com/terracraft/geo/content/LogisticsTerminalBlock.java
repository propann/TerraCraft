package com.terracraft.geo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Terminal logistique : ouvre le stock commun de la ville du joueur (le même dans tous les terminaux). */
public class LogisticsTerminalBlock extends Block {
    /** Branché par GeoMod (Logistics.open). */
    public static java.util.function.BiConsumer<ServerPlayer, BlockPos> opener = (player, pos) -> { };

    public LogisticsTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            opener.accept(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
