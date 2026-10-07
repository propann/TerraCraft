package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : contenu de la carte des étoiles (destinations, coûts, obstacles). */
public record StarMapPayload(String json) implements CustomPacketPayload {
    public static final Type<StarMapPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "star_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StarMapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), StarMapPayload::json, StarMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
