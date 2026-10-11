package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.industry.IndustryBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/**
 * Catégories des objets TerraCraft : onglets du menu créatif et chapitres du catalogue (menu O). Chaque objet du mod
 * est rangé dans une seule catégorie ; l'ordre suit la progression (on fabrique avant de décoller).
 */
public final class ModTabs {
    /** Une catégorie : identifiant (titre itemGroup.terracraft_geo.<id>), icône et objets dans l'ordre. */
    public record Category(String id, Supplier<ItemLike> icon, Supplier<List<ItemLike>> items) {
        public Component title() {
            return Component.translatable("itemGroup." + GeoMod.MOD_ID + "." + id);
        }
    }

    public static final List<Category> CATEGORIES = new java.util.ArrayList<>();

    private ModTabs() {
    }

    public static void init() {
        tab("space", () -> ModContent.ROCKET_HULL, () -> List.of(
                ModContent.ROCKET_HULL, ModContent.ROCKET_ENGINE, ModContent.ROCKET_TANK, ModContent.NOSE_CONE, ModContent.FINS,
                ModContent.ROCKET_FUEL, ModContent.SPACE_HELMET, ModContent.SPACE_SUIT, ModContent.MAGNETIC_BOOTS,
                ModContent.JETPACK, ModContent.OXYGEN_TANK, ModContent.ORBITAL_STATION_KIT, ModContent.STATION_MODULE,
                ModContent.LUNAR_BASE_KIT, ModContent.MARS_BASE_KIT, ModContent.ROVER_KIT));
        tab("station", () -> ModBlocks.STATION_HULL, () -> List.of(
                ModBlocks.STATION_HULL, ModBlocks.STATION_FLOOR, ModBlocks.STATION_WINDOW, ModBlocks.STATION_LIGHT,
                ModBlocks.AIRLOCK_DOOR, ModBlocks.STATION_BEACON, ModBlocks.DOCKING_CLAMP, ModBlocks.OXYGEN_DISTRIBUTOR,
                ModBlocks.STATION_WORKSHOP));
        tab("ships", () -> ModContent.SPACESHIP_KIT, () -> List.of(
                ModContent.SPACESHIP_KIT, ShipBlocks.NAV_CONSOLE, ShipBlocks.PILOT_SEAT, ShipBlocks.SHIP_THRUSTER,
                ShipBlocks.SHIP_FUEL_TANK, ShipBlocks.SHIP_AIRLOCK, ShipBlocks.SHIP_HULL, ShipBlocks.SHIP_HULL_STRIPE,
                ShipBlocks.SHIP_HULL_SLOPE, ShipBlocks.SHIP_CANOPY, ShipBlocks.SHIP_CANOPY_SLOPE));
        tab("industry", () -> IndustryBlocks.REFINERY, () -> List.of(
                IndustryBlocks.OIL_DETECTOR, IndustryBlocks.OIL_PUDDLE, IndustryBlocks.OIL_PUMP, IndustryBlocks.REFINERY,
                IndustryBlocks.FUEL_TANK, IndustryBlocks.FUEL_PUMP, IndustryBlocks.EMPTY_FUEL_CAN, IndustryBlocks.PIPE,
                IndustryBlocks.CABLE, IndustryBlocks.SOLAR_PANEL, IndustryBlocks.BATTERY, IndustryBlocks.GENERATOR,
                IndustryBlocks.LAMP, IndustryBlocks.GREENHOUSE, ModBlocks.LOGISTICS_TERMINAL, ModBlocks.MARKET_STALL,
                ModBlocks.TOWN_REGISTRY));
        tab("vehicles", () -> ModContent.CAR_CHASSIS, () -> List.of(
                ModContent.CAR_CHASSIS, ModContent.TRUCK_CHASSIS, ModContent.MOTORCYCLE_CHASSIS, ModContent.PLANE_KIT,
                ModContent.WHEEL, ModContent.ENGINE, ModContent.RADIATOR, ModContent.BATTERY, ModContent.TURBO, ModContent.FUEL_CAN));
        tab("combat", () -> ModContent.RIFLE, () -> List.of(
                ModContent.PISTOL, ModContent.SMG, ModContent.RIFLE, ModContent.SNIPER, ModContent.SHOTGUN, ModContent.AMMO,
                ModContent.GRENADE, ModContent.MACHETE, ModBlocks.COMMANDER_BADGE));
        tab("materials", () -> ModBlocks.TITANIUM_INGOT, () -> List.of(
                ModBlocks.TITANIUM_ORE, ModBlocks.TITANIUM_INGOT, ModBlocks.HELIUM3_CRYSTALS, ModBlocks.HELIUM3_SHARD,
                ModBlocks.LUNAR_CRYSTAL, ModBlocks.ALIEN_STONE, ModBlocks.ALIEN_GLYPH, ModBlocks.ALIEN_ARTIFACT));
    }

    private static void tab(String name, Supplier<ItemLike> icon, Supplier<List<ItemLike>> items) {
        Category category = new Category(name, icon, items);
        CATEGORIES.add(category);
        Identifier id = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name);
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(category.title())
                .icon(() -> new ItemStack(icon.get()))
                .displayItems((parameters, output) -> items.get().forEach(output::accept))
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceKey.create(Registries.CREATIVE_MODE_TAB, id), tab);
    }
}
