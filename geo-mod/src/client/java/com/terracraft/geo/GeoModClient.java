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

    static void openVehicleStorage(Minecraft client) {
        if (client.player != null && ClientPlayNetworking.canSend(OpenVehicleStoragePayload.TYPE)) {
            ClientPlayNetworking.send(OpenVehicleStoragePayload.INSTANCE);
        }
    }

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
        KeyMapping storageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.terracraft_geo.vehicle_storage",
                InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY));
        // Combinaison spatiale : fenêtre (le serveur ouvre le menu) et bandeau d'oxygène hors de la Terre.
        net.minecraft.client.gui.screens.MenuScreens.register(SpaceSuit.MENU, SuitScreen::new);
        // Équipement spatial dessiné sur le joueur comme une armure (par-dessus l'armure normale).
        com.terracraft.geo.client.SpaceSuitLayer.registerModels();
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register(
                (type, renderer, helper, context) -> {
                    if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
                        @SuppressWarnings("unchecked")
                        var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<
                                net.minecraft.client.renderer.entity.state.AvatarRenderState,
                                net.minecraft.client.model.player.PlayerModel>) (Object) avatar;
                        helper.register(new com.terracraft.geo.client.SpaceSuitLayer(parent, context));
                    }
                });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "oxygen"), new OxygenHud());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "jetpack"), new JetpackHud());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "plane"), new PlaneHud());
        // Parcours « Premiers pas » : le serveur envoie l'objectif en cours, affiché à droite.
        ClientPlayNetworking.registerGlobalReceiver(TutorialPayload.TYPE, (payload, context) -> TutorialHud.update(payload));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                TutorialHud.clear());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "tutorial"), new TutorialHud());
        ClientPlayNetworking.registerGlobalReceiver(SheetPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new CharacterSheetScreen(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(MissionPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new MissionsScreen(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(WorkshopPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new WorkshopScreen(payload.json())));
        ClientPlayNetworking.registerGlobalReceiver(MarketPayload.TYPE, (payload, context) ->
                context.client().gui.setScreen(new MarketScreen(payload.json())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sheetKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(RequestSheetPayload.TYPE)) {
                    ClientPlayNetworking.send(RequestSheetPayload.INSTANCE);
                }
            }
            // Jetpack : poussée appliquée côté client (le serveur décompte le carburant).
            if (client.player != null && client.gui.screen() == null
                    && Jetpack.canThrust(client.player, client.options.keyJump.isDown())) {
                client.player.setDeltaMovement(Jetpack.thrust(client.player.getDeltaMovement()));
            }
            while (storageKey.consumeClick()) {
                openVehicleStorage(client);
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
        ModelLayerRegistry.registerModelLayer(VehicleRenderer.ROVER_LAYER, VehicleModel::rover);
        EntityRendererRegistry.register(ModContent.ROVER, context -> new VehicleRenderer(context, Vehicle.Kind.ROVER));
        ModelLayerRegistry.registerModelLayer(RocketRenderer.LAYER, RocketModel::create);
        EntityRendererRegistry.register(ModContent.ROCKET, RocketRenderer::new);
        ModelLayerRegistry.registerModelLayer(com.terracraft.geo.client.PlaneRenderer.LAYER, com.terracraft.geo.client.PlaneModel::create);
        EntityRendererRegistry.register(ModContent.PLANE, com.terracraft.geo.client.PlaneRenderer::new);
        com.terracraft.geo.content.Plane.crashReporter = impact -> {
            if (ClientPlayNetworking.canSend(PlaneCrashPayload.TYPE)) {
                ClientPlayNetworking.send(new PlaneCrashPayload((float) impact));
            }
        };
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
            if (client.player != null && client.player.getVehicle() instanceof com.terracraft.geo.content.Plane plane) {
                Input keys = client.player.input.keyPresses;
                plane.setInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.sprint());
            }
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
