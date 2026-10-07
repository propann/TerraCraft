package com.terracraft.geo.client;

import com.terracraft.geo.content.Rocket;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Fusée : coque blanche, et pièces visibles une fois montées (moteur, réservoir, cône, ailerons). */
public class RocketModel extends EntityModel<RocketRenderState> {
    private final ModelPart engine;
    private final ModelPart tank;
    private final ModelPart nose;
    private final ModelPart fins;
    private final ModelPart[] boosters = new ModelPart[4];

    public RocketModel(ModelPart root) {
        super(root);
        engine = root.getChild("engine");
        tank = root.getChild("tank");
        nose = root.getChild("nose");
        fins = root.getChild("fins");
        for (int i = 0; i < boosters.length; i++) {
            boosters[i] = root.getChild("booster_" + i);
        }
    }

    @Override
    public void setupAnim(RocketRenderState state) {
        super.setupAnim(state);
        engine.visible = (state.parts & Rocket.ENGINE) != 0;
        tank.visible = (state.parts & Rocket.TANK) != 0;
        nose.visible = (state.parts & Rocket.NOSE) != 0;
        fins.visible = (state.parts & Rocket.FINS) != 0;
        // 1 réservoir : aucun propulseur ; 2 : est et ouest ; 3 : + nord ; 4 : les quatre.
        int shown = state.tanks >= 2 ? state.tanks : 0;
        for (int i = 0; i < boosters.length; i++) {
            boosters[i].visible = i < shown;
        }
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -56, -6, 12, 44, 12), PartPose.ZERO);
        root.addOrReplaceChild("engine", CubeListBuilder.create().texOffs(50, 0).addBox(-4, -12, -4, 8, 12, 8), PartPose.ZERO);
        root.addOrReplaceChild("tank", CubeListBuilder.create().texOffs(0, 84).addBox(-6.5f, -40, -6.5f, 13, 10, 13), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(50, 24).addBox(-4, -64, -4, 8, 8, 8)
                .texOffs(50, 44).addBox(-2, -68, -2, 4, 4, 4), PartPose.ZERO);
        root.addOrReplaceChild("fins", CubeListBuilder.create()
                .texOffs(0, 60).addBox(-1, -22, 6, 2, 14, 6)
                .texOffs(0, 60).addBox(-1, -22, -12, 2, 14, 6)
                .texOffs(0, 60).addBox(6, -22, -1, 6, 14, 2)
                .texOffs(0, 60).addBox(-12, -22, -1, 6, 14, 2), PartPose.ZERO);
        int[][] offsets = {{10, 0}, {-10, 0}, {0, 10}, {0, -10}};
        for (int i = 0; i < offsets.length; i++) {
            root.addOrReplaceChild("booster_" + i, CubeListBuilder.create().texOffs(80, 60).addBox(-2, -26, -2, 4, 18, 4),
                    PartPose.offset(offsets[i][0], 0, offsets[i][1]));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }
}
