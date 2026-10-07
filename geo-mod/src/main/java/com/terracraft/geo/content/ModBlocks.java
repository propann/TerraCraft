package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/** Blocs de l'espace : ressources lunaires et éléments de base orbitale. */
public final class ModBlocks {
    private ModBlocks() {
    }

    public static final Block TITANIUM_ORE = block("titanium_ore", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(4f, 6f).sound(SoundType.STONE));
    public static final Block HELIUM3_CRYSTALS = block("helium3_crystals", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.5f).sound(SoundType.AMETHYST)
                    .lightLevel(state -> 7).requiresCorrectToolForDrops());
    public static final Block STATION_HULL = block("station_hull", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5f, 12f).sound(SoundType.METAL));
    public static final Block STATION_FLOOR = block("station_floor", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4f, 10f).sound(SoundType.METAL));
    public static final Block STATION_WINDOW = block("station_window", TransparentBlock::new,
            BlockBehaviour.Properties.of().strength(2f, 12f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never));
    public static final Block STATION_LIGHT = block("station_light", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1f).sound(SoundType.GLASS).lightLevel(state -> 15));
    /** Balise de station : point d'arrivée des fusées de son poseur (et de sa ville). */
    public static final Block STATION_BEACON = block("station_beacon", StationBeaconBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2f).sound(SoundType.METAL).lightLevel(state -> 12));
    /** Atelier de station : installe les améliorations des plans de fusée. */
    public static final Block STATION_WORKSHOP = block("station_workshop", StationWorkshopBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).lightLevel(state -> 4));
    /** Rend l'air respirable dans un rayon de 8 blocs (Lune et orbite). */
    public static final Block OXYGEN_DISTRIBUTOR = block("oxygen_distributor", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(4f, 10f)
                    .sound(SoundType.METAL).lightLevel(state -> 6));

    public static final Item TITANIUM_INGOT = ModContent.item("titanium_ingot", Item::new, new Item.Properties());
    public static final Item HELIUM3_SHARD = ModContent.item("helium3_shard", Item::new, new Item.Properties());

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
            for (Block block : new Block[]{STATION_HULL, STATION_FLOOR, STATION_WINDOW, STATION_LIGHT, STATION_BEACON, STATION_WORKSHOP, OXYGEN_DISTRIBUTOR}) {
                output.accept(block);
            }
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
            output.accept(TITANIUM_ORE);
            output.accept(HELIUM3_CRYSTALS);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
            output.accept(TITANIUM_INGOT);
            output.accept(HELIUM3_SHARD);
        });
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
