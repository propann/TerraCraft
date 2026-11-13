# Audit de la génération de la carte (Terre réelle)

Date : 13 novembre 2026 · Base examinée : `d76c415` (`main`, après la 0.39) · Périmètre : la dimension Terre
(`geo-mod/src/main/java/com/terracraft/geo/world/`) et ce qui l'appelle (`StartPoints`, `GeoMod`), les outils
`tools/fetch_overture_buildings.py`, `tools/make_koppen.py` et les documents `docs/mesures-generation.md` et
`docs/fiabilisation.md`. La Lune, Mars et les astéroïdes sont hors périmètre.

Méthode : lecture complète du code de génération (≈ 4 800 lignes), puis mesures reproductibles hors Minecraft
(annexe A) : profil vertical du relief, altitude réelle de trois zones sous le niveau de la mer dans les tuiles
utilisées par le mod, disponibilité des URL OpenFreeMap. **Je n'ai ni compilé ni lancé de serveur** : les constats
« à vérifier en jeu » sont signalés comme tels. Toutes les pistes proposées utilisent des données et des outils
**gratuits**.

---

## 1. En bref

La chaîne est solide et bien pensée : projection unique Web Mercator, génération hors du fil principal, caches
disque et mémoire avec dé-duplication des téléchargements, rastérisation propre des données OSM en grilles d'un
octet, garde-fous sur les hauteurs aberrantes, rapport `/terracraft rapport`, banc de mesure `geo_bench.sh`.
Les mesures d'octobre montrent un tick sain jusqu'à 10 joueurs.

Les cinq points qui comptent le plus :

| # | Constat | Gravité | Preuve |
|---|---|---|---|
| G1 | **Les montagnes sont aplaties au-dessus d'environ 1 300 m** : 100 m réels ne font plus que 6 blocs à 1 500 m et 2 blocs au mont Blanc. Les Alpes, les Pyrénées et l'Himalaya deviennent des plateaux. | Élevée | Mesuré (annexe A) |
| G2 | **Les terres sous le niveau de la mer sont noyées** : polders des Pays-Bas, Death Valley, rives de la mer Morte deviennent de la mer. | Élevée | Mesuré (annexe A) |
| G3 | **Une panne réseau dégrade la carte pour toujours, sans le dire** : relief absent → terrain plat à 1 m ; tuile OSM absente → ville sans routes ni bâtiments. Les chunks sont écrits sur disque tels quels. | Élevée | Code |
| G4 | **Le niveau d'eau des rivières dépend de la cellule de 512 blocs** : marches possibles à chaque frontière de cellule et eau qui peut déborder. | Moyenne | Code, à vérifier en jeu |
| G5 | **Le cache Overture remplace tous les bâtiments d'une cellule dès qu'il en contient un** : au bord de la zone Overture, des bâtiments OSM disparaissent. | Moyenne | Code |

Le meilleur levier d'amélioration n'est pas dans le code mais dans les **données** : produire soi-même ses tuiles
vectorielles (Planetiler + extrait Geofabrik) pour garder les balises que le schéma OpenMapTiles jette (étages, forme
et matière des toits, largeur des routes, `layer` des ponts), et, en France, utiliser le relief IGN RGE ALTI et la
BD TOPO, gratuits depuis 2021 (§ 5).

---

## 2. Comment la carte est générée aujourd'hui

```text
Clic sur la carte du monde (lat, lon)
  → WebMercator : 1 seule carte continue, échelle 0,6583 (1 bloc = 1 m à Paris)
  → StartPoints : préchargement relief + OSM autour du point, recherche d'un sol sec

Pour chaque chunk (fil "terracraftTerrain", hors fil serveur)
  ├─ EarthTerrain.elevation()  relief Terrarium (AWS), zoom 12 ≈ 25 blocs par pixel, interpolation bicubique
  │     └─ surfaceY()          linéaire jusqu'à 1 200 blocs au-dessus de la mer, puis racine carrée
  ├─ OsmCells.cellAt()         cellule de 512 blocs (+16 de marge) rastérisée depuis les tuiles OpenFreeMap z14
  │     ├─ surface : routes, rail, pistes, eau        ├─ land : forêt, herbe, sable, glacier…
  │     ├─ building : id du bâtiment, inset (toits)    └─ water / deck : niveau d'eau, tabliers de pont
  ├─ GeoChunkGenerator.fill()  colonne par colonne : sol, eau, route, bâtiment (étages de 4 blocs), pont, lampadaire
  └─ Wasteland.carve()         (mode apocalypse) grottes, bunkers

Décoration (applyBiomeDecoration)
  → stations-service réelles, flaques de pétrole, arbres (parcs, avenues), ruines, coffres, épaves
Biomes (GeoBiomeSource)
  → climat Köppen 1991-2020 (0,1°) + altitude + OSM (forêt, parc, eau)
```

---

## 3. Points forts à garder

- **Une seule projection** pour le monde, le relief et les tuiles : aucune reprojection, un lieu réel = une position
  en blocs (`WebMercator.java`). Le monde tient dans la bordure de Minecraft (±13,2 millions de blocs).
- **Génération asynchrone** : `buildTerrain` tourne sur l'exécuteur d'arrière-plan (`GeoChunkGenerator.java:198`) ; les
  mesures d'octobre confirment un tick < 14 ms à 10 joueurs.
- **Téléchargements dé-dupliqués** (`inFlight`) et écriture atomique des caches (`.part` puis déplacement).
- **Rastérisation une fois par cellule** puis lecture en O(1) par bloc : le générateur ne fait plus de géométrie.
- **Garde-fous de hauteur** (`OsmCells.buildingHeight`, plafonds par type et élancement maximal) et liste des
  bâtiments suspects consultable en jeu.
- **Identifiants stables** pour les bâtiments sans id OSM (`anonymousId`) : style et ruine identiques des deux côtés
  d'une frontière de cellule.
- **Climat réel** (Köppen, Beck et al. 2023, CC BY 4.0) embarqué dans le mod : aucune dépendance réseau.
- **Attributions** affichées aux joueurs (`StartPoints.java:194`), conformes à l'ODbL.

---

## 4. Constats détaillés

### G1 · Montagnes aplaties au-dessus de ~1 300 m — élevée

**Où** : `EarthTerrain.java:153-173` (`surfaceY`, `compress`) et `dimension_type/earth.json` (`height: 1536`, Y max 1471).

**Ce qui se passe** : l'altitude est convertie en blocs à l'échelle horizontale, linéaire jusqu'à 1 200 blocs, puis en
racine carrée pour que l'Everest tienne sous Y 1 440. La racine écrase tout ce qui dépasse : la pente vaut
`1/√(1 + h − 1200)`.

| Lieu | Altitude | Y | 100 m réels de plus = |
|---|---:|---:|---:|
| Montmartre | 130 m | 193 | 100 blocs |
| Chamonix | 1 035 m | 1 041 | 95 blocs |
| — | 1 500 m | 1 290 | **6 blocs** |
| Aiguille du Midi | 3 842 m | 1 359 | **2 blocs** |
| Mont Blanc | 4 808 m | 1 376 | **2 blocs** |
| Everest | 8 848 m | 1 407 | **1 bloc** |

Entre l'Aiguille du Midi et le sommet du mont Blanc, il y a 966 m réels… et 17 blocs. Toute la haute montagne est un
plateau légèrement bosselé, alors que le reste du monde est à l'échelle 1:1.

**Pistes (gratuites, au choix)** :

1. **Monter le plafond de la dimension** au maximum autorisé par Minecraft (`height: 4064`, Y max 3 999) et remplacer
   la racine par une pente constante réduite au-dessus de 1 200 blocs. Avec un facteur 0,5, l'Everest arrive vers
   Y 3 960 et le mont Blanc vers Y 2 940 ; **chaque 100 m réels gardent 50 blocs**. Coût à mesurer : sections vides peu
   chères en mémoire, mais éclairage du ciel, hauteur des nuages (`cloud_height` à 640) et distance de vue verticale
   du client à vérifier.
2. **Garder 1 536 et assumer une exagération verticale réduite partout** (par exemple 0,25 bloc par mètre au-dessus
   de 600 m) : moins réaliste en ville de montagne, mais proportions conservées entre les sommets.
3. Dans tous les cas : **une courbe continue et strictement croissante** avec une pente plancher (au moins
   0,2 bloc/m), testée par un test unitaire (voir Q1).

**Attention** : changer `surfaceY` décale le relief des chunks déjà générés. À faire au prochain monde neuf, ou en
limitant la nouvelle courbe aux altitudes > 1 200 m réelles (aucune ville joueur n'y est probablement).

### G2 · Terres sous le niveau de la mer noyées — élevée

**Où** : `GeoChunkGenerator.java:266` (`land = surfaceY >= SEA_LEVEL`), `EarthTerrain.java:188` (altitude ≤ 0 = océan),
`StartPoints.java:201` (on ne peut pas y apparaître).

**Mesuré dans les tuiles Terrarium utilisées par le mod (zoom 12)** :

| Lieu | Altitude dans la tuile | Résultat dans TerraCraft |
|---|---:|---|
| Zuidplaspolder (Pays-Bas) | −7,7 m | mer (Y 53) |
| Death Valley (États-Unis) | −83 m | mer (Y 30) |
| Rive de la mer Morte | −412 m | mer (Y 9) |

Une partie des Pays-Bas (Rotterdam, Amsterdam), la Camargue basse, les Moëres, la dépression Caspienne deviennent des
baies. La mer est déduite de l'altitude, pas d'une vraie limite terre/mer.

**Piste** : décider « mer ou terre » avec un **masque de côte**, et non avec le signe de l'altitude :

- la couche `water` des tuiles déjà téléchargées contient les océans (`class=ocean`) ; ou
- les **water polygons OSM** (osmdata.openstreetmap.de, ODbL, gratuits, mis à jour quotidiennement).

Hors océan, une altitude négative devient une terre posée au niveau de la mer (Y 63, éventuellement 1 à 2 blocs plus
bas avec une digue sur le contour des polders), et les lacs réels (mer Morte, mer Caspienne) gardent leur niveau
propre via la couche `water`.

### G3 · Pannes réseau : dégradation définitive et silencieuse — élevée

**Où** :
- relief : `ElevationTiles.java:170-172` renvoie une tuile vide → `EarthTerrain.java:122-123` met **1 m partout** ;
- OSM : `VectorTiles.java:132-134` renvoie `null` → liste vide **gardée en mémoire** (`VectorTiles.java:82-84`) ;
- cellule : `OsmCells.java:403-405` renvoie `Cell.EMPTY` (aucune route, aucun bâtiment) sur toute exception.

Le chunk produit est ensuite **enregistré dans le monde** : même quand le réseau revient, la zone reste plate ou vide.
Le seul signal est un compteur `echecs=` dans `/terracraft generation` et une ligne de journal. Le point est déjà
listé au lot 4 de `docs/fiabilisation.md` (« ne pas figer silencieusement des zones dégradées ») : voici comment le
traiter.

**Pistes** :

1. **Ne pas générer un chunk sur une donnée manquante** : `buildTerrain` renvoie déjà un `CompletableFuture` ; on
   peut composer sur le chargement de la cellule et du relief (`load(...).thenApplyAsync(fill)`) et **réessayer avec
   un délai croissant** plutôt que produire un chunk faux. Le joueur voit un chunk qui tarde, pas une ville rasée.
2. **Ne pas mettre l'échec en cache** (ou seulement 1 à 2 minutes) dans `VectorTiles.memory` et `OsmCells.memory`.
3. **Tenir un registre des chunks dégradés** (fichier JSON dans le monde) avec une commande d'administration pour les
   régénérer à froid.
4. **Supprimer la dépendance réseau** pour les zones jouées (§ 5.2 : tuiles locales en PMTiles).

### G4 · Niveau d'eau des rivières par cellule — moyenne (à vérifier en jeu)

**Où** : `OsmCells.java:1248-1266` (`waterLevel`) et `GeoChunkGenerator.java:274`.

Le niveau d'une surface d'eau est le **point le plus bas parmi les sommets du polygone situés à moins de 160 blocs de
la cellule**, moins 1. Le commentaire dit « identique d'une cellule voisine à l'autre », mais deux cellules voisines
ne voient pas les mêmes sommets : un long polygone (la Seine) peut recevoir un niveau différent de chaque côté de la
frontière. Ensuite, chaque colonne est encore limitée à `surfaceY − 1`, donc le niveau suit le relief bicubique
colonne par colonne.

Conséquences possibles : marches d'eau à chaque frontière de cellule, blocs d'eau source plus hauts que leurs voisins
qui **s'écoulent sur les berges** dès qu'un bloc voisin est mis à jour.

**À vérifier en jeu** : suivre la Seine dans Paris et le Rhône à Lyon, coordonnées multiples de 512, et regarder les
berges après un passage de joueur.

**Pistes** : niveau calculé sur une **grille fixe du monde** (par exemple des carrés de 64 blocs), indépendante de la
cellule ; profil **monotone** le long des cours d'eau (l'eau ne remonte jamais vers l'aval) avec des chutes d'un bloc
assumées ; berges forcées à au moins niveau + 1 sur une rangée.

### G5 · Overture : tout ou rien par cellule — moyenne

**Où** : `OsmCells.java:376-384`.

Si la cellule contient **un seul** bâtiment du cache Overture, **tous** les bâtiments OpenFreeMap de la cellule sont
ignorés. Le cache Overture est téléchargé pour une petite zone (exemple de `tools/fetch_overture_buildings.py` :
1,5 km de large) : les cellules de 512 blocs à cheval sur le bord perdent les bâtiments situés hors de la zone.

`docs/etat-du-projet.md` (audit du 11 octobre) donne ce bug pour corrigé : il l'a été en partie (le remplacement
était global, il est maintenant limité à la cellule), mais l'effet de bord reste au bord de la zone.

**Piste** : n'écarter un bâtiment OpenFreeMap que s'il **recouvre** un bâtiment Overture (test de boîte englobante,
puis du centre dans l'emprise), ou découper par le polygone de couverture Overture.

### Performance et mémoire

| # | Constat | Où | Piste |
|---|---|---|---|
| P1 | **Le relief est relu en boucle sous verrou.** Chaque `elevation()` lit 16 pixels (bicubique) ; chaque pixel prend le verrou d'un `LinkedHashMap` en ordre d'accès (qui se modifie à chaque lecture). Par chunk : 324 appels dans `fill`, 512 de plus dans la décoration (`surfaceY` et `zone` recalculent l'altitude), plus les biomes, plus `groundLevel` des bâtiments. Tous les fils de génération se disputent ce verrou. | `EarthTerrain.java:94-124`, `ElevationTiles.java:103-113`, `GeoChunkGenerator.java:678-689` | Grille d'altitude **par chunk** calculée une fois et réutilisée par la décoration et les biomes ; mémoire de « dernière tuile » par fil ; cache sans verrou global (Caffeine, Apache 2.0). Mesurer avec `geo_bench.sh` avant/après. |
| P2 | **Attentes bloquantes dans l'exécuteur partagé** : `join()` sur un téléchargement à l'intérieur des tâches de génération. Pendant une rafale de téléchargements (cache vide, 10 joueurs), les fils de l'exécuteur de Minecraft attendent le réseau. | `ElevationTiles.java:111`, `OsmCells.java:303` | Charger d'abord, générer ensuite (composition de futures, cf. G3.1). |
| P3 | **Calculs quadratiques à la rastérisation** : `poiIn` teste chaque bâtiment contre tous les lieux de la cellule ; dé-doublonnage des lieux en `stream().noneMatch` ; filtrage du cache Overture complet pour chaque cellule ; lecture du GeoJSON Overture en une seule chaîne. | `OsmCells.java:1018-1036`, `605`, `376`, `435` | Indexer les lieux après remplissage de la grille `building` (comme `placeKind` le fait déjà) ; grille spatiale pour Overture ; lecture en flux (`JsonReader`). |
| P4 | **Mémoire** : une cellule pèse ~3,5 Mo (12 octets × 544²), 24 en mémoire ≈ 85 Mo ; une tuile z14 du centre de Paris fait **1,1 Mo compressée** (mesuré) et des dizaines de Mo décodée, d'où le compromis à 12 tuiles noté dans `mesures-generation.md`. | `OsmCells.java:130-138`, `VectorTiles.java:36` | Ne décoder que les couches et attributs utiles ; coordonnées en `float` ; garder la tuile brute et décoder par cellule. |
| P5 | `getBaseHeight` alloue un tableau de 1 536 états de bloc à chaque appel et ignore rivières et bâtiments. | `GeoChunkGenerator.java:738-766` | Calcul direct de la hauteur ; tenir compte de l'eau OSM. |

### Qualité, fiabilité, cohérence

| # | Constat | Où | Piste |
|---|---|---|---|
| Q1 | **Aucun test sur la génération** (les 4 tests Java portent sur le jeu). `EarthTerrain` a pourtant été écrit sans dépendance à Minecraft « pour rester testable ». | `geo-mod/src/test/` | Tests JUnit sans réseau : `compress` croissante et continue, `surfaceY` aux altitudes de référence (tableau G1), aller-retour `WebMercator`, remplissage d'un polygone connu, **continuité du niveau d'eau entre deux cellules voisines** (G4), plafonds de hauteur. Quelques tuiles de test enregistrées dans le dépôt. |
| Q2 | **URL de secours figée** au rendu OpenFreeMap du 4 octobre. Elle répond encore le 13 novembre, mais le rendu courant est `20261101_080013_pt` et les anciens rendus finissent par disparaître. Le cache disque mélange aussi des tuiles de dates différentes. `USER_AGENT` annonce `TerraCraftGeo/0.2`. | `VectorTiles.java:30`, `GeoMod.java:53` | Mémoriser la dernière URL valide dans le cache disque ; noter la date du rendu avec chaque tuile ; version réelle dans l'en-tête. |
| Q3 | **Appels potentiellement bloquants sur le fil serveur** : la source de biomes appelle `cellAt()` (téléchargement possible) ; `StartPoints.isGoodSpot` et `elevation()` sont appelés au choix du point de départ. Normalement après préchargement, mais une recherche de biome (`/locate`) ou un préchargement incomplet bloquerait le serveur. | `GeoBiomeSource.java:99`, `StartPoints.java:201-209` | `cellIfLoaded()` côté fil serveur, comme `Places` et `GeoMod` le font déjà ; repli sur le climat seul si la cellule n'est pas prête. |
| Q4 | **Relief de surface, pas du sol** : les tuiles Terrarium mélangent SRTM (qui voit toits et arbres) et d'autres sources. Le code compense (médiane pour poser les bâtiments, tabliers de pont calés sur l'eau), mais les villes restent légèrement « gonflées ». | `OsmCells.java:772-776`, `1132-1157` | Relief « sol nu » (§ 5.1). |
| Q5 | **Résolution du relief** : ~25 blocs par pixel, lissés en bicubique. Falaises, carrières, talus de voies ferrées et quais disparaissent ; les routes suivent un sol lissé mais avec des marches d'un bloc. | `EarthTerrain.java:69-74` | Relief plus fin (§ 5.1) ; **nivellement des routes** le long de l'axe (profil lissé, dalles et escaliers aux changements de niveau). |
| Q6 | **Ponts et tunnels simplifiés** : tunnels et métro retirés (bien), mais les ponts au-dessus d'une route (échangeurs, viaducs) ne gèrent pas `layer` ; seul le passage au-dessus de l'eau a une logique de hauteur. | `OsmCells.java:571`, `777-809` | Avec des tuiles maison (§ 5.2), récupérer `layer` et la vraie géométrie des ouvrages. |
| Q7 | **Étages de 4 blocs pour 3 m** : choix assumé (habitable en Minecraft), mais les immeubles sont 1,33 fois plus hauts que le réel alors que rues et relief sont à 1:1. | `OsmCells.java:75-78`, `959-962` | À garder ; le documenter côté joueurs (« les immeubles sont un peu plus hauts que le réel »). |
| Q8 | `docs/localisation-et-monde.md` décrit encore le projet initial (Overpass, SRTM USGS, « trois zones fictives »). | doc | Mettre à jour avec le schéma du § 2. |

---

## 5. Pistes d'amélioration, toutes gratuites

### 5.1 De meilleures données de relief

| Source | Couverture | Résolution | Licence | Intérêt |
|---|---|---|---|---|
| **IGN RGE ALTI** | France | 1 m (et 5 m) | Licence Ouverte Etalab 2.0, gratuit depuis 2021 | Sol nu (lidar) : quais, talus, falaises, rues à leur vraie hauteur. Le meilleur choix pour les villes françaises. |
| **Copernicus DEM GLO-30** | Monde | 30 m | Gratuit avec attribution | Meilleur que SRTM, mais c'est encore un modèle de surface. |
| **FABDEM** | Monde | 30 m | CC BY-NC-SA 4.0 (**non commercial**) | Copernicus débarrassé des bâtiments et des arbres. Utilisable tant que le serveur reste sans revenus. |
| **USGS 3DEP** | États-Unis | 1 à 10 m | Domaine public | Pour New York et les villes américaines. |

Mise en œuvre simple : précalculer des tuiles au **même format Terrarium** (outil libre `rio-rgbify` ou un petit
script GDAL) pour les régions jouées, et les servir depuis le cache local : `ElevationTiles` ne change presque pas
(une URL par source et par zone).

### 5.2 Ses propres tuiles vectorielles (le levier n° 1)

Aujourd'hui, les données passent par le schéma **OpenMapTiles** d'OpenFreeMap, pensé pour afficher une carte. Il jette
ce dont un générateur 3D a besoin : `building:levels`, `roof:shape`, `building:material`, `roof:colour`, largeur et
nombre de voies, `layer` des ponts, `natural=tree`… Le code le constate lui-même (« Les tuiles ne donnent presque
jamais le type », `OsmCells.java:940`).

- **Planetiler** (Apache 2.0) produit des tuiles à partir d'un **extrait Geofabrik** (gratuit, quotidien) avec un
  **profil maison** qui garde ces balises. La France entière se génère en quelques dizaines de minutes sur un PC.
- Format **PMTiles** (un seul fichier, outil `pmtiles` sous licence BSD) : lecture locale par plages d'octets, **plus
  aucune dépendance réseau** en jeu, ce qui règle aussi G3.
- Bonus : le profil peut produire directement une couche « océan » pour G2 et des niveaux de rivière précalculés
  pour G4.

### 5.3 Bâtiments plus fidèles

- **BD TOPO de l'IGN** (France, Etalab 2.0, gratuite) : **hauteur et nombre d'étages pour chaque bâtiment**,
  matériaux des murs et des toits. C'est exactement ce qui manque aujourd'hui et oblige à deviner (`guessType`,
  plafonds de prudence).
- **Overture Maps** (bâtiments sous ODbL) : déjà prévu ; à généraliser une fois G5 corrigé, avec une lecture en flux.
- Données ouvertes des villes : par exemple les **arbres de Paris** (opendata.paris.fr, ODbL) pour remplacer les
  alignements devinés tous les 8 blocs.

### 5.4 Occupation du sol hors des villes

- **ESA WorldCover 10 m** (CC BY 4.0) : forêts, cultures, prairies, zones humides partout dans le monde, là où OSM est
  pauvre (campagne, Afrique, Asie). Utilisable en grille légère, comme la carte Köppen déjà embarquée.

### 5.5 Technique

- **Pré-génération** des villes de départ avec **Chunky** (mod libre) en heures creuses : moins de génération à chaud
  quand dix joueurs arrivent.
- **Nivellement des routes** (Q5) et **rivières monotones** (G4) : les deux défauts les plus visibles une fois les
  montagnes corrigées.
- **Rendu de contrôle en CI** : dessiner une cellule de référence vue de dessus en PNG (code `EarthTerrain`/`OsmCells`
  sans Minecraft) et la comparer à l'image attendue dans GitHub Actions. Les régressions visuelles deviennent
  visibles dans la PR.
- Projet de référence à étudier : **Build The Earth** (mod Terra++, échelle 1:1, open source), qui a affronté les
  mêmes questions de hauteur et de sources de données.

---

## 6. Plan d'action proposé

| Ordre | Chantier | Effort | Effet | Validation |
|---|---|---|---|---|
| 1 | Tests unitaires de `EarthTerrain`, `WebMercator`, rastériseur (Q1) | S | Filet de sécurité pour tout le reste | `gradle test` en CI |
| 2 | Ne plus figer les zones dégradées : pas de cache d'échec, nouvel essai, registre (G3) | M | Plus de villes rasées par une coupure | Couper le réseau pendant le banc : aucun chunk vide |
| 3 | Masque terre/mer (G2) | M | Pays-Bas et dépressions jouables | Tests sur Rotterdam, Death Valley |
| 4 | Corriger le filtre Overture (G5) | S | Plus de trous au bord de la zone | Bord de la bbox de test |
| 5 | Grille d'altitude par chunk, cache sans verrou (P1, P2) | M | Génération plus rapide à plusieurs | `geo_bench.sh` avant/après |
| 6 | Nouvelle courbe verticale + hauteur de dimension (G1) | M | Vraies montagnes | Chamonix, Pyrénées ; mesure mémoire et FPS client |
| 7 | Niveaux d'eau sur grille fixe et monotones (G4) | M | Rivières propres | Seine et Rhône sur plusieurs cellules |
| 8 | Tuiles maison Planetiler + PMTiles (§ 5.2) | L | Données plus riches, plus de dépendance réseau | Comparaison de rapports `/terracraft rapport` |
| 9 | Relief IGN et BD TOPO pour la France (§ 5.1, 5.3) | L | Villes françaises au mètre près | Paris, Lyon, Marseille |

S = moins d'une journée · M = quelques jours · L = une à deux semaines.

---

## Annexe A — Mesures faites pour cet audit (13/11/2026)

Script Python autonome (reproduit les formules de `EarthTerrain` et `WebMercator`, lit les vraies tuiles) :

- Zoom de relief calculé : **12**, soit **25,1 blocs par pixel** à l'échelle du préréglage.
- Profil vertical : tableau du § G1.
- Altitudes lues dans les tuiles Terrarium z12 : Zuidplaspolder −7,7 m, Death Valley −83,0 m, rive de la mer Morte
  −412,0 m ; toutes converties en mer par `surfaceY`.
- OpenFreeMap : TileJSON `https://tiles.openfreemap.org/planet` → rendu courant `20261101_080013_pt` ; l'URL de secours
  `20261004_113936_pt` répond encore (tuile 14/8299/5636 du centre de Paris : 1 101 769 octets). Les requêtes portant
  l'identifiant par défaut de Python (`Python-urllib`) sont refusées (HTTP 403), celles avec un `User-Agent` explicite
  passent : le mod en envoie un, ne jamais le retirer.

Non fait : compilation, lancement d'un serveur, vérification en jeu de G4 et Q3, mesures de performance avant/après.

## Annexe B — Références

- Tuiles de relief Terrarium : https://registry.opendata.aws/terrain-tiles/
- OpenFreeMap : https://openfreemap.org/ · schéma OpenMapTiles : https://openmaptiles.org/schema/
- Water polygons OSM : https://osmdata.openstreetmap.de/data/water-polygons.html
- IGN RGE ALTI : https://geoservices.ign.fr/rgealti · BD TOPO : https://geoservices.ign.fr/bdtopo
- Copernicus DEM GLO-30 : portail Copernicus Data Space Ecosystem (collection « COP-DEM »), aussi miroir AWS Open Data
- FABDEM : jeu de données de l'université de Bristol (Hawker et al. 2022), licence CC BY-NC-SA 4.0
- USGS 3DEP : https://www.usgs.gov/3d-elevation-program
- ESA WorldCover : https://esa-worldcover.org/
- Planetiler : https://github.com/onthegomap/planetiler · PMTiles : https://github.com/protomaps/PMTiles
- Geofabrik (extraits OSM) : https://download.geofabrik.de/
- Overture Maps : https://docs.overturemaps.org/
- Chunky (pré-génération) : https://modrinth.com/mod/chunky
- Hauteur maximale d'une dimension (`height` ≤ 4064) : https://minecraft.wiki/w/Dimension_type
- Köppen-Geiger 1991-2020 (Beck et al. 2023) : https://www.gloh2o.org/koppen/
