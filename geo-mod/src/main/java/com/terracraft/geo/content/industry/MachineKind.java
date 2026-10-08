package com.terracraft.geo.content.industry;

/** Machines de l'industrie du carburant : alimentation électrique requise, contenance par liquide (mB). */
public enum MachineKind {
    OIL_PUMP("Pompe à pétrole", true, 4_000),
    REFINERY("Raffinerie", true, 4_000),
    FUEL_TANK("Réservoir", false, 16_000),
    FUEL_PUMP("Pompe à essence", false, 0),
    /** Batterie : stocke l'énergie des panneaux le jour, la rend aux machines la nuit (relié par câbles, pas par tuyaux). */
    BATTERY("Batterie", true, 0),
    /** Groupe électrogène : brûle de l'essence (5 mB par unité d'énergie et par seconde) quand panneaux et batteries manquent. */
    GENERATOR("Groupe électrogène", true, 4_000),
    /** Serre hydroponique : 1 récolte toutes les ~45 unités d'énergie, déposée dans un coffre ou tonneau collé. */
    GREENHOUSE("Serre hydroponique", true, 0);

    public final String label;
    public final boolean powered;
    public final int capacity;

    MachineKind(String label, boolean powered, int capacity) {
        this.label = label;
        this.powered = powered;
        this.capacity = capacity;
    }
}
