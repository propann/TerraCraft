# Étude profonde — MAP — audit technique

**Date :** 2026-11-12 · **Référence :** branche `main` inspectée via GitHub · **Nature :** revue statique ciblée, sans exécution du mod.

## 1. Objectif et contraintes

Rendre les quartiers réels reconnaissables et reproductibles, sans dépasser les ressources du serveur Falix (8 Go au total). Préserver le parcours d'apparition et ne pas modifier `falix`. Réutiliser le plan existant `docs/plan-route-batiments-generation-2.md` (branche `work/map`) plutôt que créer un chantier concurrent.

## 2. Constats vérifiés

| ID | Priorité | Constat / preuve | Conséquence |
|---|---|---|---|
| MAP-01 | P0 | `OsmCells.java`, `build()`, vers lignes 375–400 : dès qu'une cellule possède au moins un bâtiment Overture, toutes les features `building` OpenFreeMap de la cellule sont ignorées. | Perte possible de bâtiments non couverts par Overture ; priorité globale incorrecte. |
| MAP-02 | P0 | `OsmCells.java`, `build()`, exception interceptée puis `Cell.EMPTY` ; `VectorTiles.java`, `read()`, échec réseau après réessais retourne `null`. | Une panne de données peut produire des routes et bâtiments absents. Vérifier la propagation exacte de `null` avant correction. |
| MAP-03 | P1 | `OsmCells.java` : cellules de 512 blocs, marge, cache LRU de 24 cellules et futures `inFlight`. `cellAt()` attend via `join()`. | Risque de pics mémoire, de latence de génération et de chargements redondants à quantifier. |
| MAP-04 | P1 | `GeoChunkGenerator.java`, `fill()` et `fillColumn()` : relief et volumes bâtis appliqués colonne par colonne ; `fillBuilding()` à partir de la ligne ~409. | La cohérence architecturale doit être testée pour les grandes empreintes et frontières. |
| MAP-05 | P1 | `BuildingInterior.java` : cloisons sur grille de 7 blocs et mobilier par hash déterministe. | Intérieurs répétitifs malgré des variantes de mobilier par lieu. |
| MAP-06 | P1 | `BuildingStyles.java`, `guessType()` : classification heuristique par hauteur, emprise et localisation, et `normaliseType()`. | Les types inférés peuvent être inexacts ; distinguer observation et inférence. |
| MAP-07 | P1 | `EarthTerrain.java` : projection Web Mercator, relief et échelle dépendante de la latitude ; `surfaceY()` convertit altitude en hauteur de blocs. | Vérifier alignement routes/bâtiments/altitudes aux latitudes et reliefs extrêmes. |
| MAP-08 | P1 | `docs/mesures-generation.md`, campagne 2026-10-11 : Paris 22–64 ms/chunk, jusqu'à 13 s en pic dans le scénario dix joueurs, deux passages sur trois à 2 Go ont manqué de mémoire. | Les moyennes sont insuffisantes ; mesurer p95/p99, RSS, GC et sauvegardes avec budget Falix. |

### Ce qui n'est PAS démontré à ce stade

- Le nombre exact de bâtiments perdus par MAP-01.
- Le nombre de chunks effectivement endommagés par MAP-02.
- L'existence de doublons de géométrie aux frontières, de trous mal interprétés, ou de façades flottantes sur un échantillon représentatif.
- Le gain de performance ou de qualité d'une source géographique alternative.
- La réussite d'une compilation et de tests locaux sur cette branche documentaire.

## 3. Recherche technique : solutions à comparer

### A. Fusion multi-sources par bâtiment (MAP-01)

**Ne pas** simplement ajouter les deux couches : cela peut créer des doublons. Prévoir un modèle interne `FootprintCandidate` avec : source, version, identifiant stable, géométrie et trous, attributs observés, attributs inférés, emprise, score de qualité, provenance.

Algorithme candidat :
1. Collecter les empreintes des deux sources pour une bbox avec marge.
2. Normaliser coordonnées, orientation des anneaux, validité et précision.
3. Construire un index spatial local (grille ou R-tree selon dépendances).
4. Calculer les recouvrements (intersection / union), distinguer bâtiment identique, inclusion, bâtiments mitoyens et îlot composite.
5. Choisir la meilleure géométrie **par groupe de recouvrement**, non par cellule ; conserver les objets sans correspondance.
6. Dédupliquer sur les marges et trier les résultats par identifiant stable.
7. Rasteriser en conservant provenance et compteur de pertes/doublons.

Les seuils IoU et de distance doivent être calibrés sur des exemples annotés, pas choisis arbitrairement. Un grand polygone Overture peut représenter un îlot et plusieurs polygones OSM des bâtiments distincts : ne pas fusionner sans analyse topologique.

### B. Erreurs de données (MAP-02)

Introduire une issue explicite `READY | VERIFIED_EMPTY | TEMPORARILY_UNAVAILABLE | INVALID`. Un HTTP 204/404 n'est pas interchangeable avec une panne réseau. Éviter de persister un chunk comme géographiquement définitif si les données requises n'ont pas été chargées ; étudier une mise en attente/reprise bornée compatible avec le cycle de génération Minecraft. Journaliser et rendre observables les cellules incomplètes. Prévoir un mode hors ligne avec cache validé.

### C. Building Planner (MAP-04/05/06)

Proposer un plan immuable calculé à partir de `(worldSeed, buildingStableId, datasetVersion, geometry)`, avant la pose des blocs. Le plan décrit emprise, cour, fondations, accès, étages, façade, toiture et zones intérieures. Chaque chunk n'en matérialise que l'intersection ; aucun état aléatoire dépendant de l'ordre de génération. Garder le rendu actuel comme fallback. Ne pas introduire d'objets persistants massifs sans benchmark.

### D. Relief, routes et eau (MAP-07)

Créer une couche d'arbitrage entre relief, chaussée, pont, cours d'eau et fondation. Tester des profils de pente : vallée, versant, front de mer et franchissement de rivière. Respecter les ouvrages superposés (pont au-dessus d'une rivière). Les sources d'altitude IGN/Copernicus sont **candidates à comparer**, non des améliorations prouvées.

### E. Performance (MAP-03/08)

Mesurer cache froid/chaud, un et dix joueurs, déplacements simultanés, temps de fermeture et sauvegarde. Ne pas traiter un temps de génération asynchrone comme un MSPT. Évaluer les coûts des allocations de grille, du chargement Overture et de la conversion MVT. Fixer un budget de RAM incluant JVM, mods et processus du serveur ; prévoir marge OS.

## 4. Données externes à évaluer

| Source | Usage envisagé | Vérification préalable |
|---|---|---|
| OpenStreetMap | Géométrie et tags détaillés | couverture locale, fraîcheur, ODbL et attribution |
| OpenFreeMap / OpenMapTiles | Tuiles vectorielles actuellement consommées | schéma des couches, champs réellement disponibles, conditions d'usage |
| Overture Maps Buildings | Empreintes complémentaires | release figée, confiance, licence et provenance |
| Microsoft Global ML Building Footprints | Comparaison d'empreintes | pays couverts, licence et biais du modèle |
| IGN / Copernicus DEM | Relief de référence | résolution, licence, géoréférencement, coût de préparation |

**Ne pas présumer** que les champs `height`, `building:levels`, `roof:shape` ou `building` existent dans toutes les tuiles. Mesurer leur couverture réelle avant de les exploiter.

## 5. Questions ouvertes et décisions

- Quel est le taux de rappel des bâtiments par quartier pour chaque source ?
- Quel pourcentage d'empreintes représente un îlot composite ?
- Les anneaux intérieurs sont-ils correctement décodés puis rasterisés ?
- Comment empêcher une panne réseau de figer une zone vide ?
- Quelle qualité visuelle gagne-t-on par milliseconde et par Mo supplémentaire ?
- Quelle stratégie permet une transition sur **monde neuf**, sans casser l'apparition ?

**Décision actuelle :** priorité à MAP-01 et MAP-02, après baseline et cas de reproduction. Aucun changement de source mondiale avant preuve mesurée.

## 6. Références internes

- `geo-mod/src/main/java/com/terracraft/geo/world/{OsmCells,GeoChunkGenerator,BuildingInterior,BuildingStyles,EarthTerrain,VectorTiles}.java`
- `docs/mesures-generation.md`
- `docs/plan-route-batiments-generation-2.md` (branche `work/map`)
- `tools/geo_bench.sh`
