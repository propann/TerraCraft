package com.terracraft.geo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.Rocket;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Rendu de la fusée ; elle tremble pendant le compte à rebours et le décollage. */
public class RocketRenderer extends EntityRenderer<Rocket, RocketRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "rocket"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/rocket.png");
    private final RocketModel model;

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RocketModel(context.bakeLayer(LAYER));
        this.shadowRadius = 0.8f;
    }

    @Override
    public RocketRenderState createRenderState() {
        return new RocketRenderState();
    }

    @Override
    public void extractRenderState(Rocket rocket, RocketRenderState state, float partialTicks) {
        super.extractRenderState(rocket, state, partialTicks);
        state.yRot = rocket.getYRot(partialTicks);
        state.parts = rocket.parts();
        state.tanks = rocket.tanks();
        state.shaking = rocket.phase() == Rocket.COUNTDOWN || rocket.phase() == Rocket.ASCENT;
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
