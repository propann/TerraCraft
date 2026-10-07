package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MarketPayload(String json) implements CustomPacketPayload {
    public static final Type<MarketPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), MarketPayload::json, MarketPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
