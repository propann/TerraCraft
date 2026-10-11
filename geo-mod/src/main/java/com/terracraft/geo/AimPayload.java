package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : le joueur commence ou arrête de viser (touche « Viser », voir content.Sights). */
public record AimPayload(boolean aiming) implements CustomPacketPayload {
    public static final Type<AimPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "aim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AimPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AimPayload::aiming, AimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
