package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : ouvrir le coffre du véhicule (bouton du menu O ou touche V). */
public record OpenVehicleStoragePayload() implements CustomPacketPayload {
    public static final OpenVehicleStoragePayload INSTANCE = new OpenVehicleStoragePayload();
    public static final Type<OpenVehicleStoragePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "open_vehicle_storage"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVehicleStoragePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
