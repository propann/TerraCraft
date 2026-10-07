package com.terracraft.geo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → serveur : installer l'amélioration d'un plan sur la fusée garée à côté. */
public record InstallUpgradePayload(String plan) implements CustomPacketPayload {
    public static final Type<InstallUpgradePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "install_upgrade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InstallUpgradePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), InstallUpgradePayload::plan, InstallUpgradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
