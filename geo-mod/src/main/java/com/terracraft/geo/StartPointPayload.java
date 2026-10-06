package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : point de départ confirmé sur la carte du monde. */
public record StartPointPayload(double latitude, double longitude, String label) implements CustomPacketPayload {
    public static final Type<StartPointPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "start_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartPointPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, StartPointPayload::latitude,
            ByteBufCodecs.DOUBLE, StartPointPayload::longitude,
            ByteBufCodecs.stringUtf8(StartPoints.MAX_LABEL_LENGTH), StartPointPayload::label,
            StartPointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
