package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestMarketPayload(int page) implements CustomPacketPayload {
    public static final RequestMarketPayload INSTANCE = new RequestMarketPayload(1);
    public static final Type<RequestMarketPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "request_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMarketPayload> CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    RequestMarketPayload::page, RequestMarketPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
