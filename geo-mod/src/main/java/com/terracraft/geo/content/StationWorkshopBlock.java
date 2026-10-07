package com.terracraft.geo.content;

import com.terracraft.geo.Workshop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Atelier de station : clic droit pour améliorer la fusée garée à côté (plans de fusée). */
public class StationWorkshopBlock extends Block {
    public StationWorkshopBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Workshop.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
