# État du projet TerraCraft

Analyse au 7 octobre 2026, version **0.18.0**. Ce document dit ce que nous avons, à quel point c'est vérifié, ce qui
reste fragile et dans quel ordre continuer. La liste détaillée des tâches est dans [`feuille-de-route.md`](feuille-de-route.md).

## En bref

TerraCraft est un serveur survie sur la Terre réelle (relief, climats, routes, bâtiments), post-apocalyptique, avec
une économie, des villes, des véhicules et un volet spatial complet jusqu'à Mars. Le mod `terracraft-geo` compte
**112 fichiers Java (≈ 16 000 lignes)** et **223 ressources** générées ou écrites à la main. Le déploiement est
automatisé (GitHub Actions → branche `falix` → serveur Falix) et chaque version passe un test de démarrage puis un
test de scénario avec faux joueurs avant publication.

## Ce que nous avons

Légende du niveau de vérification :
**A** = vérifié automatiquement (test de scénario ou de démarrage) · **C** = compilé et chargé, à tester en jeu ·
**J** = testé en jeu par l'équipe.

| Domaine | Contenu | Niveau |
|---|---|---|
| Monde | Relief réel, climats de Köppen, routes, eau, bâtiments OSM/Overture aménagés, bunkers, caves | J |
| Entrée | Carte de départ en jeu, largage, kit, carnet de survie, parcours « Premiers pas », accueil des habitués, MOTD et icône | A (MOTD, icône) / J (carte) |
| Survie | Inventaire conservé, `/home` `/back` `/tpa` refusés en combat | C |
| Économie | Comptes, hôtel des ventes par catégories, prix moyens, comptoir, frais 2 %, `/eco stats` | A |
| Missions | Missions permanentes, contrats du jour, métiers, ravitaillements militaires | A |
| Villes | Maire, habitants, trésorerie, retour au centre, titres d'entrée | A |
| Claims | Open Parties and Claims, propriétaire affiché en changeant de zone | C |
| Véhicules | Voiture, camion, moto (assemblage, carburant, coffre, propriétaire), avion au clavier | C (avion) / J (voitures) |
| Combat | Armes à chargeur, sniper, grenades, anti-triche vol | C |
| Espace | Fusées à 1-4 réservoirs, orbites terrestre et lunaire, Lune, Mars ; Lune et Mars au départ d'une station | A (Terre → orbite → orbite lunaire → Lune) |
| Sous-sol lunaire | Cavernes géantes, sanctuaires (pyramide, gardiens, trésor), donjons, cristaux | A (sanctuaire, découverte) / C (cavernes à voir en jeu) |
| Équipement spatial | Combinaison (5 pièces + jetpack), oxygène automatique, panneau dans l'inventaire, rendu en armure | A (port, oxygène) / C (écrans, rendu) |
| Stations et bases | Station orbitale en kit, bases lunaire et martienne en kit, modules, sas, quai, `/station` | A |
| Rover lunaire | Électrique, recharge solaire, déposé par la fusée, remballable | A (dépôt) / C (conduite) |
| Plans et atelier | Quatre améliorations de fusée débloquées en explorant, atelier de station | A |
| Exploitation | Sauvegardes automatiques et restauration testée, données atomiques, journaux, `/signaler`, spark | A (sauvegarde, restauration) |

## Outillage et qualité

- **Intégration continue** : compilation, test de démarrage d'un vrai serveur (`tools/smoke_server.sh`), mise à jour
  de la branche `falix`, publication d'une release avec le pack client sur tag.
- **Test de scénario** (`tools/scenario_test.sh`) : deux faux joueurs (Carpet, test uniquement) jouent une partie
  courte ; **48 vérifications** (villes, économie au crédit près, combinaison et oxygène, métiers, contrats, largage,
  module de station, balise, vol en fusée posé à la balise, carburant consommé, plans et atelier, MOTD et icône).
- **Versions figées** des mods tiers (`tools/mods.lock.json`), identiques sur le serveur et dans le pack.
- **Données joueurs** écrites de façon atomique avec copie `.bak` ; **sauvegardes** du monde toutes les 6 h.
- **Liste des serveurs vérifiable** : `tools/ping_server.py` interroge le serveur comme le jeu.

## Points fragiles et risques

1. **L'interface client n'est pas testée automatiquement.** Écrans (missions, marché, atelier, combinaison), mixins
   d'inventaire, rendu de la combinaison, HUD : seules les signatures sont vérifiées. Il faut des séances de jeu.
2. **La génération à grande échelle n'est pas mesurée** (phase 0) : temps de génération des villes, erreurs de
   téléchargement, TPS et mémoire avec plusieurs joueurs. La machine de développement n'a que 7 Go de RAM.
3. **Espace disque Falix** : trois archives du monde s'ajoutent au monde lui-même (`keep` réglable).
4. **Équilibrage de l'économie** non mesuré : sources (missions, contrats, tutoriel) contre dépenses (comptoir, frais,
   villes). `/eco stats` donne les chiffres ; il faut les relever après une semaine de jeu.
5. **Anti-triche limité au vol** (`allow-flight` est forcé pour l'espace). Vitesse, X-ray et duplication par d'autres
   mods ne sont pas couverts. Pas de système de permissions fin (LuckPerms) : seulement les niveaux d'opérateur.
6. **Dette technique** : `GeoMod.java` centralise ≈ 70 enregistrements ; `OsmCells` (≈ 980 lignes) et
   `GeoChunkGenerator` (≈ 700 lignes) mériteraient un découpage. Les fichiers JSON sont écrits sur le thread serveur,
   ce qui suffit pour une bêta mais pas pour une centaine de joueurs.
7. **Réseau** : une nouvelle version qui ajoute objets, écrans ou paquets impose de mettre à jour le pack client ;
   les joueurs doivent le savoir (README, message d'accueil « nouveautés »).

## Priorités recommandées

1. **Séance de test à plusieurs** sur Falix : interface client, véhicules, avion, combinaison, villes ; relevés
   `/spark tps` et `/spark health`. Corriger ce qui en sort avant d'ajouter du contenu.
2. **Phase 0 de génération** : dix points réels (centre-ville, banlieue, montagne, côte) sur une carte neuve.
3. **Événements et danger** (phase 6) : convoi, contamination, zones PvP explicites, primes.
4. **Volet spatial** : épaves et dangers lunaires, stockage partagé de station, astéroïdes.
5. **Bêta privée** (phase 9) : whitelist, règles publiées, événement d'ouverture.
