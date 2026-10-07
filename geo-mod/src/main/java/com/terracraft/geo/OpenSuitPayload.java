package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : ouvrir la fenêtre de combinaison spatiale (touche J). */
public record OpenSuitPayload() implements CustomPacketPayload {
    public static final OpenSuitPayload INSTANCE = new OpenSuitPayload();
    public static final Type<OpenSuitPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "open_suit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSuitPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
