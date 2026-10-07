package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : l'avion piloté vient de percuter un obstacle (impact en blocs/tick). */
public record PlaneCrashPayload(float impact) implements CustomPacketPayload {
    public static final Type<PlaneCrashPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "plane_crash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaneCrashPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, PlaneCrashPayload::impact, PlaneCrashPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
