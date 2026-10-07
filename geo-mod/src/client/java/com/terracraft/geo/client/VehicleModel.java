package com.terracraft.geo.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Modèle cubique des véhicules (unités : 1/16 de bloc, y négatif vers le haut, avant vers +z).
 * Les roues et le turbo ne s'affichent que s'ils sont montés.
 */
public class VehicleModel extends EntityModel<VehicleRenderState> {
    private static final String[] WHEELS = {"wheel_fl", "wheel_fr", "wheel_bl", "wheel_br"};
    private final ModelPart[] wheels = new ModelPart[4];
    private final ModelPart turbo;

    public VehicleModel(ModelPart root) {
        super(root);
        for (int i = 0; i < 4; i++) {
            wheels[i] = root.getChild(WHEELS[i]);
        }
        turbo = root.getChild("turbo");
    }

    @Override
    public void setupAnim(VehicleRenderState state) {
        super.setupAnim(state);
        for (int i = 0; i < 4; i++) {
            wheels[i].visible = i < state.wheels;
        }
        turbo.visible = state.turbo;
    }

    /** Voiture : 26 × 48 px, habitacle vitré au centre. Texture 256 × 128. */
    public static LayerDefinition car() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-13, -13, -24, 26, 8, 48), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 56).addBox(-11, -22, -10, 22, 9, 20), PartPose.ZERO);
        wheels(root, 13.5f, 15, 8, 160, 0);
        root.addOrReplaceChild("turbo", CubeListBuilder.create().texOffs(160, 20).addBox(-3, -15, 14, 6, 2, 6), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 128);
    }

    /** Moto : cadre étroit, selle et deux roues alignées. */
    public static LayerDefinition motorcycle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -9, -16, 10, 5, 32), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 20).addBox(-4, -15, -5, 8, 6, 12), PartPose.ZERO);
        root.addOrReplaceChild("wheel_fl", CubeListBuilder.create().texOffs(48, 0).addBox(-2, -10, -3, 4, 20, 6), PartPose.offset(0, 0, 15));
        root.addOrReplaceChild("wheel_fr", CubeListBuilder.create().texOffs(48, 0).addBox(-2, -10, -3, 4, 20, 6), PartPose.offset(0, 0, -15));
        root.addOrReplaceChild("wheel_bl", CubeListBuilder.create().texOffs(48, 0).addBox(-2, -10, -3, 4, 20, 6), PartPose.offset(0, 0, 15));
        root.addOrReplaceChild("wheel_br", CubeListBuilder.create().texOffs(48, 0).addBox(-2, -10, -3, 4, 20, 6), PartPose.offset(0, 0, -15));
        root.addOrReplaceChild("turbo", CubeListBuilder.create().texOffs(48, 26).addBox(-2, -12, 8, 4, 2, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Camion : cabine à l'avant, plateau à l'arrière. Texture 256 × 256. */
    public static LayerDefinition truck() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-16, -15, -36, 32, 8, 72), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 80).addBox(-15, -34, 14, 30, 19, 20), PartPose.ZERO);
        root.addOrReplaceChild("bed", CubeListBuilder.create().texOffs(0, 120).addBox(-16, -23, -36, 32, 8, 48), PartPose.ZERO);
        wheels(root, 16.5f, 24, 10, 210, 0);
        root.addOrReplaceChild("turbo", CubeListBuilder.create().texOffs(210, 24).addBox(-3, -36, 26, 6, 2, 6), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void wheels(PartDefinition root, float side, float along, int size, int u, int v) {
        float[][] positions = {{side, along}, {-side, along}, {side, -along}, {-side, -along}};
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild(WHEELS[i],
                    CubeListBuilder.create().texOffs(u, v).addBox(-2, -size, -size / 2f, 4, size, size),
                    PartPose.offset(positions[i][0], 0, positions[i][1]));
        }
    }
}
