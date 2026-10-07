package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : objectif « Premiers pas » en cours ({@code step >= total} : parcours terminé). */
public record TutorialPayload(int step, int total, String title, String hint) implements CustomPacketPayload {
    public static final Type<TutorialPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "tutorial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TutorialPayload::step,
            ByteBufCodecs.VAR_INT, TutorialPayload::total,
            ByteBufCodecs.stringUtf8(128), TutorialPayload::title,
            ByteBufCodecs.stringUtf8(512), TutorialPayload::hint,
            TutorialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
