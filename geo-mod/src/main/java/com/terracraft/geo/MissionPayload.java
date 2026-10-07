package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : missions et progression du joueur. */
public record MissionPayload(String json) implements CustomPacketPayload {
    public static final Type<MissionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "missions"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MissionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), MissionPayload::json, MissionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
