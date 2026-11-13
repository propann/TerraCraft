package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : état de la conversation et identité du personnage. */
public record NpcChatStatePayload(int entityId, String json) implements CustomPacketPayload {
    public static final Type<NpcChatStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "npc_chat_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcChatStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcChatStatePayload::entityId,
            ByteBufCodecs.stringUtf8(16384), NpcChatStatePayload::json,
            NpcChatStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
