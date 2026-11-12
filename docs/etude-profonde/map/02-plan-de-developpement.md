# Étude profonde — MAP — plan de développement

**Principe :** une modification par hypothèse, une preuve avant/après, zéro push direct sur `falix`. Ce plan complète les portes P0–P7 du plan bâtiments existant.

## Sprint M0 — Baseline reproductible (bloquant)
- [ ] Fixer commit, version Minecraft/Fabric/Java, seed, échelle, dataset, caches et coordonnées.
- [ ] Sélectionner Paris-Marais, La Défense, Créteil, Le Havre, Chamonix, campagne ; ajouter une côte et un pont.
- [ ] Produire captures aériennes/rue et annotations des défauts.
- [ ] Capturer nombre de bâtiments, taux de champs, p50/p95/p99 chunk, MSPT, RSS/heap/GC, taille monde, erreurs.
- **Livrable :** `docs/etude-profonde/map/resultats/baseline.md` + captures versionnées ou artefacts CI.
- **Gate :** résultats rejouables sur monde neuf.

## Sprint M1 — Fusion géométrique (MAP-01)
- [ ] Créer fixtures minimales : Overture partiel, bâtiments contigus, îlot avec cour, trous, doublons et frontière 512.
- [ ] Écrire un test qui échoue avec la priorité globale actuelle.
- [ ] Introduire `FootprintCandidate` et provenance ; garder le rendu ancien derrière option expérimentale.
- [ ] Implémenter fusion locale et règles de recouvrement mesurées.
- [ ] Comparer avant/après : bâtiments conservés, faux doublons, cours, latence et mémoire.
- **Gate :** zéro perte des bâtiments non recouverts dans les fixtures, aucun doublon introduit, rendu déterministe.

## Sprint M2 — Fiabilité des données (MAP-02)
- [ ] Simuler HTTP 200/204/404/429/500, timeout, cache corrompu et interruption.
- [ ] Séparer vide vérifié, panne temporaire et invalidité.
- [ ] Ajouter métriques et diagnostics, stratégie de retry bornée et reprise.
- [ ] Vérifier que la génération ne persiste pas silencieusement une zone faussement vide.
- **Gate :** panne reproductible, signalée, récupérable ; aucun blocage du fil serveur.

## Sprint M3 — Bâtiments et intérieurs (MAP-04/05/06)
- [ ] Prototype `BuildingPlan` immuable et stable par ID.
- [ ] Tests de grands bâtiments sur plusieurs chunks, trous et orientation d'entrée.
- [ ] Gabarits par fonction : maison, immeuble, commerce, hôpital, école, industrie.
- [ ] Séparer les données réelles des valeurs déduites ; garder fallback inconnu.
- [ ] Comparer captures de même point de vue, coût CPU/RAM et navigabilité.
- **Gate :** meilleure reconnaissance sur au moins 4 profils sur 6, sans régression majeure.

## Sprint M4 — Relief, infrastructure et cache (MAP-07)
- [ ] Cas de pente, fondation, route, rivière, pont et côte.
- [ ] Tester l'intérêt d'un cache préparé hors serveur par bbox (manifeste version/licence/SHA-256).
- [ ] Mesurer cache froid/chaud, RAM et débit disque.
- **Gate :** absence de ruptures visibles aux frontières, gain mesuré si cache retenu.

## Sprint M5 — Validation / intégration
- [ ] Build propre, tests unitaires et tests Fabric.
- [ ] Génération des mêmes zones en ordre inverse, comparaison des blocs/hashes.
- [ ] Simulation 1/5/10 joueurs, attente 60 minutes, sauvegarde, redémarrage et restauration.
- [ ] Rejouer parcours d'apparition à l'identique.
- [ ] Comparer 8 Go disponibles au serveur, avec marge OS ; aucune OOM.
- [ ] Revue du diff et PR vers `main` ; `falix` seulement après validation explicite.
- **Gate :** preuves publiées, rollback prêt, pas de régression des sauvegardes.

## Ordre de PR proposé

1. `test(map): fixtures et atlas de référence`
2. `fix(map): fusion locale Overture/OpenFreeMap`
3. `fix(map): états de disponibilité géographique`
4. `feat(map): building planner expérimental`
5. `perf(map): cache et profilage`
6. `docs(map): rapport final et décision de release`

Chaque PR doit documenter : hypothèse, avant/après, tests, risques, consommation mémoire, migration et retour arrière.

## Définition de terminé

Une tâche n'est pas « terminée » parce que le JAR compile. Il faut des tests de régression, une comparaison visuelle reproductible, des mesures de charge et une validation de la sauvegarde. Une idée non implémentée reste explicitement marquée « proposée ».
