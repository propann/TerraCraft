package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : message saisi dans la conversation d'un PNJ. */
public record NpcChatMessagePayload(int entityId, String message) implements CustomPacketPayload {
    public static final Type<NpcChatMessagePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "npc_chat_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcChatMessagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcChatMessagePayload::entityId,
            ByteBufCodecs.stringUtf8(500), NpcChatMessagePayload::message,
            NpcChatMessagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
