# Avancement TerraCraft

Dernière passe : 7 octobre 2026 — version **0.7.0** (serveur Falix, release GitHub et pack client alignés).

## Fonctionnel et raccordé

- [x] Monde Terre réelle avec relief, routes, eau et bâtiments Overture/OpenFreeMap.
- [x] Génération des bâtiments limitée par type et hauteur.
- [x] Menu TerraCraft `O`, fiche joueur `K`, missions, `/aide` et carnet de survie avec les touches.
- [x] Parcours « Premiers pas » (6 étapes récompensées, encart à l'écran, `/tuto`).
- [x] Économie persistante, hôtel des ventes graphique : objets conservés à l'identique, sans duplication.
- [x] Voiture, camion et moto : assemblage, conduite, carburant, coffre, propriétaire et partage ; progression comptée une seule fois par véhicule.
- [x] Fusée, orbite terrestre, Lune, Mars et orbite de Mars.
- [x] Combinaison spatiale (touche `J`) : casque, combinaison, bottes magnétiques, deux réserves d'oxygène, rendu en armure, HUD O₂.
- [x] Survie : téléportations refusées en combat, `/tpa` limitée.
- [x] Anti-triche vol (allow-flight est forcé pour l'espace).
- [x] Données joueurs écrites de façon atomique avec copie `.bak`.
- [x] Sauvegardes automatiques du monde, rotation, restauration testée.
- [x] Versions des mods tiers figées (`tools/mods.lock.json`), identiques serveur et pack.
- [x] Test de démarrage d'un vrai serveur avant chaque déploiement Falix (CI).

## À tester en jeu

- [ ] Combinaison spatiale : rendu sur le joueur (avec et sans armure), autres joueurs, migration depuis la 0.6.
- [ ] Moto : montage avec deux roues, conduite, démontage et reconnexion.
- [ ] Mars : premier voyage, arrivée, gravité, oxygène, retour et génération de chunks.
- [ ] Orbite de Mars : quai, oxygène, bottes magnétiques, station et retour.
- [ ] Anti-triche vol : aucun faux positif en jeu normal (sauts, chutes, véhicules, échelles).
- [ ] Sauvegardes automatiques sur Falix : durée, taille et espace disque.

## Prochaine séquence

1. Retours de jeu sur la combinaison, Mars et la moto.
2. Phase 3 de la feuille de route : catégories et prix dans l'hôtel des ventes, dépenses utiles, journal économique.
3. Jetpack (module dorsal de la combinaison) avec énergie et limites, compatible anti-triche.
4. Avion terrestre avec carburant et pistes simples.

## Règle de publication

Une version est publiée seulement si le build passe, si le serveur démarre (test automatique), si le client et le
serveur utilisent le même JAR, si le `.mrpack` est régénéré et si la branche `falix` contient les mods serveur aux noms fixes.
