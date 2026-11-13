# Étude profonde — MAP — protocole de vérification

## Matrice minimale de cas

| Cas | Données | Assertion |
|---|---|---|
| A | 1 Overture + 3 bâtiments OpenFreeMap disjoints dans une cellule | Les quatre bâtiments restent représentés. |
| B | Même bâtiment dans deux sources | Un seul bâtiment, provenance enregistrée. |
| C | Îlot Overture couvrant plusieurs bâtiments OpenFreeMap | Ne pas détruire arbitrairement les subdivisions ; décision documentée. |
| D | Polygone à anneau intérieur | Cour non bâtie et contour fermé. |
| E | Bâtiment traversant deux chunks | Façade, hauteur, toit et intérieur continus. |
| F | Bâtiment traversant deux cellules de 512 | Aucun doublon, trou ou changement de style. |
| G | Route sur pont et rivière en dessous | Deux niveaux préservés, raccords continus. |
| H | Terrain pentu avec bâtiment | Fondations plausibles, accès praticable. |
| I | Timeout/HTTP 500/429/cache corrompu | Erreur distincte du vide réel ; reprise possible. |
| J | Ordre de génération inversé et cache froid/chaud | Identité des blocs produits pour données et seed figées. |
| K | Monde neuf, arrivée joueur | Choix géographique, kit, spawn et respawn inchangés. |
| L | 10 joueurs simulés et sauvegarde | Pas d'OOM, mémoire stable, données persistantes. |

## Données de chaque essai

Enregistrer : commit Git, SHA-256 des entrées, licences, seed, coordonnées/bbox, preset, JVM, Xmx, mods, taille des caches, latence réseau, nombre de chunks, erreurs, métriques p50/p95/p99/max, MSPT, RSS/heap/GC, temps d'arrêt, captures à angle fixe et résultat des assertions.

## Métriques de qualité

- **Rappel bâtiment** : nombre de bâtiments de référence présents / nombre total de référence, sur annotation manuelle locale.
- **Précision bâtiment** : vrais bâtiments produits / tous bâtiments produits, sur échantillon annoté.
- **IoU d'emprise** : intersection/union des polygones, lorsque correspondance pertinente.
- **Erreurs de topologie** : trous bouchés, doublons, collisions, discontinuités.
- **Reconnaissance visuelle** : évaluation à l'aveugle de captures appariées.
- **Performance** : p50/p95/p99 et max, MSPT, mémoire, GC, débit et taille des sauvegardes.

Ne pas interpréter une moyenne seule comme preuve de stabilité. Les seuils quantitatifs définitifs seront fixés **après** la baseline M0, sans déplacer les critères pour faire passer une régression.

## Exécution et sécurité

Utiliser un monde jetable et une branche de travail. Ne pas modifier les mondes des joueurs. Sauvegarder avant tout essai d'intégration. Préparer les données lourdes hors Falix. Ne pas télécharger une ville entière sur le serveur. Pour toute source tierce, vérifier licences et obligations d'attribution avant distribution.

## État au 12 novembre 2026

- [x] Audit statique des composants principaux et des mesures historiques.
- [x] Cas de test et plan d'expérimentation définis.
- [ ] Jeux de fixtures créés.
- [ ] Tests exécutés.
- [ ] Atlas avant/après généré.
- [ ] Corrections implémentées.
- [ ] Benchmarks indépendants réalisés.
