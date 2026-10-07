package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : contenu de l'écran de l'atelier de station. */
public record WorkshopPayload(String json) implements CustomPacketPayload {
    public static final Type<WorkshopPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "workshop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkshopPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), WorkshopPayload::json, WorkshopPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
