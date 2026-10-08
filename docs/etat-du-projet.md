# État du projet TerraCraft

Analyse au 8 octobre 2026, version **0.31.0**. Ce document dit ce que nous avons, à quel point c'est vérifié, ce qui
reste fragile et dans quel ordre continuer. Tâches : [`feuille-de-route.md`](feuille-de-route.md) · commandes :
[`commandes.md`](commandes.md) · mesures : [`mesures-generation.md`](mesures-generation.md).

## En bref

TerraCraft est un serveur survie sur la Terre réelle (relief, climats, routes, bâtiments), post-apocalyptique, avec
une économie entre joueurs, des villes, des véhicules, des événements dangereux et un volet spatial jusqu'à Mars. Le
mod `terracraft-geo` compte **137 fichiers Java (≈ 20 500 lignes)** et **301 ressources**. Le déploiement est
automatisé (GitHub Actions → branche `falix` → serveur Falix) ; chaque version passe un contrôle des ressources, un
test de démarrage puis un test de scénario de **75 vérifications** avant publication.

**Point de vigilance principal : depuis la 0.15, aucune version n'a été jouée en séance réelle.** Tout le côté
serveur est vérifié automatiquement ; les écrans, l'équilibrage et le ressenti ne le sont pas.

## Ce que nous avons

Légende : **A** = vérifié automatiquement (scénario, démarrage, mesure) · **C** = compilé et chargé, à voir en jeu ·
**J** = testé en jeu par l'équipe.

| Domaine | Contenu | Niveau |
|---|---|---|
| Monde | Relief réel, climats de Köppen, routes, eau, bâtiments OSM/Overture, bunkers, caves ; météo réelle, jour et nuit normaux | J (monde) / A (horloge) |
| Performance | Génération hors du fil principal : tick 2 / 9 / 11 ms à 1 / 5 / 10 joueurs ; 3 Go de RAM minimum, 5 Go pour 10 | A (banc de mesure) |
| Entrée | Pack dans le dépôt (serveur déjà dans la liste), installeurs, carte de départ, règles, carnet, « Premiers pas », accueil | A (MOTD, icône, règles) / J (carte) |
| Interface | Un seul style (menu `O`, fiche `K`, marché, missions, atelier, carte des étoiles), barres de vie, chat, préfixes de ville, aide par thèmes | C |
| Économie | Comptes, hôtel des ventes, comptoir, frais 2 %, étals, primes, confirmations, `/eco stats` (argent bloqué compris) | A (bilan au crédit près) |
| Villes | Maire, adjoints, trésorerie, vagues nocturnes récompensées, territoire étendu par les claims | A (rôles, vagues) / C (claims) |
| Protection | Claims Open Parties and Claims (tout protégé par défaut) ; stations et bases protégées autour de leur balise | A (bases) / C (claims) |
| Combat et danger | PvE partout, zones PvP, armes, convois, zones contaminées, boss de bunker, micrométéorites, nuit lunaire | A |
| Missions | Missions permanentes, contrats du jour, métiers, découvertes, course à l'espace | A |
| Véhicules | Voiture, camion, moto, avion au clavier, rover lunaire | A (rover) / C (avion) / J (voitures) |
| Espace | Fusées 1 à 4 réservoirs, station orbitale d'abord, Lune, Mars, carte des étoiles, plans et atelier | A |
| Lune | Bases en kit, cavernes, sanctuaires extraterrestres, donjons, cristaux | A (sanctuaire) / C (cavernes) |
| Exploitation | Sauvegardes et restauration testée, données atomiques, journaux, `/signaler`, modérateurs, `/terracraft suivi` | A |

## Outillage et qualité

- **Intégration continue** : contrôle des ressources (`tools/check_assets.py` : traductions, modèles, textures,
  états de bloc et butins de chaque objet et bloc), compilation, test de démarrage d'un vrai serveur
  (`tools/smoke_server.sh`), mise à jour de la branche `falix`, release avec le pack client sur tag.
- **Test de scénario** (`tools/scenario_test.sh`) : trois faux joueurs (Carpet, test uniquement) jouent une partie
  complète — villes et rôles, économie au crédit près, étal, primes, PvP, vagues, convoi, contamination, boss,
  combinaison et oxygène, trois vols jusqu'à la Lune, base, rover, sanctuaire, météorites, protection, course.
- **Banc de génération** (`tools/geo_bench.sh`) : 8 lieux réels puis 5 et 10 joueurs simultanés.
- **Versions figées** des mods tiers (`tools/mods.lock.json`) ; pack et dossier `mods/` resynchronisés à chaque
  version (`tools/sync_client_mods.py`).
- **Données** écrites de façon atomique avec copie `.bak` ; **sauvegardes** du monde toutes les 6 h.

## Points fragiles et risques

1. **Rien n'a été joué en vrai depuis la 0.15** : écrans, mixins d'inventaire, rendu de la combinaison, HUD, barres
   de vie, carte des étoiles, équilibrage des combats et des primes. C'est le premier risque du projet.
2. **Mémoire** : avec 2 Go, le serveur a manqué de mémoire en sauvegardant après 10 joueurs. Respecter les seuils de
   `deploy/FALIX.md` ; surveiller `/spark health` pendant les premières soirées.
3. **Claims de ville** et **accès des modérateurs non opérateurs** : non couverts par le test automatique (les faux
   joueurs ne tapent pas de commandes) ; à vérifier une fois en jeu.
4. **Équilibrage de l'économie** non mesuré en conditions réelles : relever `/eco stats` après une semaine.
5. **Anti-triche limité au vol** : vitesse, X-ray et duplication par d'autres mods ne sont pas couverts.
6. **Dette technique** : `GeoMod.java` centralise les enregistrements (≈ 650 lignes) ; `OsmCells` et
   `GeoChunkGenerator` mériteraient un découpage ; les fichiers JSON sont écrits sur le fil du serveur (suffisant
   pour une bêta, pas pour une centaine de joueurs).
7. **Espace disque Falix** : trois archives du monde s'ajoutent au monde lui-même.

## Priorités recommandées

1. **Séance de test à plusieurs** sur Falix (RAM réglée selon `deploy/FALIX.md`) : écrans, véhicules, combinaison,
   villes et claims, étals, événements ; relevés `/terracraft suivi` et `/spark health`. Corriger ce qui en sort.
2. **Bêta privée** : whitelist, canal de retours, `/terracraft course demarrer` le soir de l'ouverture.
3. **Phase 7** : classification des bâtiments, points d'intérêt, puis missions liées aux lieux réels.
4. **Espace** : stockage partagé de station, grande station de ville, astéroïdes.
