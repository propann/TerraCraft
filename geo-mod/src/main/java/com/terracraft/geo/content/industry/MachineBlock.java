package com.terracraft.geo.content.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Bloc de machine : la logique (énergie, liquides, production) est dans {@link MachineBlockEntity} et Industry. */
public class MachineBlock extends BaseEntityBlock {
    /** Branché par GeoMod : clic droit sur une machine (état, remplissage d'un bidon à la pompe à essence…). */
    public static Interaction interaction = (player, machine, stack, hand) -> InteractionResult.PASS;

    public interface Interaction {
        InteractionResult use(ServerPlayer player, MachineBlockEntity machine, ItemStack stack, InteractionHand hand);
    }

    public final MachineKind kind;

    public MachineBlock(MachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != IndustryBlocks.MACHINE_ENTITY) {
            return null;
        }
        return (l, pos, s, entity) -> ((MachineBlockEntity) entity).serverTick((ServerLevel) l);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            InteractionResult result = interaction.use(server, machine, stack, hand);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            interaction.use(server, machine, ItemStack.EMPTY, InteractionHand.MAIN_HAND);
        }
        return InteractionResult.SUCCESS;
    }
}
