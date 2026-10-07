# Avancement TerraCraft

Dernière passe : 7 octobre 2026.

## Fonctionnel et raccordé

- [x] Monde Terre réelle avec relief, routes, eau et bâtiments Overture/OpenFreeMap.
- [x] Génération des bâtiments limitée par type et hauteur.
- [x] Menu TerraCraft `O`, fiche joueur `K`, missions et guide.
- [x] Économie persistante, hôtel des ventes graphique, pagination, achat et retrait.
- [x] Voiture et camion : assemblage, conduite, carburant, coffre, propriétaire et partage.
- [x] Moto : châssis, deux roues, conduite, coffre, partage et rendu dédié.
- [x] Fusée, combinaison, oxygène, orbite terrestre et Lune.
- [x] Registre solaire commun et dimension Mars avec orbite dédiée.
- [x] Pack Falix et pack client générés depuis le même JAR TerraCraft.

## À tester en jeu

- [ ] Moto : montage avec deux roues, conduite, démontage et reconnexion.
- [ ] Mars : premier voyage, arrivée, gravité, oxygène, retour et génération de chunks.
- [ ] Orbite de Mars : quai, oxygène, station et retour.
- [ ] HDV : pagination et restitution d'une annonce avec inventaire plein.
- [ ] Pack client : menu, textures, minimap et connexion Falix avec la même version.

## Prochaine séquence

1. Corriger les problèmes observés sur Mars et la moto en jeu.
2. Ajouter le jetpack avec énergie et limites de déplacement.
3. Ajouter l'avion terrestre avec carburant et pistes simples.
4. Ajouter les textures finales des composants et les modèles d'objets.
5. Étendre le registre aux autres planètes seulement après validation Mars.

## Règle de publication

Une version est publiée seulement si le build passe, si le client et le serveur utilisent le même JAR, si le `.mrpack` est régénéré et si la branche `falix` contient les mods serveur aux noms fixes.
