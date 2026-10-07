package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : afficher une page de l'hôtel des ventes, filtrée par catégorie. */
public record RequestMarketPayload(int page, String category) implements CustomPacketPayload {
    public static final RequestMarketPayload INSTANCE = new RequestMarketPayload(1, "TOUT");
    public static final Type<RequestMarketPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "request_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMarketPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestMarketPayload::page,
            ByteBufCodecs.stringUtf8(32), RequestMarketPayload::category,
            RequestMarketPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
