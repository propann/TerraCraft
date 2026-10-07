package com.terracraft.geo.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class RocketRenderState extends EntityRenderState {
    public float yRot;
    public int parts;
    /** Réservoirs montés : 2 à 4 propulseurs latéraux visibles. */
    public int tanks;
    public boolean shaking;
}
