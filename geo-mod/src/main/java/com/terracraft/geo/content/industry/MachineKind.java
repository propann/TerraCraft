package com.terracraft.geo.content.industry;

/** Machines de l'industrie du carburant : alimentation électrique requise, contenance par liquide (mB). */
public enum MachineKind {
    OIL_PUMP("Pompe à pétrole", true, 4_000),
    REFINERY("Raffinerie", true, 4_000),
    FUEL_TANK("Réservoir", false, 16_000),
    FUEL_PUMP("Pompe à essence", false, 0);

    public final String label;
    public final boolean powered;
    public final int capacity;

    MachineKind(String label, boolean powered, int capacity) {
        this.label = label;
        this.powered = powered;
        this.capacity = capacity;
    }
}
