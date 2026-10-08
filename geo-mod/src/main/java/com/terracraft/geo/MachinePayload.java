package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : état d'une machine (écran à jauges). */
public record MachinePayload(String json) implements CustomPacketPayload {
    public static final Type<MachinePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachinePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(8192), MachinePayload::json, MachinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
