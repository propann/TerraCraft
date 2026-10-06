package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Serveur → client : ouvrir la carte du monde. {@code required} empêche de fermer l'écran
 * tant que le joueur n'a pas choisi son premier point de départ.
 */
public record OpenMapPayload(boolean required) implements CustomPacketPayload {
    public static final Type<OpenMapPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "open_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMapPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenMapPayload::required,
            OpenMapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
