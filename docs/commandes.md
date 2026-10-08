# Commandes et touches

Référence de TerraCraft (version 0.37). Tout est en français ; `/aide` en donne l'essentiel en jeu.

## Touches

| Touche | Rôle |
|---|---|
| `O` | Menu TerraCraft : carte, hôtel des ventes, argent, fiche, missions, combinaison, coffre du véhicule, ville, guide |
| `J` | Combinaison spatiale : casque, combinaison, bottes, jetpack, deux réserves d'oxygène |
| `E` | Inventaire, avec le panneau de la combinaison spatiale à droite (survie et créatif) |
| `V` | Coffre du véhicule où l'on est assis (ou du sien à moins de 6 blocs) ; soute de la fusée |
| `K` | Fiche de personnage : paliers, compétences, découvertes, compteurs |
| `M` / `'` | Carte Xaero ; menu des claims (Open Parties and Claims) |
| Saut maintenu en l'air | Jetpack (s'il est porté et a du carburant) |
| Avion | `Z`/`S` gaz, `Q`/`D` tourner, `Espace` monter, `Ctrl` descendre, `Maj` sortir |
| Rover lunaire | Clic droit avec la caisse : déballer ; conduite comme une voiture ; `Maj` + clic gauche : remballer |
| Fusée | `Espace` décoller ; à pied, `Maj` + clic droit main vide : **carte des étoiles** (aussi menu `O`) ; clic droit avec un réservoir : réservoir en plus, avec un kit : charge utile |

## Industrie du carburant

| Bloc ou objet | Usage |
|---|---|
| Détecteur de pétrole | Clic droit : distance et direction du gisement le plus proche (les flaques de pétrole le signalent aussi) |
| Pompe à pétrole | Sur un gisement, alimentée : extrait le brut (5 mB/s par point de richesse et par panneau, 2 panneaux au plus) |
| Panneau solaire, câble électrique | Un panneau = une unité d'énergie le jour, s'il voit le ciel ; les câbles le relient aux machines |
| Tuyau | Relie les machines ; les liquides vont vers les réservoirs (et le brut vers les raffineries) |
| Raffinerie | 2 panneaux : 200 mB de brut → 140 mB d'essence + 60 mB de kérosène par seconde |
| Réservoir | 16 000 mB d'un seul liquide |
| Pompe à essence | Bidon vide en main : clic droit = essence, Maj + clic droit = carburant de fusée (1 000 mB) |
| Batterie de stockage | Se charge le jour avec les panneaux reliés (20 min d'un panneau) ; alimente les machines la nuit |
| Groupe électrogène | Relié par tuyau (essence) et câbles : brûle 5 mB par unité d'énergie et par seconde quand panneaux et batteries manquent |
| Serre hydroponique | Alimentée par câbles : une récolte (blé, pommes de terre, carottes, betteraves) toutes les ~45 unités d'énergie, dans un coffre ou tonneau collé |
| Lampe électrique | Reliée par câbles : allumée quand le réseau a de l'énergie (la nuit : 1 unité de batterie ou d'essence toutes les 2 s) |
| Station-service en ruine | Aux vraies adresses : les pompes puisent dans la cuve enterrée (2 000 à 8 000 mB d'essence) |
| Bidon vide | 2 fer + 1 seau ; rendu après chaque plein ; à défaut, bidon artisanal : bidon vide + 4 charbons |

Clic droit main vide sur une machine : écran à jauges (contenu, énergie, charge, gisement), rafraîchi chaque seconde.

## Joueurs

| Commande | Rôle |
|---|---|
| `/aide [thème]` (ou `/guide`) | Aide par thèmes : survie, commerce, villes, combat, espace, touches |
| `/regles` | Règles du serveur |
| `/course` | Course à l'espace : étapes, primes et vainqueurs |
| `/tuto` | Objectif « Premiers pas » en cours ; `/tuto passer` pour masquer le parcours |
| `/sethome`, `/home` | Maison (refusé en combat) |
| `/back` | Retour au lieu de la dernière mort (refusé en combat) |
| `/tpa <joueur>`, `/tpaccept`, `/tpdeny` | Téléportation entre joueurs (une demande toutes les 10 s) |
| `/argent` | Solde (1 000 crédits au départ) |
| `/hdv` | Hôtel des ventes (aussi menu `O`) : `vendre <prix>` (objet en main, frais 2 %), `acheter <n°>`, `retirer <n°>`, `page <n>` |
| `/etal`, `/etal prix <n>`, `/etal ajouter`, `/etal acheter [n]` | Étal le plus proche (4 blocs) : offre ; fixer le prix et garnir (propriétaire) ; acheter. Clic droit : stock (propriétaire) ou offre ; Maj + clic droit : acheter 1 |
| `/comptoir [n]` | Comptoir du serveur : carburant, oxygène, munitions, vivres à prix fixe ; offres réservées (★) selon le métier et la réputation |
| `/missions` | Contrats du jour et missions ; `reclamer <id>` |
| `/metier` | Métiers (mécanicien, éclaireur, récupérateur, combattant, pilote) ; `choisir <métier>` (un changement par 24 h) |
| `/ville` | Sa ville : `creer <nom>` (500 crédits), `inviter`, `rejoindre`, `quitter`, `exclure`, `maire`, `adjoint`, `centre`, `tp`, `deposer`, `payer`, `liste` (adjoints : inviter, exclure, payer) ; `stock [deposer|retirer]` devant un terminal logistique |
| `/station` | Ses stations spatiales ; `nom <nom>` pour renommer la plus proche |
| `/plans` | Plans de fusée débloqués, matériaux, comment débloquer les autres |
| `/fusee carte` | Carte des étoiles de la fusée où l'on est assis (ou à moins de 8 blocs) |
| `/fusee cap <destination>` | Mettre le cap : `terre`, `orbite`, `orbite_lunaire`, `lune`, `orbite_mars`, `mars`, `asteroides` |
| `/atelier installer <plan>` | Installer une amélioration sur la fusée garée près d'un atelier de station |
| `/terracraft ou` | Latitude, longitude et altitude réelles de sa position |
| `/lieux` | Lieux réels les plus proches (hôpital, pharmacie, commissariat, gare, supermarché, école…) : nom, distance, direction |
| `/terracraft vehicule partager|retirer <joueur>`, `liberer` | Partage et propriété de son véhicule |
| `/pvp` | Règle de combat : PvE partout, liste des zones PvP |
| `/prime <joueur> <montant>`, `/primes` | Mettre une tête à prix (minimum 50, argent bloqué, gagné par qui l'abat en zone PvP) ; primes en cours |
| `/confirmer`, `/annuler` | Valider ou abandonner une action coûteuse en attente (boutons cliquables dans le chat, 30 s) |
| `/signaler <message>` | Signaler un bug ou une perte d'objet aux administrateurs (un par minute) |

## Modérateurs

| Commande | Rôle |
|---|---|
| `/mod expulser <joueur> <raison>` | Expulser un joueur |
| `/mod silence <joueur> <minutes>` | Réduire au silence dans le chat (0 = lever) |
| `/mod avertir <joueur> <message>` | Avertissement officiel |
| `/mod aller <joueur>` | Se rendre auprès d'un joueur |
| `/mod signalements` | Dix derniers `/signaler` |

## Administrateurs (opérateurs)

| Commande | Rôle |
|---|---|
| `/eco donner <joueur> <montant>` | Créditer un joueur (journalisé `[ECO]`) |
| `/moderateurs [ajouter|retirer <joueur>]` | Nommer ou retirer des modérateurs (sans les rendre opérateurs) |
| `/terracraft course demarrer|arreter` | Ouvrir ou fermer la course à l'espace (événement d'ouverture) |
| `/terracraft aller <lat> <lon>` | Se rendre à des coordonnées réelles |
| `/terracraft station-service [aller]` | Station-service la plus proche (et s'y rendre) |
| `/terracraft asteroide` | Se rendre sur l'astéroïde le plus proche (ceinture d'astéroïdes) |
| `/terracraft petrole [aller]` | Gisement de pétrole le plus proche (et s'y rendre) |
| `/terracraft generation [reset]` | Mesures de génération : chunks, temps par chunk, téléchargements retentés ou ratés |
| `/terracraft suivi` | Joueurs uniques, actifs 24 h / 7 j, retour, temps de jeu, tick moyen |
| `/eco stats` | Crédits créés, détruits, en circulation et dans les trésoreries des villes |
| `/terracraft sauvegarde [liste]` | Sauvegarde immédiate du monde ; liste des archives (`backups/`) |
| `/terracraft largage` | Ravitaillement militaire près de soi |
| `/pvp creer <rayon> <nom>`, `/pvp supprimer <nom>` | Déclarer ou retirer une zone PvP centrée sur soi |
| `/terracraft boss` | Invoquer un Commandant du bunker devant soi |
| `/terracraft vague` | Vague nocturne immédiate sur sa ville (s'arrête au bout de 10 min) |
| `/terracraft convoi` | Convoi militaire en panne près de soi |
| `/terracraft contamination [aller]` | Zone contaminée la plus proche (et s'y rendre) |
| `/terracraft fusee decoller` | Lancer la fusée où l'on est assis (mêmes vérifications que la touche Espace) |
| `/terracraft depart` | Rouvrir la carte de départ pour soi |
| `/terracraft meteores` | Pluie de micrométéorites sur la Lune dans 5 s (sinon toutes les 25 à 45 min) |
| `/terracraft sanctuaire` | Sur la Lune : se rendre au pied du puits du sanctuaire le plus proche |
| `/terracraft vehicule voiture|camion|moto` | Véhicule complet et plein devant soi |
| spark : `/spark tps`, `/spark health`, `/spark profiler` | Performances du serveur |

## Journal de la console

Préfixes à surveiller : `[HDV]`, `[ECO]`, `[MISSION]`, `[CONTRAT]`, `[VILLE]`, `[METIER]`, `[VEHICULE]`, `[LARGAGE]`, `[CONVOI]`, `[CONTAMINATION]`, `[VAGUE]`, `[PVP]`, `[BOSS]`, `[PRIME]`, `[ETAL]`, `[MOD]`, `[SUIVI]`, `[COURSE]`, `[GEN]`, `[PROTECTION]`, `[CARBURANT]`, `[LIEUX]`, `[LOGISTIQUE]`, `[ESPACE]`,
`[STATION]`, `[ATELIER]`, `[NAV]`, `[METEORES]`, `[PLAN]`, `[SAUVEGARDE]`, `[ANTITRICHE]`, `[SIGNALEMENT]`.

Fichiers de données du monde (`<monde>/terracraft_geo/`, écriture atomique avec copie `.bak`) : `balances.json`,
`hotel-des-ventes.json`, `economie.json`, `missions.json`, `contrats.json`, `progression.json`, `homes.json`,
`start_points.json`, `tutorial.json`, `villes.json`, `stations.json`, `signalements.json`, `contamination.json`, `pvp-zones.json`, `primes.json`, `moderateurs.json`, `activite.json`, `course.json`, `logistique.json`.
