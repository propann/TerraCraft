package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serveur → client : fiche de personnage (JSON). */
public record SheetPayload(String json) implements CustomPacketPayload {
    public static final Type<SheetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "sheet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SheetPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), SheetPayload::json, SheetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
