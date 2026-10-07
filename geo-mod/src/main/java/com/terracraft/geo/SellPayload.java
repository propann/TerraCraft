package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : mettre en vente l'objet tenu en main à ce prix. */
public record SellPayload(long price) implements CustomPacketPayload {
    public static final Type<SellPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "sell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SellPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SellPayload::price, SellPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
