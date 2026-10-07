package com.terracraft.geo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.Plane;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Rendu de l'avion : cap, assiette, inclinaison dans les virages et hélice qui tourne. */
public class PlaneRenderer extends EntityRenderer<Plane, PlaneRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "plane"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/plane.png");

    private final PlaneModel model;

    public PlaneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PlaneModel(context.bakeLayer(LAYER));
        this.shadowRadius = 1.4f;
    }

    @Override
    public PlaneRenderState createRenderState() {
        return new PlaneRenderState();
    }

    @Override
    public void extractRenderState(Plane plane, PlaneRenderState state, float partialTicks) {
        super.extractRenderState(plane, state, partialTicks);
        state.yRot = plane.getYRot(partialTicks);
        state.xRot = plane.getXRot(partialTicks);
        state.roll = Mth.clamp(Mth.wrapDegrees(plane.getYRot() - plane.yRotO) * 8f, -35f, 35f);
        boolean running = plane.isVehicle() && plane.fuel() > 0;
        state.propeller = running ? (state.ageInTicks * 1.4f) % Mth.TWO_PI : 0.3f;
        state.hurtTime = plane.getHurtTime() - partialTicks;
        state.hurtDir = plane.getHurtDir();
    }

    @Override
    public void submit(PlaneRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        poseStack.rotateDegrees(Axis.XP, state.xRot);
        poseStack.rotateDegrees(Axis.ZP, state.roll);
        if (state.hurtTime > 0) {
            poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime * 0.4f * state.hurtDir);
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
