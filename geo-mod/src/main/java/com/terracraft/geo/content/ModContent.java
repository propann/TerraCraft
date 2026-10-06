package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/** Objets et entités du gameplay post-apocalyptique : pièces de véhicule, véhicules, armes. */
public final class ModContent {
    private ModContent() {
    }

    public static final EntityType<Vehicle> CAR = entity("car", Vehicle.Kind.CAR, 1.9f, 1.3f);
    public static final EntityType<Vehicle> TRUCK = entity("truck", Vehicle.Kind.TRUCK, 2.4f, 2.1f);
    public static final EntityType<Rocket> ROCKET = rocketType();

    public static final Item WHEEL = item("wheel", Item::new, new Item.Properties().stacksTo(16));
    public static final Item ENGINE = item("engine", Item::new, new Item.Properties().stacksTo(4));
    public static final Item RADIATOR = item("radiator", Item::new, new Item.Properties().stacksTo(4));
    public static final Item BATTERY = item("battery", Item::new, new Item.Properties().stacksTo(4));
    public static final Item TURBO = item("turbo", Item::new, new Item.Properties().stacksTo(4));
    public static final Item FUEL_CAN = item("fuel_can", Item::new, new Item.Properties().stacksTo(8));
    public static final Item CAR_CHASSIS = item("car_chassis", p -> new ChassisItem(() -> CAR, p), new Item.Properties().stacksTo(1));
    public static final Item TRUCK_CHASSIS = item("truck_chassis", p -> new ChassisItem(() -> TRUCK, p), new Item.Properties().stacksTo(1));

    public static final Item ROCKET_HULL = item("rocket_hull", p -> new ChassisItem(() -> ROCKET, p), new Item.Properties().stacksTo(1));
    public static final Item ROCKET_ENGINE = item("rocket_engine", Item::new, new Item.Properties().stacksTo(1));
    public static final Item ROCKET_TANK = item("rocket_tank", Item::new, new Item.Properties().stacksTo(1));
    public static final Item NOSE_CONE = item("nose_cone", Item::new, new Item.Properties().stacksTo(1));
    public static final Item FINS = item("fins", Item::new, new Item.Properties().stacksTo(1));
    public static final Item ROCKET_FUEL = item("rocket_fuel", Item::new, new Item.Properties().stacksTo(16));
    /** Casque spatial : la durabilité est la réserve d'oxygène (600 s). */
    public static final Item SPACE_HELMET = item("space_helmet", Item::new, new Item.Properties()
            .durability(600)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
    public static final Item OXYGEN_TANK = item("oxygen_tank", OxygenTankItem::new, new Item.Properties().stacksTo(16));

    public static final Item AMMO = item("ammo", Item::new, new Item.Properties().stacksTo(64));
    // Armes : dégâts, portée, ticks entre tirs, chargeur, rechargement, plombs, dispersion, recul, automatique.
    public static final Item PISTOL = gun("pistol", new GunItem.Stats(6f, 40, 6, 12, 30, 1, 0.01, 1.5f, false));
    public static final Item SMG = gun("smg", new GunItem.Stats(3.5f, 30, 3, 30, 40, 1, 0.045, 0.6f, true));
    public static final Item RIFLE = gun("rifle", new GunItem.Stats(12f, 90, 18, 8, 50, 1, 0.003, 3f, false));
    public static final Item SNIPER = gun("sniper", new GunItem.Stats(24f, 160, 40, 5, 60, 1, 0.0005, 6f, false));
    public static final Item SHOTGUN = gun("shotgun", new GunItem.Stats(3.5f, 20, 20, 6, 50, 6, 0.18, 5f, false));
    public static final Item GRENADE = item("grenade", GrenadeItem::new, new Item.Properties().stacksTo(16));
    /** Machette : arme de mêlée en fer, rapide. */
    public static final Item MACHETE = item("machete", Item::new, new Item.Properties().sword(ToolMaterial.IRON, 3.5f, -2.2f));
    public static final EntityType<Grenade> GRENADE_ENTITY = grenadeType();

    public static void init() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            for (Item item : new Item[]{CAR_CHASSIS, TRUCK_CHASSIS, WHEEL, ENGINE, RADIATOR, BATTERY, TURBO, FUEL_CAN,
                    ROCKET_HULL, ROCKET_ENGINE, ROCKET_TANK, NOSE_CONE, FINS, ROCKET_FUEL, SPACE_HELMET, OXYGEN_TANK}) {
                output.accept(item);
            }
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            for (Item item : new Item[]{PISTOL, SMG, RIFLE, SNIPER, SHOTGUN, AMMO, GRENADE, MACHETE}) {
                output.accept(item);
            }
        });
        GeoMod.LOGGER.info("TerraCraft : véhicules, pièces et armes enregistrés.");
    }

    /** Bit de pièce installable pour cet objet (0 si ce n'en est pas une). */
    static int partFlag(Item item) {
        if (item == ENGINE) {
            return Vehicle.ENGINE;
        }
        if (item == RADIATOR) {
            return Vehicle.RADIATOR;
        }
        if (item == BATTERY) {
            return Vehicle.BATTERY;
        }
        return item == TURBO ? Vehicle.TURBO : 0;
    }

    static int rocketPart(Item item) {
        if (item == ROCKET_ENGINE) {
            return Rocket.ENGINE;
        }
        if (item == ROCKET_TANK) {
            return Rocket.TANK;
        }
        if (item == NOSE_CONE) {
            return Rocket.NOSE;
        }
        return item == FINS ? Rocket.FINS : 0;
    }

    static Item rocketPartItem(int flag) {
        return switch (flag) {
            case Rocket.ENGINE -> ROCKET_ENGINE;
            case Rocket.TANK -> ROCKET_TANK;
            case Rocket.NOSE -> NOSE_CONE;
            default -> FINS;
        };
    }

    static Item partItem(int flag) {
        return switch (flag) {
            case Vehicle.ENGINE -> ENGINE;
            case Vehicle.RADIATOR -> RADIATOR;
            case Vehicle.BATTERY -> BATTERY;
            default -> TURBO;
        };
    }

    static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    private static Item gun(String name, GunItem.Stats stats) {
        return item(name, p -> new GunItem(stats, p), new Item.Properties());
    }

    private static EntityType<Grenade> grenadeType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "grenade"));
        EntityType<Grenade> type = EntityType.Builder.<Grenade>of(Grenade::new, MobCategory.MISC)
                .sized(0.25f, 0.25f)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    private static EntityType<Rocket> rocketType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "rocket"));
        EntityType<Rocket> type = EntityType.Builder.<Rocket>of(Rocket::new, MobCategory.MISC)
                .sized(1.2f, 4.0f)
                .clientTrackingRange(16)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    private static EntityType<Vehicle> entity(String name, Vehicle.Kind kind, float width, float height) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name));
        EntityType<Vehicle> type = EntityType.Builder.<Vehicle>of((t, level) -> new Vehicle(t, level, kind), MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(10)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }
}
