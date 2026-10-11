package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.StarMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Blocs de construction de vaisseau : coque blanche (unie ou à bande), verrière teintée, réacteur orientable qui
 * crache des flammes, siège de pilote où l'on s'assoit, et console de navigation qui ouvre la carte des étoiles de
 * l'engin amarré le plus proche.
 */
public final class ShipBlocks {
    public static final Block SHIP_HULL = block("ship_hull", Block::new, metal());
    public static final Block SHIP_HULL_STRIPE = block("ship_hull_stripe", Block::new, metal());
    public static final Block SHIP_CANOPY = block("ship_canopy", TransparentBlock::new,
            BlockBehaviour.Properties.of().strength(2f, 12f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
    public static final Block SHIP_THRUSTER = block("ship_thruster", ThrusterBlock::new, metal().lightLevel(state -> 10));
    public static final Block PILOT_SEAT = block("pilot_seat", PilotSeatBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOL).noOcclusion());
    public static final Block NAV_CONSOLE = block("nav_console", NavConsoleBlock::new, metal().lightLevel(state -> 7).noOcclusion());
    /*
     * Tunnel-sas déployé par un vaisseau amarré : même aspect que la station, mais sans butin, pour qu'un amarrage
     * ne fabrique pas de blocs gratuits. Le vaisseau le replie au décollage.
     */
    public static final Block TUNNEL_HULL = block("docking_tunnel_hull", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 12f).sound(SoundType.METAL));
    public static final Block TUNNEL_WINDOW = block("docking_tunnel_window", TransparentBlock::new,
            BlockBehaviour.Properties.of().strength(2f, 12f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
    public static final Block TUNNEL_LIGHT = block("docking_tunnel_light", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1f).sound(SoundType.GLASS).lightLevel(state -> 15));
    public static final EntityType<Seat> SEAT = seatType();

    private ShipBlocks() {
    }

    public static void init() {
        GeoMod.LOGGER.info("TerraCraft : blocs de vaisseau enregistrés.");
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(4f, 10f).sound(SoundType.METAL);
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static EntityType<Seat> seatType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "seat"));
        EntityType<Seat> type = EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC)
                .sized(0.1f, 0.1f)
                .noSave()
                .noSummon()
                .clientTrackingRange(8)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    /** Réacteur : la tuyère regarde vers le joueur qui le pose (comme un distributeur) et crache des flammes. */
    public static class ThrusterBlock extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

        public ThrusterBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            Direction out = state.getValue(FACING);
            if (!level.getBlockState(pos.relative(out)).isAir()) {
                return; // Tuyère bouchée : pas de flamme.
            }
            double x = pos.getX() + 0.5 + out.getStepX() * 0.6;
            double y = pos.getY() + 0.5 + out.getStepY() * 0.6;
            double z = pos.getZ() + 0.5 + out.getStepZ() * 0.6;
            for (int i = 0; i < 2; i++) {
                level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME,
                        x + (random.nextDouble() - 0.5) * 0.3, y + (random.nextDouble() - 0.5) * 0.3, z + (random.nextDouble() - 0.5) * 0.3,
                        out.getStepX() * 0.06, out.getStepY() * 0.06, out.getStepZ() * 0.06);
            }
        }
    }

    /** Bloc orienté à l'horizontale, face au joueur qui le pose. */
    abstract static class FacingBlock extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

        FacingBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        protected BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
    }

    /** Siège de pilote : clic droit pour s'asseoir (Maj pour se lever). */
    public static class PilotSeatBlock extends FacingBlock {
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

        public PilotSeatBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.isClientSide() || player.isPassenger()) {
                return InteractionResult.SUCCESS;
            }
            if (!level.getEntitiesOfClass(Seat.class, new AABB(pos)).isEmpty()) {
                return InteractionResult.CONSUME; // Déjà occupé.
            }
            Seat seat = new Seat(SEAT, level);
            seat.setPos(pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5);
            seat.setYRot(state.getValue(FACING).toYRot());
            level.addFreshEntity(seat);
            player.startRiding(seat);
            return InteractionResult.SUCCESS;
        }
    }

    /** Console de navigation : ouvre la carte des étoiles de la fusée ou du vaisseau amarré le plus proche. */
    public static class NavConsoleBlock extends FacingBlock {
        private static final int RANGE = 24;

        public NavConsoleBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!(player instanceof ServerPlayer server)) {
                return InteractionResult.SUCCESS;
            }
            Rocket nearest = level.getEntitiesOfClass(Rocket.class, new AABB(pos).inflate(RANGE), r -> r.phase() == Rocket.IDLE && r.isComplete())
                    .stream().min(Comparator.comparingDouble(r -> r.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
            if (nearest == null) {
                server.sendOverlayMessage(Component.literal("Aucune fusée ni aucun vaisseau amarré à moins de " + RANGE + " blocs.")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.CONSUME;
            }
            StarMap.open(server, nearest);
            return InteractionResult.SUCCESS;
        }
    }
}
