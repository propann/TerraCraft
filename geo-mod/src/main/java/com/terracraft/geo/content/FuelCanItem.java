package com.terracraft.geo.content;

import com.terracraft.geo.Jetpack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Bidon d'essence : clic droit sur un véhicule pour le remplir, ou dans le vide pour recharger le jetpack porté. */
public class FuelCanItem extends Item {
    public FuelCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            Jetpack.refuel(player, hand);
        }
        return InteractionResult.SUCCESS;
    }
}
