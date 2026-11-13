package com.terracraft.geo;

import java.util.*;

/** Catalogue indépendant du moteur de jeu ; les preuves sont produites par le serveur. */
final class ExodusQuests {
    static final String PREFIX = "exodus:";
    record Goal(String fact, long target, String label) { }
    record Quest(String id, int act, String title, String npc, List<String> requires, List<Goal> goals, long credits) { }
    private static Goal g(String fact, long n, String label) { return new Goal(fact, n, label); }
    private static Quest q(String id, int act, String title, String npc, String prerequisites, long credits, Goal... goals) {
        return new Quest(id, act, title, npc, prerequisites.isEmpty() ? List.of() : List.of(prerequisites.split(",")), List.of(goals), credits);
    }
    static final List<Quest> ALL = List.of(
        q("q1_1_ashes",1,"Premiers Pas dans les Cendres","helene_vance","",150,
            g("loot",1,"Fouiller un coffre"),g("weapon",1,"Porter une arme")),
        q("q1_3_town",1,"L’Acte Fondateur","helene_vance","q1_1_ashes",250,
            g("town_claim",1,"Habiter une ville et protéger un chunk")),
        q("q1_2_night",1,"Sous les Étoiles Hostiles","helene_vance","q1_3_town",300,
            g("rampart",1,"Tenir un raid jusqu’à l’aube"),g("kills",8,"Repousser huit monstres")),
        q("q1_4_trade",1,"Le Pouls du Commerce","helene_vance","q1_3_town",200,
            g("market",1,"Utiliser un étal"),g("contracts",1,"Terminer un contrat du jour")),
        q("q2_1_oil_hunt",2,"Chasseur d’Or Noir","boulon_kowalski","q1_2_night,q1_4_trade",250,
            g("oil",1,"Détecter un gisement sous ses pieds")),
        q("q2_2_iron_blood",2,"Le Sang de la Terre","boulon_kowalski","q2_1_oil_hunt",350,
            g("pumping",1,"Faire fonctionner une pompe attribuée")),
        q("q2_3_refining",2,"Raffinage Stratégique","boulon_kowalski","q2_2_iron_blood",500,
            g("gasoline",10000,"Produire 10 000 mB d’essence"),g("kerosene",1000,"Produire 1 000 mB de kérosène")),
        q("q2_4_logistics",2,"Maîtrise des Continents","boulon_kowalski","q2_3_refining",450,
            g("vehicles",1,"Assembler un véhicule"),g("supplies",1,"Ouvrir un ravitaillement")),
        q("q3_1_eva_prep",3,"Équipement Extravéhiculaire","aris_thorne","q2_4_logistics",300,
            g("eva",1,"Équiper casque, combinaison, bottes et oxygène")),
        q("q3_2_orbit",3,"Cap sur l’Orbite","aris_thorne","q3_1_eva_prep",600,
            g("launches",1,"Lancer une fusée"),g("orbit",1,"Atteindre l’orbite terrestre")),
        q("q3_3_station",3,"Avant-Poste Orbital","aris_thorne","q3_2_orbit",600,
            g("station",1,"Enregistrer une station orbitale avec atelier et oxygène")),
        q("q3_4_moon_step",3,"Un Pas sur la Lune","sujet_09","q3_3_station",600,
            g("moon",1,"Visiter la Lune"),g("moon_base",1,"Déployer une base lunaire"),g("moon_rover",1,"Déployer un rover lunaire")),
        q("q3_5_ghosts_he3",3,"Les Fantômes de 2089","sujet_09","q3_4_moon_step",800,
            g("alien_sanctuary",1,"Explorer un sanctuaire lunaire"),g("helium",16,"Extraire seize éclats d’hélium-3"),
            g("lunar_crystal",8,"Extraire huit cristaux lunaires"),g("titanium",5,"Extraire cinq minerais de titane"),g("archive_2089",1,"Lire l’archive lunaire")),
        q("q4_1_shipyard",4,"Chantier Naval Spatial","soren_kael","q3_5_ghosts_he3",900,
            g("built_flight",1,"Achever un vol en vaisseau construit")),
        q("q4_2_mars_colony",4,"La Colonie Rouge","soren_kael","q4_1_shipyard",1000,
            g("mars",1,"Visiter Mars"),g("mars_base",1,"Déployer une base martienne"),g("mars_harvest",1,"Obtenir une récolte de serre attribuée sur Mars")),
        q("q4_3_orion_glyphs",4,"Les Glyphes d’Orion","soren_kael","q4_2_mars_colony",1000,
            g("mars_archive",1,"Lire les quatre glyphes de l’archive martienne")),
        q("q5_1_zero_g",5,"Navigation en Zéro-G","archiviste_orion","q4_3_orion_glyphs",1000,
            g("eva_return",1,"Sortir équipé dans les astéroïdes puis revenir à une balise")),
        q("q5_2_sanctuary",5,"L’Épreuve du Sanctuaire","archiviste_orion","q5_1_zero_g",1500,
            g("guardian",1,"Vaincre son gardien dans le temple d’Orion")),
        q("q5_3_artifact",5,"L’Éveil Cosmique","archiviste_orion","q5_2_sanctuary",2000,
            g("restoration",1,"Contribuer un Noyau authentique à la restauration d’une ville")));
    static Quest find(String id) {
        String key = id.startsWith(PREFIX) ? id.substring(PREFIX.length()) : id;
        return ALL.stream().filter(q -> q.id().equals(key)).findFirst().orElse(null);
    }
    static boolean unlocked(Quest q, Set<String> done) { return q.requires().stream().allMatch(done::contains); }
    static boolean ready(Quest q, Set<String> done, Map<String, Long> facts) {
        return !done.contains(q.id()) && unlocked(q, done) && q.goals().stream().allMatch(g -> facts.getOrDefault(g.fact(),0L) >= g.target());
    }
    static int chapter(Set<String> done) {
        return ALL.stream().filter(q -> !done.contains(q.id())).mapToInt(Quest::act).min().orElse(5);
    }
    private ExodusQuests() { }
}
