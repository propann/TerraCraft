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
    /** Sas étanche : porte de station qui s'ouvre à la main (relie salles, tunnels et quai). */
    public static final Block AIRLOCK_DOOR = block("airlock_door", p -> new net.minecraft.world.level.block.DoorBlock(
                    net.minecraft.world.level.block.state.properties.BlockSetType.COPPER, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).noOcclusion());
    /** Pince d'amarrage : repère le quai où se posent les fusées. */
    public static final Block DOCKING_CLAMP = block("docking_clamp", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3f).sound(SoundType.METAL).lightLevel(state -> 7));
    /** Atelier de station : installe les améliorations des plans de fusée. */
    public static final Block STATION_WORKSHOP = block("station_workshop", StationWorkshopBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).lightLevel(state -> 4));
    /** Rend l'air respirable dans un rayon de 8 blocs (Lune et orbite). */
    public static final Block OXYGEN_DISTRIBUTOR = block("oxygen_distributor", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).requiresCorrectToolForDrops().strength(4f, 10f)
                    .sound(SoundType.METAL).lightLevel(state -> 6));

    /** Pierre extraterrestre : pyramides et ruines du sous-sol lunaire. */
    public static final Block ALIEN_STONE = block("alien_stone", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(4f, 9f));
    /** Glyphe extraterrestre : pierre gravée qui luit (bandes des pyramides, piliers des ruines). */
    public static final Block ALIEN_GLYPH = block("alien_glyph", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).requiresCorrectToolForDrops().strength(4f, 9f).lightLevel(state -> 10));
    /** Cristal lunaire : éclaire les cavernes géantes, au sol et au plafond. */
    public static final Block LUNAR_CRYSTAL = block("lunar_crystal", TransparentBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(1.5f).sound(SoundType.AMETHYST)
                    .lightLevel(state -> 12).noOcclusion());

    public static final Item TITANIUM_INGOT = ModContent.item("titanium_ingot", Item::new, new Item.Properties());
    /** Artefact extraterrestre : trésor rare des pyramides et sanctuaires lunaires. */
    public static final Item ALIEN_ARTIFACT = ModContent.item("alien_artifact", Item::new, new Item.Properties().stacksTo(16));
    public static final Item HELIUM3_SHARD = ModContent.item("helium3_shard", Item::new, new Item.Properties());

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
            for (Block block : new Block[]{STATION_HULL, STATION_FLOOR, STATION_WINDOW, STATION_LIGHT, STATION_BEACON, STATION_WORKSHOP, AIRLOCK_DOOR, DOCKING_CLAMP, OXYGEN_DISTRIBUTOR}) {
                output.accept(block);
            }
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
            output.accept(TITANIUM_ORE);
            output.accept(HELIUM3_CRYSTALS);
            output.accept(ALIEN_STONE);
            output.accept(ALIEN_GLYPH);
            output.accept(LUNAR_CRYSTAL);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
            output.accept(TITANIUM_INGOT);
            output.accept(HELIUM3_SHARD);
            output.accept(ALIEN_ARTIFACT);
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
