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

    /**
     * Moto : cadre fin, réservoir et selle, fourche et guidon, pot d'échappement, deux roues de 10 px alignées (seules
     * les deux premières roues sont visibles : la moto en exige deux). Texture 128 × 64 (motorcycle.png).
     */
    public static LayerDefinition motorcycle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -12, -14, 6, 4, 28), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 32).addBox(-4, -16, -6, 8, 4, 14), PartPose.ZERO);
        root.addOrReplaceChild("fork", CubeListBuilder.create().texOffs(100, 0).addBox(-1, -20, 10, 2, 12, 2), PartPose.ZERO);
        root.addOrReplaceChild("bars", CubeListBuilder.create().texOffs(70, 24).addBox(-7, -21, 10, 14, 1, 1), PartPose.ZERO);
        // Roues : avant et arrière visibles, les deux suivantes (cachées : la moto n'en exige que deux) au même endroit.
        float[] along = {11, -11, 11, -11};
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild(WHEELS[i], CubeListBuilder.create().texOffs(70, 0).addBox(-2, -10, -5, 4, 10, 10),
                    PartPose.offset(0, 0, along[i]));
        }
        root.addOrReplaceChild("turbo", CubeListBuilder.create().texOffs(70, 40).addBox(3, -9, -14, 2, 2, 8), PartPose.ZERO);
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

    /** Rover lunaire : châssis bas, cabine ouverte, panneau solaire, grandes roues. Texture 256 × 128. */
    public static LayerDefinition rover() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-12, -12, -20, 24, 6, 40), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 50).addBox(-9, -20, -6, 18, 8, 14), PartPose.ZERO);
        root.addOrReplaceChild("panel", CubeListBuilder.create().texOffs(0, 80).addBox(-11, -23, -19, 22, 1, 14), PartPose.ZERO);
        wheels(root, 13.5f, 14, 9, 160, 0);
        root.addOrReplaceChild("turbo", CubeListBuilder.create().texOffs(160, 30).addBox(-2, -14, 18, 4, 2, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 128);
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
