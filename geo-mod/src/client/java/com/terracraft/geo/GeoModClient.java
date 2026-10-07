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
    private static final int SNIPER_FOV = 22;
    private static Integer fovBeforeSniper = null;

    static void openSuit(Minecraft client) {
        if (client.player != null && ClientPlayNetworking.canSend(OpenSuitPayload.TYPE)) {
            ClientPlayNetworking.send(OpenSuitPayload.INSTANCE);
        }
    }

    @Override
    public void onInitializeClient() {
        // Le serveur décide quand ouvrir la carte (premier choix ou /terracraft depart).
        ClientPlayNetworking.registerGlobalReceiver(OpenMapPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new WorldMapScreen(payload.required())));

        // Fiche de personnage : touche K, le serveur renvoie la fiche à jour.
        KeyMapping sheetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.terracraft_geo.sheet",
                InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY));
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.terracraft_geo.menu",
                InputConstants.KEY_O, KeyMapping.Category.GAMEPLAY));
        KeyMapping suitKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.terracraft_geo.suit",
                InputConstants.KEY_J, KeyMapping.Category.GAMEPLAY));
        // Combinaison spatiale : fenêtre (le serveur ouvre le menu) et bandeau d'oxygène hors de la Terre.
        net.minecraft.client.gui.screens.MenuScreens.register(SpaceSuit.MENU, SuitScreen::new);
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "oxygen"), new OxygenHud());
        ClientPlayNetworking.registerGlobalReceiver(SheetPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new CharacterSheetScreen(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(MissionPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new MissionsScreen(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(MarketPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new MarketScreen(payload.json())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sheetKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(RequestSheetPayload.TYPE)) {
                    ClientPlayNetworking.send(RequestSheetPayload.INSTANCE);
                }
            }
            while (suitKey.consumeClick()) {
                openSuit(client);
            }
            while (menuKey.consumeClick()) {
                if (client.player != null) {
                    client.setScreenAndShow(new TerraCraftMenuScreen());
                }
            }
        });

        // Véhicules : modèles, rendu, et touches de déplacement transmises au véhicule conduit.
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.CAR_LAYER, VehicleModel::car);
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.TRUCK_LAYER, VehicleModel::truck);
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.MOTORCYCLE_LAYER, VehicleModel::motorcycle);
        EntityRendererRegistry.register(ModContent.CAR, context -> new VehicleRenderer(context, false));
        EntityRendererRegistry.register(ModContent.TRUCK, context -> new VehicleRenderer(context, true));
        EntityRendererRegistry.register(ModContent.MOTORCYCLE, context -> new VehicleRenderer(context, Vehicle.Kind.MOTORCYCLE));
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
                // Le véhicule ne recevait auparavant que A/D : la souris tournait la
                // caméra, mais jamais la voiture. En conduite, la direction regardée
                // devient la direction du véhicule ; la rotation reste côté client,
                // comme pour les bateaux vanilla.
                vehicle.setYRot(client.player.getYRot());
                vehicle.setYHeadRot(client.player.getYRot());
                vehicle.setInput(keys.left(), keys.right(), keys.forward(), keys.backward());
            }

            // Zoom propre et réversible du sniper : clic droit maintenu, sans
            // modifier définitivement le réglage FOV du joueur.
            boolean zooming = client.player != null
                    && client.options.keyUse.isDown()
                    && client.player.getMainHandItem().is(ModContent.SNIPER);
            if (zooming) {
                if (fovBeforeSniper == null) {
                    fovBeforeSniper = client.options.fov().get();
                }
                if (client.options.fov().get() != SNIPER_FOV) {
                    client.options.fov().set(SNIPER_FOV);
                }
            } else if (fovBeforeSniper != null) {
                client.options.fov().set(fovBeforeSniper);
                fovBeforeSniper = null;
            }
        });
    }

    public static void sendStartPoint(double latitude, double longitude, String label) {
        String safeLabel = label.length() > StartPoints.MAX_LABEL_LENGTH
                ? label.substring(0, StartPoints.MAX_LABEL_LENGTH) : label;
        ClientPlayNetworking.send(new StartPointPayload(latitude, longitude, safeLabel));
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(null);
        // Après un clic dans la carte, Minecraft peut laisser le curseur libre : les
        // touches fonctionnent encore mais le joueur ne peut plus tourner la caméra.
        client.mouseHandler.grabMouse();
    }
}
