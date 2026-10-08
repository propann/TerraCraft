package com.terracraft.geo;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : rafraîchir l'écran d'une machine (une fois par seconde tant qu'il est ouvert). */
public record RequestMachinePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<RequestMachinePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "request_machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMachinePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestMachinePayload::pos, RequestMachinePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
