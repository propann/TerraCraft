package com.terracraft.geo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Étal de marché : vend un objet à prix fixe, même vendeur hors ligne. La logique est dans {@code Stalls}. */
public class MarketStallBlock extends BaseEntityBlock {
    /** Branché par GeoMod : interaction (propriétaire : stock ; client : offre, Maj : achat). */
    public static java.util.function.BiConsumer<ServerPlayer, MarketStallBlockEntity> interaction = (p, s) -> { };

    public MarketStallBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketStallBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
            stall.setOwner(player.getUUID(), player.getName().getString());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
            interaction.accept(server, stall);
        }
        return InteractionResult.SUCCESS;
    }
}
