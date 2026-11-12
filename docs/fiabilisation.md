# Fiabilisation de TerraCraft

Mise à jour : 12 novembre 2026. Base examinée : `6736c35`, version `0.39.0`.
Ce suivi complète la [feuille de route](feuille-de-route.md) et les anciennes
[mesures de génération](mesures-generation.md). Une modification proposée ne vaut pas validation en production.

## Lot 1 — Validation avant déploiement

Le workflow vérifie les PR vers `main`, les push sur `main` et les tags `v*`.
Il contrôle les scripts de test, les ressources et les recettes, compile, puis lance
`tools/scenario_test.sh`. Ce scénario démarre lui-même un serveur Fabric et exerce
les villes, échanges, industrie, vols et élections avec des joueurs simulés.
Un échec bloque les étapes suivantes : branche `falix`, pack et release.
Les journaux d'échec sont conservés sept jours comme artefact GitHub Actions.

Le déclenchement sur PR et l'annulation des anciennes vérifications reprennent la
[proposition #1](https://github.com/propann/TerraCraft/pull/1). Le
[wrapper Gradle proposé en #2](https://github.com/propann/TerraCraft/pull/2)
reste un chantier distinct. Le lot ne modifie pas les opérateurs du serveur.

Les serveurs de test utilisent `127.0.0.1:25599`, en mode hors ligne, dans un dossier
jetable. Ils refusent un JAR absent et un dossier non vide sans marqueur
`.terracraft-test-workdir`. Le marqueur est créé automatiquement dans un dossier vide ;
ne jamais l'ajouter à un serveur réel. Le scénario efface son monde de test après
un démarrage et un arrêt réussis. Un dossier de test ne doit pas être utilisé par
deux commandes simultanées.

Les scripts suivent leur propre processus Java. Ils attendent sa fin et vérifient
son code de sortie avant de lire les données ou d'annoncer un succès. Un dépassement
du délai d'arrêt échoue et nettoie le processus ; aucune recherche globale de JVM
n'est nécessaire. Le scénario peut durer plusieurs minutes ; la CI est limitée à 45 minutes.

## Reproduire les contrôles

Depuis la racine du dépôt :

```bash
bash -n tools/test-server-lib.sh tools/smoke_server.sh tools/scenario_test.sh
python3 -m unittest discover -s tools/tests -v
python3 tools/check_assets.py
python3 tools/check_recipes.py
git diff --check
```

Neuf tests Python utilisent un faux processus Java, sans réseau. Ils vérifient
le refus des dossiers existants, le JAR absent, le démarrage en échec, l'arrêt normal,
l'arrêt en erreur, l'arrêt bloqué, le nettoyage, la réutilisation du dossier et la
préservation du monde si le démarrage préalable échoue. Ils ne valident pas Minecraft.

Neuf autres tests vérifient le classement des exceptions du journal. Le scénario
tolère et affiche séparément les réponses Mojang HTTP 429 pour les quatre identités
simulées et les délais réseau Open-Meteo identifiés. Une erreur d'un autre joueur,
un autre statut HTTP, une erreur de données météo ou une exception du mod reste
bloquante. Les profils et la météo en direct ne font pas partie des assertions de
ce scénario ; les pannes externes restent visibles dans les journaux archivés.

Après compilation avec un JDK 25 et Gradle 9.7.1 :

```bash
VERSION="$(sed -n 's/^mod_version=//p' geo-mod/gradle.properties)"
TEST_WORK="$(mktemp -d /tmp/terracraft-scenario.XXXXXX)"
tools/scenario_test.sh "geo-mod/build/libs/terracraft-geo-$VERSION.jar" "$TEST_WORK"
```

Le script télécharge Fabric, les mods figés et Carpet pour ce test. Les options
`SMOKE_TIMEOUT` (600 s par défaut) et `STOP_TIMEOUT` (90 s) ajustent les délais.
Les fichiers `console.log` et `scenario.log` restent dans le dossier pour diagnostic.

## État de vérification du lot

- Contrôles locaux : syntaxe Bash, ShellCheck, dix-huit régressions Python, lecture YAML et espaces.
- Compilation et scénario Minecraft : consulter le résultat de la CI pour le commit
  concerné. Ils n'ont pas été exécutés localement : le réseau du terminal d'audit ne
  permet pas le téléchargement du dépôt et des dépendances.
- Interface et ressenti : à tester avec de vrais clients ; aucun test simulé ne les remplace.
- Production Falix : aucun déploiement réalisé lors de la préparation du lot.

Avant fusion, vérifier le résultat complet de la CI et ses journaux. Configurer
ensuite la protection de `main` pour exiger le contrôle `build` si cette règle
n'est pas déjà active. Le lot n'active pas lui-même cette protection.

## Lots suivants et critères de réussite

| Ordre | Chantier | Validation attendue |
|---|---|---|
| 2 | Transactions économiques durables | Achat, vente et retrait interrompus à chaque étape : reprise sans duplication ni perte ; échec d'écriture remonté à l'opération. Inclure l'inventaire Minecraft dans la stratégie de reprise. |
| 3 | Sauvegarde cohérente | Capturer monde et données du mod au même état logique ; refuser une archive incomplète ; restaurer dans un serveur isolé et comparer soldes, annonces, villes et inventaires. |
| 4 | Charge et géographie | Mesurer à 1, 5 et 10 joueurs avec le budget RAM réel : mémoire maximale, GC, ticks et latences de génération ; tester une panne de tuiles sans figer silencieusement des zones dégradées. |
| 5 | Première heure de jeu | Séance multijoueur : arrivée, abri, vente, véhicule, reconnexion ; consigner blocages, durée et défauts visuels avant de modifier l'équilibrage. |

Les écritures atomiques de `JsonStore` protègent un fichier individuel. Elles ne
constituent pas une transaction entre `balances.json`, `hotel-des-ventes.json`,
`economie.json` et les inventaires. Une migration SQLite éventuelle doit prévoir
import, sauvegarde et retour arrière ; le lot 1 ne change pas ces formats.

La sauvegarde actuelle compresse pendant le jeu : les écritures JSON du mod peuvent
continuer malgré la suspension de sauvegarde Minecraft. Un essai de restauration
simple ne prouve donc pas sa cohérence sous activité commerciale.

## Retour arrière et exploitation

Le lot ne change ni le format du monde ni les données des joueurs. Avant fusion,
la branche de travail peut être abandonnée. Après fusion, un commit de réversion
des changements CI et scripts rétablit le comportement précédent ; sa fusion sur
`main` déclenche le pipeline normal, à coordonner avec l'exploitation Falix.

Sources du constat : [workflow](../.github/workflows/build.yml),
[scénario](../tools/scenario_test.sh),
[économie](../geo-mod/src/main/java/com/terracraft/geo/AuctionHouse.java),
[persistance JSON](../geo-mod/src/main/java/com/terracraft/geo/JsonStore.java),
[sauvegardes](../geo-mod/src/main/java/com/terracraft/geo/Backups.java).
