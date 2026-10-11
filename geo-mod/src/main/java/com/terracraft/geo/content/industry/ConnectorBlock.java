package com.terracraft.geo.content.industry;

import com.terracraft.geo.content.ModBlocks;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tuyau (liquides) ou câble électrique : se relie aux blocs identiques, aux machines compatibles et aux modules de
 * station, dans les six directions (forme et modèle à bras, comme la plante de chorus). Chaque raccord à une machine
 * ou à un module porte une bride boulonnée ; deux connecteurs se suivent sans bride.
 */
public class ConnectorBlock extends PipeBlock {
    /** Bride de raccordement côté machine ou module, par direction (purement visuelle). */
    public static final Map<Direction, BooleanProperty> FLANGE_BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            FLANGE_BY_DIRECTION.put(direction, BooleanProperty.create("flange_" + direction.getSerializedName()));
        }
    }

    public final boolean electric;

    public ConnectorBlock(float size, boolean electric, Properties properties) {
        super(size, properties);
        this.electric = electric;
        BlockState state = this.stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), false).setValue(FLANGE_BY_DIRECTION.get(direction), false);
        }
        registerDefaultState(state);
    }

    /** Ce connecteur se relie-t-il à ce voisin ? */
    public boolean connects(BlockState neighbour) {
        if (neighbour.is(this)) {
            return true;
        }
        if (stationModule(neighbour)) {
            return true;
        }
        if (electric) {
            return neighbour.getBlock() instanceof SolarPanelBlock || neighbour.getBlock() instanceof LampBlock
                    || neighbour.getBlock() instanceof MachineBlock machine && machine.kind.powered;
        }
        return neighbour.getBlock() instanceof MachineBlock machine && machine.kind != MachineKind.BATTERY
                && machine.kind != MachineKind.GREENHOUSE;
    }

    /**
     * Modules de station raccordables : le câble alimente oxygène, atelier, balise, éclairage et pinces d'amarrage ;
     * le tuyau alimente l'oxygène et ravitaille le quai par les pinces. Raccord visuel : le réseau industriel
     * ne s'occupe que des machines.
     */
    private boolean stationModule(BlockState neighbour) {
        if (neighbour.is(ModBlocks.OXYGEN_DISTRIBUTOR) || neighbour.is(ModBlocks.DOCKING_CLAMP)) {
            return true;
        }
        return electric && (neighbour.is(ModBlocks.STATION_WORKSHOP) || neighbour.is(ModBlocks.STATION_BEACON)
                || neighbour.is(ModBlocks.STATION_LIGHT));
    }

    /** Raccord à autre chose qu'un connecteur identique : la bride est dessinée. */
    private boolean flanged(BlockState neighbour) {
        return !neighbour.is(this) && connects(neighbour);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), connects(neighbour))
                    .setValue(FLANGE_BY_DIRECTION.get(direction), flanged(neighbour));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), connects(neighbourState))
                .setValue(FLANGE_BY_DIRECTION.get(direction), flanged(neighbourState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
        FLANGE_BY_DIRECTION.values().forEach(builder::add);
    }
}
