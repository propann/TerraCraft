package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BuyListingPayload(long id) implements CustomPacketPayload {
    public static final Type<BuyListingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "buy_listing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuyListingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BuyListingPayload::id, BuyListingPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
