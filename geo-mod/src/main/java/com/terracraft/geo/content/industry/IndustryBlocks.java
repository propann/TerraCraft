package com.terracraft.geo.content.industry;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.ModContent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;
import java.util.function.Function;

/** Blocs et objets de l'industrie du carburant (voir Industry pour la logique de jeu). */
public final class IndustryBlocks {
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5f, 6f).sound(SoundType.METAL);
    }

    public static final Block OIL_PUMP = block("oil_pump", p -> new MachineBlock(MachineKind.OIL_PUMP, p), metal().noOcclusion());
    public static final Block REFINERY = block("refinery", p -> new MachineBlock(MachineKind.REFINERY, p), metal());
    public static final Block FUEL_TANK = block("fuel_tank", p -> new MachineBlock(MachineKind.FUEL_TANK, p), metal());
    public static final Block FUEL_PUMP = block("fuel_pump", p -> new MachineBlock(MachineKind.FUEL_PUMP, p), metal().noOcclusion());
    public static final Block SOLAR_PANEL = block("solar_panel", SolarPanelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5f).sound(SoundType.GLASS).noOcclusion());
    public static final Block PIPE = block("pipe", p -> new ConnectorBlock(4f, false, p), metal().strength(1.5f).noOcclusion());
    public static final Block CABLE = block("cable", p -> new ConnectorBlock(2f, true, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5f).sound(SoundType.WOOL).noOcclusion());
    public static final Block OIL_PUDDLE = block("oil_puddle", OilPuddleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3f).sound(SoundType.MUD).noOcclusion());

    public static final BlockEntityType<MachineBlockEntity> MACHINE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "machine"),
            new BlockEntityType<>(MachineBlockEntity::new, Set.of(OIL_PUMP, REFINERY, FUEL_TANK, FUEL_PUMP)));

    /** Bidon vide : rempli à la pompe à essence (essence, ou kérosène avec Maj) ; rendu après chaque plein. */
    public static final Item EMPTY_FUEL_CAN = ModContent.item("empty_fuel_can", Item::new, new Item.Properties().stacksTo(16));
    /** Détecteur de pétrole : indique la direction et la distance du gisement le plus proche. */
    public static final Item OIL_DETECTOR = ModContent.item("oil_detector", Item::new, new Item.Properties().stacksTo(1));

    private IndustryBlocks() {
    }

    public static void init() {
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(output -> {
                    for (Block block : new Block[]{OIL_PUMP, REFINERY, FUEL_TANK, FUEL_PUMP, SOLAR_PANEL, PIPE, CABLE, OIL_PUDDLE}) {
                        output.accept(block);
                    }
                    output.accept(EMPTY_FUEL_CAN);
                    output.accept(OIL_DETECTOR);
                });
    }

    /** Après un plein (véhicule, avion, jetpack, fusée) : le bidon vide revient au joueur. */
    public static void returnEmptyCan(Player player) {
        if (player.isCreative()) {
            return;
        }
        ItemStack can = new ItemStack(EMPTY_FUEL_CAN);
        if (!player.getInventory().add(can) && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            player.spawnAtLocation(level, can);
        }
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
