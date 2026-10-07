package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestMissionsPayload() implements CustomPacketPayload {
    public static final RequestMissionsPayload INSTANCE = new RequestMissionsPayload();
    public static final Type<RequestMissionsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "request_missions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMissionsPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
