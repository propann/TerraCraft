# Mesures de génération — 2026-10-11 (0.38, échelle 1:1 à Paris)

Monde neuf, distance de vue 6, 60 s par lieu, serveur de test à **2 Go** sur le PC de développement (client
Minecraft ouvert en parallèle : chiffres bruités, à refaire sur Falix).

| Lieu | Chunks | Moyenne (ms/chunk) | Max (ms) | Échecs | Tick moyen (ms) |
|---|---:|---:|---:|---:|---:|
| Paris (centre-ville) | 441 | 22 à 64 | 110 à 388 | 0 | 1,4 à 4,6 |
| Créteil (banlieue) | 441 | 19,1 | 133 | 0 | 2,0 |
| Le Havre (port) | 441 | 18,5 | 238 | 0 | 1,8 |
| Beauce (campagne) | 441 | 21,6 | 152 | 0 | 2,5 |
| Chamonix (montagne) | 441 | 24,5 | 85 | 0 | 1,8 |
| Marseille (côte) | 441 | 8,6 | 49 | 0 | 0,7 |
| New York | 441 | 7,6 | 34 | 0 | 1,1 |
| Tokyo | 441 | 9,2 | 43 | 0 | 1,4 |
| 5 joueurs simultanés | ~1 050 | 21 à 72 | 0,2 à 4,7 s | 0 | 8,6 à 10,4 |
| 10 joueurs simultanés | ~1 100 | 16 à 241 | 0,1 à 13 s | 0 | 7,8 à 13,7 |

Lecture :

- **Le tick reste sain** (moins de 14 ms à 10 joueurs ; limite 50 ms) : la génération reste hors du fil principal.
- **Un chunk coûte plus cher qu'avant** (il contient 2,3 fois plus de ville réelle), mais une même surface de ville se
  génère plus vite : il faut moins de chunks.
- **Mémoire** : à 2 Go, 2 passages sur 3 à 10 joueurs (dix capitales à la fois, cache vide) ont manqué de mémoire.
  Falix a 8 Go : garder **au moins 4 Go** au serveur et vérifier `/spark health` à la relance.
- Cache de tuiles vectorielles décodées : 6 (relectures en boucle) → 32 (mémoire saturée) → **12** retenu.

---

# Mesures de génération — 2026-10-08 (0.37, ancienne échelle)

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
