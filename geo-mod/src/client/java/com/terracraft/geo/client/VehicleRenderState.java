package com.terracraft.geo.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** État de rendu d'un véhicule : orientation, choc et pièces visibles. */
public class VehicleRenderState extends EntityRenderState {
    public float yRot;
    public float hurtTime;
    public int hurtDir;
    public int wheels;
    public boolean turbo;
    public boolean truck;
    public boolean motorcycle;
}
