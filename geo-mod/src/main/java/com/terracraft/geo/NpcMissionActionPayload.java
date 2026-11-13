package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : consulter ou réclamer l'objectif lié à la conversation. */
public record NpcMissionActionPayload(int entityId, boolean claim) implements CustomPacketPayload {
    public static final Type<NpcMissionActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "npc_mission_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcMissionActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcMissionActionPayload::entityId,
            ByteBufCodecs.BOOL, NpcMissionActionPayload::claim,
            NpcMissionActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
