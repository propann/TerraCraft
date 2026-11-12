package com.terracraft.geo;

import java.util.*;

/** Un seul document atomique pour les preuves, noyaux et régions restaurées. */
final class ExodusState {
    int version = 1;
    Map<String, Journal> players = new LinkedHashMap<>();
    Map<String, Region> regions = new LinkedHashMap<>();
    static final class Journal {
        Map<String, Long> facts = new LinkedHashMap<>();
        Set<String> done = new LinkedHashSet<>();
        String pending = "";
        String core = "";
        String contribution = "";
        String guardian = "";
        boolean evaOut;
        int trialX, trialY, trialZ;
    }
    static final class Region {
        String name;
        Map<String,String> names = new LinkedHashMap<>();
        int x,z,radius = 64;
        boolean active;
        Set<String> contributors = new LinkedHashSet<>();
    }
    Journal journal(UUID id) { return players.computeIfAbsent(id.toString(), k -> new Journal()); }
    static boolean contains(Region r, int x, int z) {
        return r.active && Math.hypot((double)x-r.x,(double)z-r.z) <= r.radius;
    }
}
