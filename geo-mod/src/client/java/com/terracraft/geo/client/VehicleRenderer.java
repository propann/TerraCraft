package com.terracraft.geo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.content.Vehicle;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Rendu des véhicules : modèle cubique orienté selon le cap, secousse quand on les frappe. */
public class VehicleRenderer extends EntityRenderer<Vehicle, VehicleRenderState> {
    public static final ModelLayerLocation CAR_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "car"), "main");
    public static final ModelLayerLocation TRUCK_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "truck"), "main");

    private final VehicleModel model;
    private final Identifier texture;

    public VehicleRenderer(EntityRendererProvider.Context context, boolean truck) {
        super(context);
        this.model = new VehicleModel(context.bakeLayer(truck ? TRUCK_LAYER : CAR_LAYER));
        this.texture = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "textures/entity/" + (truck ? "truck" : "car") + ".png");
        this.shadowRadius = truck ? 1.3f : 1.0f;
    }

    @Override
    public VehicleRenderState createRenderState() {
        return new VehicleRenderState();
    }

    @Override
    public void extractRenderState(Vehicle vehicle, VehicleRenderState state, float partialTicks) {
        super.extractRenderState(vehicle, state, partialTicks);
        state.yRot = vehicle.getYRot(partialTicks);
        state.hurtTime = vehicle.getHurtTime() - partialTicks;
        state.hurtDir = vehicle.getHurtDir();
        state.wheels = vehicle.wheels();
        state.turbo = vehicle.has(Vehicle.TURBO);
        state.truck = vehicle.kind() == Vehicle.Kind.TRUCK;
    }

    @Override
    public void submit(VehicleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
        if (state.hurtTime > 0) {
            poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.hurtTime) * state.hurtTime * 0.4f * state.hurtDir);
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        collector.submitModel(model, state, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
