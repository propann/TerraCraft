package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ship.BuiltShip;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Blocs de construction de vaisseau. Un vaisseau est une construction de ces blocs (et de quelques blocs d'intérieur,
 * tag {@code terracraft_geo:ship_blocks}) autour d'un siège de pilote et d'une console de navigation, avec des
 * réacteurs et des réservoirs : voir {@link BuiltShip}. Coque blanche (unie, à bande, en pente), verrière (droite ou
 * en pente), réacteur orientable, réservoir de carburant, sas, siège de pilote et console.
 */
public final class ShipBlocks {
    /** Blocs qu'un vaisseau emporte : blocs de vaisseau et mobilier d'intérieur autorisé. */
    public static final TagKey<Block> SHIP_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "ship_blocks"));

    public static final Block SHIP_HULL = block("ship_hull", Block::new, metal());
    public static final Block SHIP_HULL_STRIPE = block("ship_hull_stripe", Block::new, metal());
    public static final Block SHIP_HULL_SLOPE = block("ship_hull_slope", SlopeBlock::new, metal().noOcclusion());
    public static final Block SHIP_CANOPY = block("ship_canopy", TransparentBlock::new, glass());
    public static final Block SHIP_CANOPY_SLOPE = block("ship_canopy_slope", SlopeBlock::new, glass());
    public static final Block SHIP_THRUSTER = block("ship_thruster", ThrusterBlock::new, metal().lightLevel(state -> 10));
    public static final Block SHIP_FUEL_TANK = block("ship_fuel_tank", FuelTankBlock::new, metal());
    public static final Block SHIP_AIRLOCK = block("ship_airlock", p -> new DoorBlock(BlockSetType.COPPER, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3f, 10f).sound(SoundType.METAL).noOcclusion());
    public static final Block PILOT_SEAT = block("pilot_seat", PilotSeatBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.WOOL).noOcclusion());
    public static final Block NAV_CONSOLE = block("nav_console", NavConsoleBlock::new, metal().lightLevel(state -> 7).noOcclusion());
    public static final EntityType<Seat> SEAT = seatType();

    private ShipBlocks() {
    }

    public static void init() {
        GeoMod.LOGGER.info("TerraCraft : blocs de vaisseau enregistrés.");
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).requiresCorrectToolForDrops().strength(4f, 10f).sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of().strength(2f, 12f).sound(SoundType.GLASS).noOcclusion()
                .isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never);
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    /** Point d'assise du siège : sérialisable, sinon le serveur refuse qu'on y monte (Entity#startRiding). */
    private static EntityType<Seat> seatType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "seat"));
        EntityType<Seat> type = EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC)
                .sized(0.1f, 0.1f)
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

    /**
     * Pente de coque ou de verrière : rampe à 45° en quatre marches de 4 pixels (assez fine pour paraître lisse, sans
     * changer l'échelle des blocs). Posée comme un escalier : le côté haut est à l'opposé du joueur, à l'envers si l'on
     * vise le haut d'un bloc.
     */
    public static class SlopeBlock extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
        private static final Map<Direction, VoxelShape> BOTTOM = new EnumMap<>(Direction.class);
        private static final Map<Direction, VoxelShape> TOP = new EnumMap<>(Direction.class);

        static {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape bottom = Shapes.empty();
                VoxelShape top = Shapes.empty();
                for (int step = 0; step < 4; step++) {
                    int depth = 16 - 4 * step;   // Profondeur de la marche depuis le côté haut (FACING).
                    bottom = Shapes.or(bottom, stepBox(facing, depth, 4 * step, 4 * step + 4));
                    top = Shapes.or(top, stepBox(facing, depth, 12 - 4 * step, 16 - 4 * step));
                }
                BOTTOM.put(facing, bottom);
                TOP.put(facing, top);
            }
        }

        private static VoxelShape stepBox(Direction facing, int depth, int y1, int y2) {
            return switch (facing) {
                case NORTH -> Block.box(0, y1, 0, 16, y2, depth);
                case SOUTH -> Block.box(0, y1, 16 - depth, 16, y2, 16);
                case WEST -> Block.box(0, y1, 0, depth, y2, 16);
                default -> Block.box(16 - depth, y1, 0, 16, y2, 16);
            };
        }

        public SlopeBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction face = context.getClickedFace();
            boolean top = face == Direction.DOWN || face != Direction.UP && context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
            return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(HALF, top ? Half.TOP : Half.BOTTOM);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return (state.getValue(HALF) == Half.TOP ? TOP : BOTTOM).get(state.getValue(FACING));
        }

        @Override
        protected boolean useShapeForLightOcclusion(BlockState state) {
            return true;
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
            builder.add(FACING, HALF);
        }
    }

    /** Réservoir de vaisseau : 5 doses de carburant de fusée, versées une à une (clic droit avec un bidon de carburant). */
    public static class FuelTankBlock extends Block {
        public static final int CAPACITY = 5;
        public static final IntegerProperty FUEL = IntegerProperty.create("fuel", 0, CAPACITY);

        public FuelTankBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FUEL, 0));
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(ModContent.ROCKET_FUEL)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            int fuel = state.getValue(FUEL);
            if (fuel >= CAPACITY) {
                player.sendOverlayMessage(Component.literal("Réservoir plein (" + CAPACITY + " doses).").withStyle(ChatFormatting.YELLOW));
                return InteractionResult.CONSUME;
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(FUEL, fuel + 1), Block.UPDATE_ALL);
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.8f);
                player.sendOverlayMessage(Component.literal("Réservoir : " + (fuel + 1) + "/" + CAPACITY + " doses").withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            player.sendOverlayMessage(Component.literal("Réservoir : " + state.getValue(FUEL) + "/" + CAPACITY
                    + " doses — verse du carburant de fusée (clic droit avec le bidon).").withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FUEL);
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

    /** Siège de pilote : clic droit pour s'asseoir (Maj pour se lever). Le pilote assis commande le vaisseau. */
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
            if (!player.startRiding(seat)) {
                seat.discard();
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer pilot) {
                pilot.sendOverlayMessage(Component.literal("Aux commandes : clic droit sur la console de navigation pour choisir le cap.")
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Console de navigation : ouvre la carte des étoiles du vaisseau dont elle fait partie (ou, à défaut, de la fusée
     * amarrée la plus proche). Elle porte les améliorations de l'atelier de station : moteur ionique et navigation
     * martienne.
     */
    public static class NavConsoleBlock extends FacingBlock {
        public static final BooleanProperty ION = BooleanProperty.create("ion");
        public static final BooleanProperty MARS_NAV = BooleanProperty.create("mars_nav");

        public NavConsoleBlock(Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(ION, false).setValue(MARS_NAV, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, ION, MARS_NAV);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer server) {
                BuiltShip.openConsole(server, pos);
            }
            return InteractionResult.SUCCESS;
        }
    }
}
