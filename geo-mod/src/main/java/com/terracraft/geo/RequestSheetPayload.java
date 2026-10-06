package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : « envoie-moi ma fiche de personnage » (touche K). */
public record RequestSheetPayload() implements CustomPacketPayload {
    public static final RequestSheetPayload INSTANCE = new RequestSheetPayload();
    public static final Type<RequestSheetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "request_sheet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSheetPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
