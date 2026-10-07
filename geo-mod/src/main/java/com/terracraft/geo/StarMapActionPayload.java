package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → serveur : depuis la carte des étoiles, mettre le cap sur une destination (et décoller si {@code launch}).
 * Une destination négative demande seulement l'ouverture de la carte (menu O).
 */
public record StarMapActionPayload(int rocket, byte destination, boolean launch) implements CustomPacketPayload {
    public static final Type<StarMapActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "star_map_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StarMapActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StarMapActionPayload::rocket,
            ByteBufCodecs.BYTE, StarMapActionPayload::destination,
            ByteBufCodecs.BOOL, StarMapActionPayload::launch,
            StarMapActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
