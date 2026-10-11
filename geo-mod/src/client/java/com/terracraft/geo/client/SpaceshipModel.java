package com.terracraft.geo.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Petit vaisseau : fuselage horizontal, nez profilé, verrière de cockpit, ailes, deux réacteurs, dérive et quatre
 * pieds d'atterrissage. L'avant est vers -z ; les portes de sas sont peintes sur les deux flancs.
 */
public class SpaceshipModel extends EntityModel<RocketRenderState> {
    public SpaceshipModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fuselage", CubeListBuilder.create().texOffs(0, 0).addBox(-10, -26, -28, 20, 18, 52), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 72).addBox(-7, -23, -38, 14, 12, 10), PartPose.ZERO);
        root.addOrReplaceChild("canopy", CubeListBuilder.create().texOffs(50, 72).addBox(-6, -32, -26, 12, 6, 16), PartPose.ZERO);
        root.addOrReplaceChild("wings", CubeListBuilder.create()
                .texOffs(0, 96).addBox(-34, -17, -4, 24, 3, 22)
                .texOffs(96, 96).addBox(10, -17, -4, 24, 3, 22), PartPose.ZERO);
        root.addOrReplaceChild("engines", CubeListBuilder.create()
                .texOffs(0, 124).addBox(-9, -23, 24, 8, 10, 8)
                .texOffs(0, 124).addBox(1, -23, 24, 8, 10, 8), PartPose.ZERO);
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 124).addBox(-1, -38, 8, 2, 12, 16), PartPose.ZERO);
        root.addOrReplaceChild("legs", CubeListBuilder.create()
                .texOffs(80, 124).addBox(-9, -8, -20, 3, 8, 3)
                .texOffs(80, 124).addBox(6, -8, -20, 3, 8, 3)
                .texOffs(80, 124).addBox(-9, -8, 16, 3, 8, 3)
                .texOffs(80, 124).addBox(6, -8, 16, 3, 8, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }
}
