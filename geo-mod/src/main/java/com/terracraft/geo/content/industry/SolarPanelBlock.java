package com.terracraft.geo.content.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Panneau solaire : une unité d'énergie le jour, s'il voit le ciel ; relié aux machines par des câbles. */
public class SolarPanelBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
