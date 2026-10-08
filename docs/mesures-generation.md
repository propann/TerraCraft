# Mesures de génération — 2026-10-08

Commit `7338f25` · distance de vue 6 · 60 s par lieu · monde neuf, sans cache de tuiles.

| Lieu | Chunks | Moyenne (ms/chunk) | Max (ms) | Réessais | Échecs | Tick moyen (ms) |
|---|---:|---:|---:|---:|---:|---:|
| Paris (centre-ville) | 447 | 30.9 | 118.6 | 0 | 0 | 1.8 |
| Créteil (banlieue) | 441 | 7.7 | 25.4 | 0 | 0 | 1.1 |
| Le Havre (port, industrie) | 441 | 6.3 | 39.1 | 0 | 0 | 0.6 |
| Beauce (campagne) | 441 | 8.4 | 22.6 | 0 | 0 | 0.8 |
| Chamonix (montagne) | 441 | 21.7 | 40.9 | 0 | 0 | 1.3 |
| Marseille (côte) | 441 | 6.1 | 19.0 | 0 | 0 | 0.9 |
| New York (Manhattan) | 441 | 7.3 | 39.4 | 0 | 0 | 1.1 |
| Tokyo (Shinjuku) | 441 | 8.5 | 41.1 | 0 | 0 | 1.2 |

Erreurs dans le journal du serveur : 0.

## Lecture

- **Génération hors du fil principal** : même au centre de Paris, le tick moyen reste sous 2 ms (limite : 50 ms).
  Les chunks sont construits sur les fils de génération ; le serveur reste fluide pendant l'import d'une ville.
- **Centre-ville dense = cas le plus lourd** : Paris coûte ~4 fois plus par chunk que la banlieue (bâtiments OSM,
  intérieurs, rues). Pointe à ~120 ms par chunk. Chamonix vient ensuite (relief fort, roche et neige).
- **Aucun téléchargement raté** sur 8 lieux et ~3 500 chunks : OpenFreeMap et les tuiles de relief ont tenu.
- **Mesure sur la machine de développement** (7 Go de RAM, serveur à 2 Go) : Falix devrait faire au moins aussi bien.

## Reste à mesurer (phase 0)

- Avec et sans cache Overture ; plusieurs joueurs générant en même temps ; mémoire après une heure.
- Rapport des bâtiments suspects (géants, vides, dupliqués) : phase 7.

Relancer : `tools/geo_bench.sh geo-mod/build/libs/terracraft-geo-<version>.jar /tmp/bench docs/mesures-generation.md`
(variables `WAIT` et `VIEW`).
