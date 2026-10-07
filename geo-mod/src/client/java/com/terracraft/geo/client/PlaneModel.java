package com.terracraft.geo.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Avion léger : fuselage, cockpit, ailes, empennage, hélice et train d'atterrissage. Texture 256 × 128. */
public class PlaneModel extends EntityModel<PlaneRenderState> {
    private final ModelPart propeller;

    public PlaneModel(ModelPart root) {
        super(root);
        propeller = root.getChild("propeller");
    }

    @Override
    public void setupAnim(PlaneRenderState state) {
        super.setupAnim(state);
        propeller.zRot = state.propeller;
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fuselage", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -14, -24, 10, 10, 48), PartPose.ZERO);
        root.addOrReplaceChild("cockpit", CubeListBuilder.create().texOffs(120, 0).addBox(-4, -19, -2, 8, 5, 12), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(120, 20).addBox(-4, -13, 24, 8, 8, 4), PartPose.ZERO);
        root.addOrReplaceChild("wings", CubeListBuilder.create().texOffs(0, 60).addBox(-36, -11, -2, 72, 2, 14), PartPose.ZERO);
        root.addOrReplaceChild("stabilizer", CubeListBuilder.create().texOffs(0, 80).addBox(-14, -12, -26, 28, 2, 8), PartPose.ZERO);
        root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(80, 80).addBox(-1, -24, -26, 2, 10, 8), PartPose.ZERO);
        root.addOrReplaceChild("propeller", CubeListBuilder.create()
                .texOffs(180, 0).addBox(-12, -1, 0, 24, 2, 1)
                .texOffs(180, 10).addBox(-1, -1, -1, 2, 2, 2), PartPose.offset(0, -9, 28));
        root.addOrReplaceChild("gear", CubeListBuilder.create()
                .texOffs(200, 20).addBox(-9, -4, 8, 2, 4, 4)
                .texOffs(200, 20).addBox(7, -4, 8, 2, 4, 4)
                .texOffs(200, 20).addBox(-1, -4, -22, 2, 4, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 128);
    }
}
