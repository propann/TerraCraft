package com.terracraft.geo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.Rocket;
import com.terracraft.geo.content.Spaceship;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Rendu du vaisseau ; il vibre pendant le compte à rebours et le décollage, comme la fusée. */
public class SpaceshipRenderer extends EntityRenderer<Spaceship, RocketRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "spaceship"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/spaceship.png");
    private final SpaceshipModel model;

    public SpaceshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new SpaceshipModel(context.bakeLayer(LAYER));
        this.shadowRadius = 1.6f;
    }

    @Override
    public RocketRenderState createRenderState() {
        return new RocketRenderState();
    }

    @Override
    public void extractRenderState(Spaceship ship, RocketRenderState state, float partialTicks) {
        super.extractRenderState(ship, state, partialTicks);
        state.yRot = ship.getYRot(partialTicks);
        state.parts = ship.parts();
        state.tanks = ship.tanks();
        state.shaking = ship.phase() == Rocket.COUNTDOWN || ship.phase() == Rocket.ASCENT;
    }

    @Override
    public void submit(RocketRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        if (state.shaking) {
            poseStack.translate(Mth.sin(state.ageInTicks * 3.1f) * 0.03f, 0, Mth.cos(state.ageInTicks * 2.7f) * 0.03f);
        }
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
