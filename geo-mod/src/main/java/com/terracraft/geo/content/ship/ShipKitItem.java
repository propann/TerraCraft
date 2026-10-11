package com.terracraft.geo.content.ship;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ShipBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kit de vaisseau : construit devant le joueur une navette de 5 × 4 × 7 en blocs de vaisseau (114 blocs, 2 réacteurs,
 * 2 réservoirs vides, sas à bâbord, siège et console à l'avant). C'est un vaisseau comme un autre : on peut le
 * repeindre, l'agrandir (un réacteur par tranche de 60 blocs) ou le démonter.
 */
public class ShipKitItem extends Item {
    /** Couches de bas en haut ; lignes de l'avant (nord) vers l'arrière, colonnes d'ouest en est. */
    private static final String[][] LAYERS = {
            {"HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH", "HHHHH"},
            {"HGGGH", "H.N.H", "H.P.H", "D...H", "H...H", "HT.TH", "HXHXH"},
            {"HGGGH", "S...S", "S...S", "d...S", "S...S", "S...S", "HHHHH"},
            {"/////", "HHSHH", "HHSHH", "HHSHH", "HHSHH", "HHSHH", "HHHHH"},
    };

    public ShipKitItem(Properties properties) {
        super(properties);
    }

    static List<ShipStructure.ShipBlock> blueprint() {
        List<ShipStructure.ShipBlock> blocks = new ArrayList<>();
        for (int y = 0; y < LAYERS.length; y++) {
            for (int row = 0; row < LAYERS[y].length; row++) {
                for (int col = 0; col < 5; col++) {
                    BlockState state = state(LAYERS[y][row].charAt(col));
                    if (state != null) {
                        blocks.add(new ShipStructure.ShipBlock(new BlockPos(col - 2, y, row - 3), state, Optional.empty()));
                    }
                }
            }
        }
        return blocks;
    }

    private static BlockState state(char c) {
        BlockState door = ShipBlocks.SHIP_AIRLOCK.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        return switch (c) {
            case 'H' -> ShipBlocks.SHIP_HULL.defaultBlockState();
            case 'S' -> ShipBlocks.SHIP_HULL_STRIPE.defaultBlockState();
            case 'G' -> ShipBlocks.SHIP_CANOPY.defaultBlockState();
            case '/' -> ShipBlocks.SHIP_CANOPY_SLOPE.defaultBlockState().setValue(ShipBlocks.SlopeBlock.FACING, Direction.SOUTH)
                    .setValue(ShipBlocks.SlopeBlock.HALF, Half.BOTTOM);
            case 'N' -> ShipBlocks.NAV_CONSOLE.defaultBlockState().setValue(ShipBlocks.NavConsoleBlock.FACING, Direction.SOUTH);
            case 'P' -> ShipBlocks.PILOT_SEAT.defaultBlockState().setValue(ShipBlocks.PilotSeatBlock.FACING, Direction.NORTH);
            case 'T' -> ShipBlocks.SHIP_FUEL_TANK.defaultBlockState();
            case 'X' -> ShipBlocks.SHIP_THRUSTER.defaultBlockState().setValue(ShipBlocks.ThrusterBlock.FACING, Direction.SOUTH);
            case 'D' -> door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            case 'd' -> door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            default -> null;
        };
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Direction forward = player.getDirection();
        Rotation rotation = ShipStructure.rotationTo(Direction.NORTH, forward);
        // La poupe (z = 3) à deux blocs devant le joueur, l'avant dans la direction où il regarde.
        BlockPos origin = context.getClickedPos().relative(context.getClickedFace()).relative(forward, 6);
        List<ShipStructure.ShipBlock> blocks = blueprint();
        if (!ShipStructure.fits(level, origin, rotation, blocks)) {
            player.sendOverlayMessage(Component.literal("Pas assez de place : il faut 5 × 4 × 7 blocs libres devant toi.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ShipStructure.place(level, origin, rotation, blocks);
        context.getItemInHand().consume(1, player);
        level.playSound(null, origin, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        GeoMod.LOGGER.info("[VAISSEAU] {} construit une navette en {} {}", player.getName().getString(), level.dimension().identifier(), origin);
        player.sendSystemMessage(Component.literal("✦ Navette construite : verse du carburant de fusée dans ses deux réservoirs, "
                + "assieds-toi au siège de pilote puis clic droit sur la console de navigation.").withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }
}
