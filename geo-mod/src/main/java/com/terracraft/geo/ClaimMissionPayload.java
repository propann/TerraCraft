package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClaimMissionPayload(String id) implements CustomPacketPayload {
    public static final Type<ClaimMissionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "claim_mission"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMissionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ClaimMissionPayload::id, ClaimMissionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
