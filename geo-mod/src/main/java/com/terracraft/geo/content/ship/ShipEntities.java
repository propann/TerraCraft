package com.terracraft.geo.content.ship;

import com.terracraft.geo.GeoMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entités invisibles d'un vaisseau en vol : le porteur et les places de l'équipage. */
public final class ShipEntities {
    /** Suivi à chaque tick : le vaisseau se déplace de façon fluide chez les joueurs. */
    public static final EntityType<ShipCarrier> CARRIER = register("ship_carrier", EntityType.Builder.<ShipCarrier>of(ShipCarrier::new, MobCategory.MISC)
            .sized(0.5f, 0.5f).noSummon().clientTrackingRange(16).updateInterval(1));
    public static final EntityType<ShipSeat> SEAT = register("ship_seat", EntityType.Builder.<ShipSeat>of(ShipSeat::new, MobCategory.MISC)
            .sized(0.1f, 0.1f).noSummon().clientTrackingRange(16).updateInterval(1));

    private ShipEntities() {
    }

    public static void init() {
        GeoMod.LOGGER.info("TerraCraft : vaisseaux construits enregistrés.");
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
