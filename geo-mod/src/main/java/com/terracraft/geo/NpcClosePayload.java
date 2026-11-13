package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : ferme la conversation active. */
public record NpcClosePayload(int entityId) implements CustomPacketPayload {
    public static final Type<NpcClosePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "npc_close"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcClosePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcClosePayload::entityId, NpcClosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
