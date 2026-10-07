package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : acheter une offre du comptoir du serveur. */
public record BuyShopPayload(int index) implements CustomPacketPayload {
    public static final Type<BuyShopPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "buy_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuyShopPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuyShopPayload::index, BuyShopPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
