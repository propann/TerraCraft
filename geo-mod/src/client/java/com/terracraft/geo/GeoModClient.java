package com.terracraft.geo;

import com.terracraft.geo.client.RocketModel;
import com.terracraft.geo.client.RocketRenderer;
import com.terracraft.geo.client.VehicleModel;
import com.terracraft.geo.client.VehicleRenderer;
import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.ModMobs;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import com.terracraft.geo.content.Vehicle;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.world.entity.player.Input;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class GeoModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Le serveur décide quand ouvrir la carte (premier choix ou /terracraft depart).
        ClientPlayNetworking.registerGlobalReceiver(OpenMapPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new WorldMapScreen(payload.required())));

        // Fiche de personnage : touche K, le serveur renvoie la fiche à jour.
        KeyMapping sheetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.terracraft_geo.sheet",
                InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY));
        ClientPlayNetworking.registerGlobalReceiver(SheetPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new CharacterSheetScreen(payload.json())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sheetKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(RequestSheetPayload.TYPE)) {
                    ClientPlayNetworking.send(RequestSheetPayload.INSTANCE);
                }
            }
        });

        // Véhicules : modèles, rendu, et touches de déplacement transmises au véhicule conduit.
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.CAR_LAYER, VehicleModel::car);
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.TRUCK_LAYER, VehicleModel::truck);
        EntityRendererRegistry.register(ModContent.CAR, context -> new VehicleRenderer(context, false));
        EntityRendererRegistry.register(ModContent.TRUCK, context -> new VehicleRenderer(context, true));
        ModelLayerRegistry.registerModelLayer(RocketRenderer.LAYER, RocketModel::create);
        EntityRendererRegistry.register(ModContent.ROCKET, RocketRenderer::new);
        EntityRendererRegistry.register(ModContent.GRENADE_ENTITY, ThrownItemRenderer::new);

        // Ennemis lunaires : modèles vanilla, textures TerraCraft.
        Identifier crawler = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/moon_crawler.png");
        Identifier astronaut = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/lost_astronaut.png");
        EntityRendererRegistry.register(ModMobs.MOON_CRAWLER, context -> new SpiderRenderer<ModMobs.MoonCrawler>(context) {
            @Override
            public Identifier getTextureLocation(LivingEntityRenderState state) {
                return crawler;
            }
        });
        EntityRendererRegistry.register(ModMobs.LOST_ASTRONAUT, context -> new ZombieRenderer(context) {
            @Override
            public Identifier getTextureLocation(ZombieRenderState state) {
                return astronaut;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.getVehicle() instanceof Vehicle vehicle) {
                Input keys = client.player.input.keyPresses;
                vehicle.setInput(keys.left(), keys.right(), keys.forward(), keys.backward());
            }
        });
    }

    public static void sendStartPoint(double latitude, double longitude, String label) {
        String safeLabel = label.length() > StartPoints.MAX_LABEL_LENGTH
                ? label.substring(0, StartPoints.MAX_LABEL_LENGTH) : label;
        ClientPlayNetworking.send(new StartPointPayload(latitude, longitude, safeLabel));
        Minecraft.getInstance().gui.setScreen(null);
    }
}
