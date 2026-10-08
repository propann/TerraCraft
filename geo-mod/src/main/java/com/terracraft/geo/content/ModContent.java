package com.terracraft.geo.content;

import com.terracraft.geo.GeoMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
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
    public static final EntityType<Vehicle> MOTORCYCLE = entity("motorcycle", Vehicle.Kind.MOTORCYCLE, 1.1f, 1.5f);
    public static final EntityType<Vehicle> ROVER = entity("rover", Vehicle.Kind.ROVER, 2.0f, 1.5f);
    public static final EntityType<Rocket> ROCKET = rocketType();
    public static final EntityType<Plane> PLANE = planeType();

    public static final Item WHEEL = item("wheel", Item::new, new Item.Properties().stacksTo(16));
    public static final Item ENGINE = item("engine", Item::new, new Item.Properties().stacksTo(4));
    public static final Item RADIATOR = item("radiator", Item::new, new Item.Properties().stacksTo(4));
    public static final Item BATTERY = item("battery", Item::new, new Item.Properties().stacksTo(4));
    public static final Item TURBO = item("turbo", Item::new, new Item.Properties().stacksTo(4));
    /** Bidon d'essence : véhicules (clic droit sur le véhicule) et jetpack porté (clic droit dans le vide). */
    public static final Item FUEL_CAN = item("fuel_can", FuelCanItem::new, new Item.Properties().stacksTo(8));
    public static final Item CAR_CHASSIS = item("car_chassis", p -> new ChassisItem(() -> CAR, p), new Item.Properties().stacksTo(1));
    public static final Item TRUCK_CHASSIS = item("truck_chassis", p -> new ChassisItem(() -> TRUCK, p), new Item.Properties().stacksTo(1));
    public static final Item MOTORCYCLE_CHASSIS = item("motorcycle_chassis", p -> new ChassisItem(() -> MOTORCYCLE, p), new Item.Properties().stacksTo(1));

    /** Kit d'avion : posé au sol, il devient un avion complet (il ne manque que le carburant). */
    public static final Item PLANE_KIT = item("plane_kit", p -> new ChassisItem(() -> PLANE, p), new Item.Properties().stacksTo(1));
    /**
     * Charge utile : kit de station orbitale. Chargé dans une fusée, il déploie à l'arrivée en orbite terrestre une
     * station complète (salle de travail, tunnels vitrés, stockage, quai d'amarrage, sas) si le pilote n'en a pas.
     */
    public static final Item ORBITAL_STATION_KIT = item("orbital_station_kit", Item::new, new Item.Properties().stacksTo(1));
    /** Charges utiles des bases de surface : déployées à l'atterrissage sur la Lune ou sur Mars. */
    public static final Item LUNAR_BASE_KIT = item("lunar_base_kit", Item::new, new Item.Properties().stacksTo(1));
    public static final Item MARS_BASE_KIT = item("mars_base_kit", Item::new, new Item.Properties().stacksTo(1));
    /** Rover lunaire en caisse : posé au sol (clic droit) ou déposé par la fusée à l'atterrissage. */
    public static final Item ROVER_KIT = item("rover_kit", RoverKitItem::new, new Item.Properties().stacksTo(1));
    /** Kit de module de station : construit un module pressurisé de 7 × 5 × 7 dans l'espace. */
    public static final Item STATION_MODULE = item("station_module", StationModuleItem::new, new Item.Properties().stacksTo(16));
    public static final Item ROCKET_HULL = item("rocket_hull", p -> new ChassisItem(() -> ROCKET, p), new Item.Properties().stacksTo(1));
    public static final Item ROCKET_ENGINE = item("rocket_engine", Item::new, new Item.Properties().stacksTo(1));
    public static final Item ROCKET_TANK = item("rocket_tank", Item::new, new Item.Properties().stacksTo(1));
    public static final Item NOSE_CONE = item("nose_cone", Item::new, new Item.Properties().stacksTo(1));
    public static final Item FINS = item("fins", Item::new, new Item.Properties().stacksTo(1));
    public static final Item ROCKET_FUEL = item("rocket_fuel", Item::new, new Item.Properties().stacksTo(16));
    /**
     * Casque spatial : la durabilité est la réserve d'oxygène (600 s). Il se porte dans la
     * combinaison (touche J), pas dans l'emplacement tête : un casque normal reste possible dessous.
     */
    public static final Item SPACE_HELMET = item("space_helmet", SuitPieceItem::new, new Item.Properties().durability(600));
    /** Combinaison : consommation d'oxygène réduite de 25 %. */
    public static final Item SPACE_SUIT = item("space_suit", SuitPieceItem::new, new Item.Properties().stacksTo(1));
    /** Bottes magnétiques : adhérence en orbite, pas de dégâts de chute hors de la Terre. */
    public static final Item MAGNETIC_BOOTS = item("magnetic_boots", SuitPieceItem::new, new Item.Properties().stacksTo(1));
    /** Jetpack : la durabilité est le carburant (600 ticks = 30 s de poussée). */
    public static final Item JETPACK = item("jetpack", SuitPieceItem::new, new Item.Properties().durability(600));
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
            for (Item item : new Item[]{CAR_CHASSIS, TRUCK_CHASSIS, MOTORCYCLE_CHASSIS, PLANE_KIT, WHEEL, ENGINE, RADIATOR, BATTERY, TURBO, FUEL_CAN,
                    ROCKET_HULL, ROCKET_ENGINE, ROCKET_TANK, NOSE_CONE, FINS, ROCKET_FUEL, SPACE_HELMET, SPACE_SUIT, MAGNETIC_BOOTS, JETPACK, OXYGEN_TANK, STATION_MODULE, ORBITAL_STATION_KIT, LUNAR_BASE_KIT, MARS_BASE_KIT, ROVER_KIT}) {
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

    public static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
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

    private static EntityType<Plane> planeType() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "plane"));
        EntityType<Plane> type = EntityType.Builder.<Plane>of(Plane::new, MobCategory.MISC)
                .sized(2.6f, 1.4f)
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
