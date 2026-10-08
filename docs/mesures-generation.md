# Mesures de génération — 2026-10-08

Commit `2df5927` · distance de vue 6 · 60 s par lieu · monde neuf, sans cache de tuiles.

| Lieu | Chunks | Moyenne (ms/chunk) | Max (ms) | Réessais | Échecs | Mémoire (Mo) | Tick moyen (ms) |
|---|---:|---:|---:|---:|---:|---:|---:|
| Paris (centre-ville) | 441 | 48.4 | 196.0 | 0 | 0 | 716 | 1.9 |
| Créteil (banlieue) | 441 | 8.5 | 72.9 | 0 | 0 | 612 | 1.1 |
| Le Havre (port, industrie) | 441 | 5.7 | 41.3 | 0 | 0 | 609 | 0.5 |
| Beauce (campagne) | 441 | 13.0 | 97.7 | 0 | 0 | 619 | 1.7 |
| Chamonix (montagne) | 441 | 39.9 | 137.7 | 0 | 0 | 1048 | 1.5 |
| Marseille (côte) | 441 | 9.2 | 75.1 | 0 | 0 | 718 | 0.9 |
| New York (Manhattan) | 441 | 8.6 | 52.9 | 0 | 0 | 1267 | 1.5 |
| Tokyo (Shinjuku) | 441 | 9.7 | 46.1 | 0 | 0 | 1293 | 1.6 |
| 5 joueurs simultanés | 1047 | 21.1 | 157.5 | 0 | 0 | 1342 | 8.7 |
| 10 joueurs simultanés | 1323 | 14.7 | 135.3 | 0 | 0 | 1353 | 11.1 |

Erreurs dans le journal du serveur : 22. Taille du monde après 5 puis 10 joueurs : 67 Mo puis 76 Mo.

## Lecture

- **Le tick reste bon** : 1 à 2 ms pour un joueur, 8,7 ms à 5 joueurs et 11,1 ms à 10 joueurs générant en même temps
  (limite : 50 ms). La génération tourne hors du fil principal.
- **Le coût vient des centres-villes et de la montagne** : Paris ~30 à 50 ms par chunk (bâtiments, intérieurs), Chamonix
  ~20 à 40 ms ; campagne, banlieue, ports et côtes 6 à 13 ms.
- **La mémoire est la vraie limite** : avec 2 Go, le serveur a manqué de mémoire **à l'arrêt** après 10 joueurs
  (sauvegarde de ~1 300 chunks chargés). Recommandations reprises dans `deploy/FALIX.md` :
  **3 Go minimum** jusqu'à 5 joueurs, **5 Go** pour 10 joueurs à distance de vue 6, davantage avec une vue plus grande.
- **Aucun téléchargement raté** sur ~4 900 chunks ; le monde pèse ~75 Mo après cette visite de 18 villes.

## Reste à mesurer (phase 0)

- Avec et sans cache Overture ; mémoire après une heure de jeu réel ; rapport des bâtiments suspects (phase 7).

Relancer : `tools/geo_bench.sh geo-mod/build/libs/terracraft-geo-<version>.jar /tmp/bench docs/mesures-generation.md`
(variables `WAIT` et `VIEW`).
