package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : touche de rechargement de l'arme en main. */
public record ReloadPayload() implements CustomPacketPayload {
    public static final ReloadPayload INSTANCE = new ReloadPayload();
    public static final Type<ReloadPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "reload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
