# TerraCraft — Plan de route : génération des bâtiments

Date : 12 octobre 2026  
Branche de travail : `work/buildings-roadmap` (créée depuis `main`)  
Branche de déploiement : `falix` (générée depuis `main`; aucune modification directe)  
Branche cible après validation : `map` (à créer seulement quand les essais locaux sont concluants)

## But

Les bâtiments se génèrent déjà, mais leur rendu n’est pas assez reconnaissable. On améliore d’abord les empreintes, le découpage des polygones fusionnés, la silhouette/hauteur, la classification et l’implantation sur la rue et le terrain. Le but est une Terre crédible à l’œil dans Minecraft, sans achat de données ni service payant.

## Contraintes qui ne bougent pas

- Falix dispose de 8 Go de RAM au total : aucune ingestion géospatiale lourde ni pré-calcul de ville complète sur le serveur.
- Les téléchargements, comparaisons, rendus et préparations se font sur le poste de travail; le serveur ne reçoit que des fichiers légers et validés si un cache préparé est retenu.
- Le monde actuel peut être supprimé. Les essais de génération se font sur un monde local jetable et neuf.
- Le parcours d’apparition reste identique : choix de position, séquence d’arrivée, commandes, état initial du joueur et point de réapparition. Il est rejoué en test de non-régression.
- `main` reste la base de développement; `falix` reste la distribution serveur générée automatiquement. Aucun push vers `falix` pendant la phase de recherche.
- Le serveur tourne aujourd’hui; les changements doivent rester isolés jusqu’à ce qu’ils apportent un gain visuel vérifié.

## Branches et portes de passage

```text
main
  └── work/buildings-roadmap   étude, outils hors ligne, corrections candidates et essais locaux
          └── map              branche dédiée au nouveau paquet/format de carte, après validation locale
                  └── retour vers main, puis falix seulement après décision et vérification
```

La branche `map` ne sera créée qu’après les portes locales P0–P4 ci-dessous. Elle portera la version candidate de génération/carte, pas les artefacts de test temporaires. `falix` n’est pas une branche d’essai.

## Plan de travail

### P0 — Établir une référence visuelle reproductible

- Choisir des fenêtres GPS compactes : Paris Marais (îlots/cours), La Défense (tours), Créteil (résidentiel), Le Havre (port/industrie), Chamonix (pente) et une petite zone rurale.
- Fixer les coordonnées, bbox, monde/seed, version du JAR, preset, position/caméra, heure Minecraft, distance de rendu et commandes.
- Produire vues aériennes et vues au niveau de la rue de la génération actuelle; sauvegarder aussi les chunks de référence.
- Annoter les défauts par catégorie : îlot fusionné, polygone déformé, trou/cour, bâtiment en double, hauteur/type incohérent, bâtiment flottant/enfoui, mauvaise orientation ou accès rue.
- Mesurer baseline : chunks générés, ms/chunk p50/p95/p99/max, MSPT, heap/RSS/pic mémoire, taille du monde, erreurs et temps de sauvegarde.

**Sortie P0 :** atlas de référence et fiche de repro pour chaque défaut. Pas de changement de génération avant d’avoir identifié les cas concrets.

### P1 — Comprendre les données réellement reçues

- Télécharger hors serveur les tuiles vectorielles OpenFreeMap z14 exactes sur les mêmes fenêtres et archiver date, URL/style, couches, hashes et attribution.
- Auditer les champs vraiment présents : hauteur/niveaux, type, couleur, toit, identifiant, source; mesurer leur taux de présence par quartier.
- Extraire les mêmes fenêtres depuis OSM brut, Overture Buildings et Microsoft Global ML Building Footprints, en figeant release et licence.
- Comparer empreintes, regroupements/fusions/scissions, doublons, cours, annexes, densité et attributs; générer des cartes superposées par source.
- Garder les attributs observés séparés des valeurs converties ou devinées.

**Sortie P1 :** rapport de couverture des champs et atlas OSM/Overture/Microsoft. Identifier quelle source aide quel profil, sans conclure à une source unique parfaite.

### P2 — Diagnostiquer l’algorithme de bâtiment

- Rejouer la rastérisation sur un petit échantillon isolé, hors Falix.
- Vérifier explicitement le cas relevé dans `OsmCells` : présence d’au moins un bâtiment Overture semble désactiver toute la couche `building` OpenFreeMap pour la cellule de 512 blocs. Mesurer les bâtiments perdus hors bbox Overture et les doublons aux bords.
- Vérifier la séparation des composantes, les anneaux intérieurs/cours, l’orientation, les intersections et la simplification.
- Contrôler calcul de surface, largeur moyenne, hauteur, étages, toit et garde-fous; vérifier qu’une valeur inférée n’est pas affichée comme mesurée.
- Comparer une génération sans Overture, avec Overture, et avec une fusion locale candidate limitée au même bbox.

**Sortie P2 :** liste priorisée des causes, avec cas minimal reproductible et impact visuel/mémoire/temps.

### P3 — Prototyper des améliorations ciblées

Prototyper sur branche `work/buildings-roadmap`, en petits changements réversibles :

1. Corriger d’abord les pertes/doublons de géométrie et le traitement des polygones avec trous.
2. Séparer îlot urbain et bâtiment individuel à partir de géométrie, largeur, mitoyenneté et alignement rue; ne pas traiter tout grand polygone comme un seul immeuble.
3. Donner une forme lisible à l’échelle Minecraft : fronts bâtis alignés, cours ouvertes quand elles existent, volumes distincts, hauteur par niveau avec bornes par classe.
4. Améliorer seulement les classes soutenues par les données (maison, immeuble, commerce, industrie, équipement, tour); utiliser un niveau de confiance et une catégorie « inconnue » au lieu d’inventer.
5. Placer les fondations sur un niveau local robuste et raccorder aux rues; les tests Copernicus/IGN/Terrarium ne concernent que les cas de pente et de bâtiment mal posé.
6. Garder routes/eau comme couches co-présentes : un pont peut conserver la rivière en dessous; ces règles servent au raccordement du bâti, pas à une refonte générale.

**Sortie P3 :** JAR candidat et atlas avant/après sur exactement les fenêtres P0. Ne pas préparer toute une ville.

### P4 — Cache léger et préparation hors serveur

- Déterminer si la meilleure qualité vient des tuiles actuelles ou d’un cache prétraité. Ne choisir un cache que s’il apporte un gain net mesuré.
- Si nécessaire, définir un paquet géographique par bbox : géométries simplifiées sans perte de topologie, indexées par cellule, compactées et chargées à la demande.
- Inclure un manifeste : schéma/dataset/version, bbox, projection, source/release, licence/attribution, transformations, volumes, nombre de formes et SHA-256.
- Garder l’ingestion multi-sources, les opérations lourdes et les atlas sur le poste de travail; Falix ne fait que lire le cache borné. Mesurer la consommation serveur totale (Java + mods + chunks + cache) avec 8 Go disponibles.
- Éviter le pré-généré massif : tester seulement le voisinage utile du point d’apparition et quelques zones de validation.

**Sortie P4 :** cache pilote mesuré en taille, temps de lecture et pic RAM, avec procédure de retour à la version précédente.

### P5 — Validation serveur local

Sur un serveur local configuré comme Falix (même version Minecraft/Fabric/Java/mods, même mémoire maximale réaliste) :

- Monde neuf jetable; vérifier le scénario d’apparition identique avant/après.
- Générer les mêmes points dans des ordres opposés et avec cache froid/chaud.
- Vérifier frontière chunk 16 blocs, cellule 512 blocs, tuile MVT z14 et tuile de relief; regarder routes, rivières, façades, cours et fondations.
- Faire 1/5/10 téléportations ou joueurs simulés, génération dense, attente d’une heure, arrêt propre, redémarrage et sauvegarde/restauration.
- Comparer hashes/blocs des zones déjà produites dans chaque run; le résultat ne doit pas dépendre de l’ordre ou de l’état mémoire.
- Mesurer p95/p99 des temps de chunk, MSPT, heap/RSS et GC. Pas de téléchargement ou de calcul lourd sur le fil principal; pas de cellule vide silencieuse; pas de croissance mémoire persistante.
- Vérifier que la persistance des joueurs, villes, inventaires et systèmes reste intacte dans les scénarios concernés, même si le monde de test est neuf.

**Porte P5 :** rendu visiblement meilleur sur au moins 4 profils, défauts majeurs corrigés, apparition inchangée, aucune régression fonctionnelle, mémoire stable sous plafond local équivalent à Falix.

### P6 — Créer la branche `map`

Après validation P5 uniquement :

- Créer `map` depuis le commit testé de `work/buildings-roadmap`.
- Y isoler la version candidate des données/règles de génération de carte et son manifeste.
- Rejouer build, tests d’assets, test de scénario, atlas et benchmark depuis un checkout propre de `map`.
- Revoir le diff par rapport à `main`; conserver les outils/rapports nécessaires pour reproduire les résultats.

**Porte P6 :** branche `map` reproductible et validée en local. Elle n’est toujours pas la branche serveur.

### P7 — Préparer le déploiement, sans le déclencher automatiquement

- Comparer l’artefact de `map` au processus `deploy/make_falix_branch.sh` et au contenu attendu par `falix`.
- Faire une sauvegarde Falix et vérifier la procédure de monde neuf. Le monde peut être supprimé, mais les données joueurs et la procédure serveur doivent être traitées séparément et clairement.
- Déployer uniquement après décision explicite, sur un monde neuf; garder le JAR précédent et le chemin de retour.
- Vérifier les points d’apparition et observer les quartiers avec des joueurs; consigner version et résultat.

**Sortie P7 :** déploiement contrôlé et décision documentée. Tant que P5 n’est pas franchie, aucun changement sur `falix`.

## Critères de réussite du chantier bâtiments

- Les quartiers d’essai sont reconnaissables par leur densité, leurs îlots, leurs tours/maisons/entrepôts et leur rapport aux rues.
- Les cas visuels de référence sont comparés avec mêmes angles et mêmes coordonnées.
- Les polygones fusionnés, cours, trous, duplications et bords produisent un résultat stable.
- Les gains n’imposent ni service payant, ni ingestion sur Falix, ni pré-calcul mondial.
- Le pic mémoire et les p95/p99 sont mesurés sur la configuration locale équivalente à Falix.
- Le parcours d’apparition et le fonctionnement général restent identiques.

## Repères techniques du dépôt

- Code bâtiment et rasterisation : `geo-mod/src/main/java/com/terracraft/geo/world/OsmCells.java`, `GeoChunkGenerator.java`, `BuildingStyles.java`.
- Banc existant : `tools/geo_bench.sh` (il utilise aujourd’hui `-Xmx2G`, huit lieux, et des joueurs simulés); adapter les paramètres de test localement sans modifier la référence de production pendant la phase d’audit.
- Mesures existantes : `docs/mesures-generation.md` (notamment banc 11 octobre à refaire dans des conditions fixes).
- Configuration Falix et limites : `deploy/FALIX.md`.
- Préparation/déploiement Falix : `deploy/make_falix_branch.sh`; branche `falix` générée, ne pas éditer directement.

## Sources ouvertes à vérifier au moment de chaque extraction

OpenMapTiles/OpenFreeMap, OSM, Overture Buildings, Microsoft Global ML Building Footprints, IGN RGE ALTI et Copernicus DEM. Les données sont à télécharger par petites emprises, avec version, attribution et licence conservées. Pour le sujet immédiat, commencer par les polygones et attributs bâtiment; le relief n’est qu’un test ciblé de fondations en pente.
