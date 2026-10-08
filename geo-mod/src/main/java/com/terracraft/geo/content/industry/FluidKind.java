package com.terracraft.geo.content.industry;

/** Liquides de l'industrie du carburant, mesurés en millibuckets (1 000 mB = un seau, un bidon, une dose). */
public enum FluidKind {
    CRUDE("Pétrole brut"),
    GASOLINE("Essence"),
    KEROSENE("Kérosène");

    public final String label;

    FluidKind(String label) {
        this.label = label;
    }
}
