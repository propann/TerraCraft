package com.terracraft.geo;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class GeoModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Le serveur décide quand ouvrir la carte (premier choix ou /terracraft depart).
        ClientPlayNetworking.registerGlobalReceiver(OpenMapPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new WorldMapScreen(payload.required())));
    }

    public static void sendStartPoint(double latitude, double longitude, String label) {
        String safeLabel = label.length() > StartPoints.MAX_LABEL_LENGTH
                ? label.substring(0, StartPoints.MAX_LABEL_LENGTH) : label;
        ClientPlayNetworking.send(new StartPointPayload(latitude, longitude, safeLabel));
        Minecraft.getInstance().gui.setScreen(null);
    }
}
