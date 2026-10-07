package com.terracraft.geo.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** État de rendu de l'avion : cap, assiette, inclinaison en virage et hélice. */
public class PlaneRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public float roll;
    public float propeller;
    public float hurtTime;
    public int hurtDir;
}
