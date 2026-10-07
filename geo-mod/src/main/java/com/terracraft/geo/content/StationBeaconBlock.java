package com.terracraft.geo.content;

import com.terracraft.geo.Stations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Balise de station : enregistre la station de son poseur, point d'arrivée de ses fusées. */
public class StationBeaconBlock extends Block {
    public StationBeaconBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel server && by instanceof ServerPlayer player) {
            Stations.get().placed(player, server, pos.immutable());
        }
    }
}
