# Avancement TerraCraft

Dernière passe : 7 octobre 2026 — version **0.12.0**.

## Fonctionnel et raccordé

- [x] Monde Terre réelle avec relief, routes, eau et bâtiments Overture/OpenFreeMap.
- [x] Génération des bâtiments limitée par type et hauteur.
- [x] Menu TerraCraft `O`, fiche joueur `K`, missions, `/aide` et carnet de survie avec les touches.
- [x] Parcours « Premiers pas » (6 étapes récompensées, encart à l'écran, `/tuto`).
- [x] Économie persistante, hôtel des ventes graphique : objets conservés à l'identique, sans duplication.
- [x] Hôtel des ventes : catégories, prix moyens, icônes, vente directe ; comptoir du serveur ; frais de 2 % ; `/eco stats`.
- [x] Voiture, camion et moto : assemblage, conduite, carburant, coffre, propriétaire et partage ; progression comptée une seule fois par véhicule.
- [x] Fusée, orbite terrestre, orbite lunaire, Lune, Mars et orbite de Mars ; carburant par trajet (8 doses), Mars après la Lune.
- [x] Stations : kit de module pressurisé 7×5×7, balise de station (arrivée des fusées), `/station`.
- [x] Plans de fusée (`/plans`) et atelier de station : réservoir étendu, moteur ionique, soute (touche V), navigation martienne.
- [x] Combinaison spatiale (touche `J` et panneau de l'inventaire `E`) : casque, combinaison, bottes magnétiques, jetpack, deux réserves d'oxygène, rendu en armure, HUD O₂.
- [x] Jetpack : poussée en maintenant saut, carburant au bidon, compatible anti-triche.
- [x] Avion : kit, carburant, pilotage au regard, décrochage, crash, instruments de bord.
- [x] Villes : fondation (500 crédits), invitations, maire, trésorerie, `/ville tp`, titres d'entrée et de sortie.
- [x] Coffre du véhicule : bouton du menu O et touche V.
- [x] Contrats du jour (3 par jour), nouvelles missions, écran des missions refait.
- [x] Métiers (`/metier`) avec bonus branchés sur véhicules, armes, jetpack, avion et attributs.
- [x] Territoires : propriétaire affiché en changeant de chunk (Open Parties and Claims).
- [x] Ravitaillements militaires toutes les 45 à 75 min (`/terracraft largage` pour les admins).
- [x] `/signaler` : rapports de bugs enregistrés, alerte aux opérateurs.
- [x] Survie : téléportations refusées en combat, `/tpa` limitée.
- [x] Anti-triche vol (allow-flight est forcé pour l'espace).
- [x] Données joueurs écrites de façon atomique avec copie `.bak`.
- [x] Sauvegardes automatiques du monde, rotation, restauration testée.
- [x] Versions des mods tiers figées (`tools/mods.lock.json`), identiques serveur et pack.
- [x] Test de démarrage d'un vrai serveur avant chaque déploiement Falix (CI).
- [x] Test de scénario avec faux joueurs (`tools/scenario_test.sh`, 27 vérifications) : villes, économie, combinaison, oxygène, métiers, largages, station, vol en fusée jusqu'à la balise, plans et atelier.

## À tester en jeu

- [ ] Inventaire E : panneau de la combinaison, shift-clic, clic sans jeter l'objet, mode créatif.
- [ ] Hôtel des ventes : catégories, vente depuis l'écran, comptoir, frais.
- [ ] Jetpack : poussée, carburant, recharge, flammes, pas de faux positif anti-triche.
- [ ] Avion : décollage, virages, décrochage, atterrissage, crash, passager.

- [ ] Combinaison spatiale : rendu sur le joueur (avec et sans armure), autres joueurs, migration depuis la 0.6.
- [ ] Moto : montage avec deux roues, conduite, démontage et reconnexion.
- [ ] Mars : premier voyage, arrivée, gravité, oxygène, retour et génération de chunks.
- [ ] Orbite de Mars : quai, oxygène, bottes magnétiques, station et retour.
- [ ] Anti-triche vol : aucun faux positif en jeu normal (sauts, chutes, véhicules, échelles).
- [ ] Sauvegardes automatiques sur Falix : durée, taille et espace disque.

## Prochaine séquence

1. Retours de jeu sur la 0.8.0 (inventaire, économie, jetpack, avion), puis publication.
2. Événements (convoi, contamination), primes, magasins de joueurs.
3. Missions liées aux lieux réels et métiers (phase 4).

## Règle de publication

Une version est publiée seulement si le build passe, si le serveur démarre (test automatique), si le client et le
serveur utilisent le même JAR, si le `.mrpack` est régénéré et si la branche `falix` contient les mods serveur aux noms fixes.
