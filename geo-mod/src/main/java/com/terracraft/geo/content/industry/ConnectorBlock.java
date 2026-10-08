package com.terracraft.geo.content.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Tuyau (liquides) ou câble électrique : se relie aux blocs identiques et aux machines compatibles, dans les six
 * directions (forme et modèle à bras, comme la plante de chorus).
 */
public class ConnectorBlock extends PipeBlock {
    public final boolean electric;

    public ConnectorBlock(float size, boolean electric, Properties properties) {
        super(size, properties);
        this.electric = electric;
        BlockState state = this.stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    /** Ce connecteur se relie-t-il à ce voisin ? */
    public boolean connects(BlockState neighbour) {
        if (neighbour.is(this)) {
            return true;
        }
        if (electric) {
            return neighbour.getBlock() instanceof SolarPanelBlock || neighbour.getBlock() instanceof LampBlock
                    || neighbour.getBlock() instanceof MachineBlock machine && machine.kind.powered;
        }
        return neighbour.getBlock() instanceof MachineBlock machine && machine.kind != MachineKind.BATTERY;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connects(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connects(neighbourState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }
}
