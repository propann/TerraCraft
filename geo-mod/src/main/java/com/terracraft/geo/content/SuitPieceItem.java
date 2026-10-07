package com.terracraft.geo.content;

import com.terracraft.geo.SpaceSuit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Pièce de combinaison spatiale : clic droit pour la porter (elle va dans la combinaison, touche J). */
public class SuitPieceItem extends Item {
    public SuitPieceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            SpaceSuit.equipFromHand(player, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
