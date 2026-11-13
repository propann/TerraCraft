# Audit complet du serveur TerraCraft

Date : 13 novembre 2026 · Base examinée : `d76c415` (`main`, mod `0.41.0`, Minecraft 26.3, Fabric) · Complète
l'[audit de la génération de la carte](audit-generation-carte-2026-11-13.md), qui n'est pas répété ici.

Périmètre : tout le reste du serveur, c'est-à-dire la sécurité des échanges client → serveur, la triche, l'économie
et la persistance des données, les sauvegardes, la performance du fil serveur, les PNJ à IA, les services externes,
l'intégration continue et le déploiement Falix, la structure du code et les licences.

Méthode : lecture ciblée du code (20 gestionnaires de paquets, 21 traitements par tick, l'économie, `JsonStore`,
`Backups`, `NpcBrain`, les permissions des commandes, les clients HTTP), du workflow GitHub Actions, des scripts de
déploiement et de la documentation existante (`docs/etat-du-projet.md`, `docs/fiabilisation.md`,
`docs/permissions.md`, `docs/pnj-intelligents.md`, `deploy/FALIX.md`). **Aucune compilation ni partie jouée** pour
cet audit : les points « à vérifier en jeu » sont signalés. Toutes les pistes sont **gratuites**.

---

## 1. En bref

Le serveur est **nettement au-dessus de la moyenne des projets amateurs** : les paquets venant du client sont
contrôlés (distance, droits, bornes, fréquence), les commandes d'administration sont protégées, l'économie a un
journal unique avec reçus anti-doublon, les écritures de fichiers sont atomiques, les traitements par tick sont
espacés, la CI joue de vrais scénarios multijoueurs avant tout déploiement, et les licences des mods et des données
sont respectées.

Les risques qui restent sont surtout **d'exploitation et de passage à l'échelle**, pas de sécurité grossière.

| Domaine | État | Risque principal |
|---|---|---|
| Sécurité des paquets et commandes | 🟢 Bon | Quelques paquets sans limite de fréquence (faible) |
| Triche | 🟠 Partiel | Anti-triche limité au vol ; pas d'anti-X-ray |
| Économie et données | 🟠 Correct pour une bêta | Écritures synchrones sur le fil serveur ; perte possible si le disque échoue |
| Sauvegardes | 🟠 Correct | Sur le même disque que le monde ; cohérence pendant le jeu |
| Performance du fil serveur | 🟢 Bon | Génération de la carte (voir l'autre audit) |
| PNJ à IA | 🟢 Très bon | — |
| CI et déploiement | 🟠 Bon mais direct | Tout push sur `main` part en production, sans validation humaine |
| Code et maintenabilité | 🟠 Moyen | Classe centrale de 829 lignes, état statique global, peu de tests Java |
| Vie privée | 🟠 À corriger | Adresse mail personnelle en clair dans un script public |
| Licences et politiques d'usage | 🟢 Bon | — |
| **Jeu réel** | 🔴 **Premier risque** | Rien n'a été joué en conditions réelles depuis la 0.15 (`docs/etat-du-projet.md`) |

Les cinq actions les plus rentables :

1. **Une vraie séance de jeu à plusieurs** avec une grille d'observation (§ 5.8) : c'est le risque n° 1, et aucun
   test automatique ne le couvre.
2. **Validation humaine avant la production** : déployer sur Falix seulement depuis un tag, ou via un
   *environment* GitHub avec approbation (§ 5.6).
3. **Écritures JSON hors du fil serveur** avec un seul fil d'écriture qui regroupe les sauvegardes (§ 5.3).
4. **Sauvegarde hors du serveur**, automatique (§ 5.4).
5. **Retirer l'adresse mail personnelle** du script de déploiement public (§ 5.1, S4).

---

## 2. Vue d'ensemble

```text
Clients (Prism / Modrinth, pack .mrpack)
  ├─ carte du monde : tuiles raster tile.openstreetmap.org + recherche Nominatim (côté client, politique respectée)
  └─ 20 types de paquets → serveur (achat, vente, missions, PNJ, carte, combinaison, machines…)

Serveur Falix (4 CPU, 8 Go, Java 25, Fabric 0.19.5)
  ├─ mods serveur : TerraCraft, Fabric API, Open Parties and Claims, Lithium, Krypton, FerriteCore, Chunky, spark
  ├─ terracraft-geo : ~200 classes Java, 29 000 lignes
  │    ├─ monde : génération Terre / Lune / Mars / astéroïdes (fils d'arrière-plan)
  │    ├─ jeu : villes et politique, économie et hôtel des ventes, missions, contrats, métiers, industrie,
  │    │        véhicules, espace, PNJ, campagne Exodus, raids, contamination, convois, primes…
  │    ├─ 21 traitements par tick (tous espacés : 0,5 s à 1 min)
  │    └─ données : ~20 fichiers JSON dans world/terracraft_geo/ (écriture atomique + .bak)
  ├─ services externes : AWS Terrain Tiles, OpenFreeMap, Open-Meteo (1 appel/min), IA PNJ (Ollama ou Mistral, optionnel)
  └─ sauvegardes : zip toutes les 6 h dans backups/ (3 gardées)

GitHub Actions (à chaque push et PR)
  contrôles → compilation → 3 scénarios avec serveur réel (campagne, PNJ, multijoueur) → branche falix → Falix redéploie
```

---

## 3. Points forts

- **Paquets client contrôlés** : achat, vente, missions, carte des étoiles, atelier, coffre de véhicule sont limités
  à une action toutes les 150 ms par joueur (`GeoMod.java:361-411`) ; les montants et index sont bornés
  (`AuctionHouse.java:404-414`) ; les actions sur machines et PNJ vérifient la distance
  (`Industry.java:153`, `Npcs.java:294-307`) ; le point de départ n'est accepté que s'il a été proposé et une seule
  fois à la fois (`StartPoints.java:127`) ; l'impact d'un crash d'avion est borné (`Plane.java:253`) ; les chaînes
  ont une longueur maximale dans les codecs (500 caractères pour les PNJ).
- **Commandes d'administration protégées** : tout ce qui téléporte, crée de l'argent ou des objets exige le niveau
  gamemaster (`GeoMod.java:615-700`, `AuctionHouse.java:161-169`).
- **Économie défendue** : un journal unique `portefeuilles.json` (soldes, annonces, reçus) écrit d'un bloc ;
  récompenses de campagne à **reçu unique** (`creditOnce`) ; débit, crédit et retrait d'annonce avant la remise de
  l'objet ; confirmation si une vente est très en dessous du prix moyen ; démarrage refusé si le journal est
  illisible, plutôt que de repartir de zéro.
- **Écritures atomiques** (`JsonStore`) : fichier temporaire, renommage, `.bak`, mise à l'écart d'un fichier corrompu.
- **PNJ à IA exemplaires** : file bornée (2 fils, 8 en attente), délai maximal, réponse limitée à 64 Ko et validée en
  JSON, quêtes limitées à une liste autorisée, 4 s entre deux messages par joueur, clé Mistral lue dans une variable
  d'environnement, HTTPS obligatoire hors machine locale, aucune IA dans les tests (`NpcBrain.java`, `Npcs.java:411`).
- **Fil serveur ménagé** : les 21 traitements par tick commencent par un test de fréquence ; les accès OSM depuis le
  fil serveur passent par `cellIfLoaded` (sauf deux cas, voir l'autre audit, Q3).
- **CI sérieuse** : contrôles des ressources et des recettes, compilation, **trois scénarios avec un vrai serveur**
  et des faux joueurs, journaux archivés en cas d'échec, mods téléchargés avec **vérification sha512**.
- **Licences et politiques respectées** : `mods/LICENCES.md`, mods « tous droits réservés » non redistribués,
  attributions OSM/relief/climat affichées, politiques d'usage des tuiles OSM et de Nominatim appliquées côté client
  (identifiant, 1 requête/s, cache 30 jours, attribution).
- **Documentation d'exploitation** claire (`deploy/FALIX.md` : mémoire, restauration testée, remise à zéro).

---

## 4. Ce qui était déjà connu (et reste vrai)

Le projet documente honnêtement ses faiblesses ; cet audit les confirme sans les répéter en détail :
- rien n'a été joué en vrai depuis la 0.15 (`etat-du-projet.md`, point 1) ;
- transactions non atomiques entre fichiers et inventaires, sauvegarde non cohérente pendant le jeu
  (`fiabilisation.md`, lots 2 et 3) ;
- anti-triche limité au vol, dette technique de `GeoMod.java`, écritures JSON sur le fil serveur
  (`etat-du-projet.md`, points 5 et 6).

Correction d'une note existante : `etat-du-projet.md` indique que le bug du cache Overture qui supprimait les
bâtiments OpenFreeMap est corrigé. La correction est **partielle** : le remplacement se fait maintenant par cellule
de 512 blocs, mais une cellule à cheval sur le bord de la zone Overture perd encore ses bâtiments OpenFreeMap
hors zone (audit carte, G5).

---

## 5. Constats détaillés

### 5.1 Sécurité, triche, vie privée

| # | Constat | Gravité | Où | Piste |
|---|---|---|---|---|
| S1 | **Paquets sans limite de fréquence** : visée, rechargement, fermeture de dialogue PNJ, demande d'écran de machine (renvoie un écran à chaque paquet), message PNJ (protégé plus loin par 4 s). Un client modifié peut les envoyer en rafale ; impact faible (pas d'écriture disque), mais du trafic sortant gratuit pour `RequestMachinePayload`. | Faible | `GeoMod.java:321-337` | Même `GUI_RATE` que les autres paquets d'écran. |
| S2 | **Carburant d'avion contournable par un client modifié** : le carburant se compte sur la distance parcourue côté serveur, mais un déplacement de plus de 8 blocs en un tick est ignoré (« téléportation »). Le contrôle vanilla des véhicules ne rejette qu'au-delà d'environ 10 blocs/tick : entre 8 et 10 blocs/tick, un client modifié vole vite **sans consommer**. Vitesse normale de l'avion : 1,5 bloc/tick. | Faible | `content/Plane.java:257-273` | Au-delà de `MAX_SPEED × 2` sans téléportation connue, ramener l'avion à sa position précédente et le signaler `[ANTITRICHE]`. |
| S3 | **Anti-triche limité au vol** ; `allow-flight` forcé à vrai (nécessaire pour la Lune et les fusées) désactive le contrôle vanilla. Pas d'anti-X-ray : dans un monde de ruines et de coffres, le X-ray est la triche la plus rentable. | Moyenne | `GeoMod.java:416-422`, `AntiFly.java` | Anti-X-ray serveur gratuit si une version existe pour 26.3 (à vérifier sur Modrinth) ; sinon masquer les coffres générés dans les bunkers derrière des blocs « leurres ». Surveiller les gains anormaux avec `/eco stats` et le journal `[HDV]`. |
| S4 | **Adresse mail personnelle en clair dans un script public** : valeur de secours de l'auteur des commits de déploiement. Elle est déjà dans l'historique Git public. | Moyenne (vie privée) | `deploy/make_falix_branch.sh` (avant-dernier bloc) | Remplacer par l'adresse « noreply » GitHub déjà utilisée pour les commits. L'historique ne s'efface pas facilement : accepter l'exposition passée ou utiliser une adresse dédiée au projet. |
| S5 | **Droits d'écriture du jeton CI pour tous les déclencheurs**, y compris les PR (les PR de forks reçoivent un jeton en lecture, mais une PR depuis une branche du dépôt garde l'écriture) ; actions référencées par étiquette (`@v7`) et non par empreinte ; Carpet téléchargé sans contrôle d'empreinte pour les tests. | Faible | `.github/workflows/build.yml:12-13`, `tools/scenario_test.sh:14-20` | `permissions: contents: read` au niveau du workflow, `write` seulement sur l'étape de la branche falix et des releases ; épingler les actions par SHA (Dependabot, gratuit, les met à jour) ; vérifier le sha512 de Carpet comme pour les autres mods. |
| S6 | `ops.json` réécrit à chaque déploiement : voulu et documenté. Un opérateur ajouté depuis la console disparaît au prochain push. | Info | `deploy/FALIX.md` | Rien, sauf le rappeler aux modérateurs. |

### 5.2 PNJ à IA

Rien de bloquant. Deux remarques :
- le message du joueur et la mémoire sont envoyés au modèle **avec le pseudo du joueur** (`Npcs.java:425`). Avec
  Mistral (service externe), mentionner dans les règles du serveur que les conversations avec les PNJ passent par un
  service tiers ;
- la consigne système dit que les messages sont des données, mais un modèle peut encore être détourné. La protection
  réelle est ailleurs et elle est bonne : le modèle ne choisit qu'une quête dans une liste et ne peut ni donner, ni
  valider, ni exécuter quoi que ce soit.

### 5.3 Économie et données

| # | Constat | Gravité | Où | Piste |
|---|---|---|---|---|
| E1 | **Chaque transaction réécrit 4 fichiers complets sur le fil serveur** (journal, soldes, annonces, statistiques). Avec quelques centaines d'annonces, un achat écrit des centaines de Ko pendant le tick ; 20 modules font de même. Suffisant pour 10 joueurs, pas pour 50 actifs. | Moyenne | `AuctionHouse.java:131-145`, 20 fichiers utilisant `JsonStore.write` | **Un fil d'écriture unique** qui reçoit des instantanés et regroupe les écritures (au plus une par fichier toutes les 1 à 2 s), avec écriture forcée à l'arrêt. Plus tard, **SQLite** (gratuit, une seule base, vraies transactions) comme prévu au lot 2. |
| E2 | **Une écriture ratée n'annule pas l'opération** : `save()` journalise l'erreur et le joueur reçoit « Achat confirmé ». En vente, l'objet a déjà quitté l'inventaire. Si le serveur s'arrête avant une écriture réussie, l'objet est perdu. (Seul `creditOnce` annule proprement.) | Moyenne | `AuctionHouse.java:142-144`, `296-339` | Reprendre le modèle de `creditOnce` (instantané avant, retour arrière si l'écriture échoue) pour l'achat, la vente et le retrait. C'est le lot 2 de `fiabilisation.md`. |
| E3 | **Pas de synchronisation disque** (`fsync`) avant le renommage : après une coupure électrique, un fichier peut être vide. Le `.bak` limite les dégâts. | Faible | `JsonStore.java` (`write`) | `FileChannel.force(true)` sur le fichier temporaire avant `move`. |
| E4 | **Une vingtaine de fichiers indépendants** : pas de cohérence entre eux (une ville, son stock, la trésorerie, les soldes). | Connu | `fiabilisation.md` | Regrouper l'économie, les villes et la logistique dans le même journal ou la même base. |

### 5.4 Sauvegardes

| # | Constat | Piste gratuite |
|---|---|---|
| B1 | Les archives sont dans `backups/` **sur le même serveur** : perte du serveur ou du compte = perte des sauvegardes. | Copie automatique hors du serveur : **rclone** (libre) vers un stockage gratuit (Google Drive 15 Go, un autre PC), ou les sauvegardes du panel Falix. Tester une restauration une fois par mois. |
| B2 | Les fichiers JSON du mod continuent d'être écrits pendant la compression (`fiabilisation.md`, lot 3). | Avec le fil d'écriture unique (E1) : le suspendre le temps de copier `terracraft_geo/` en premier. |
| B3 | Trois archives plus le monde : surveiller l'espace disque (déjà noté). | Afficher l'espace libre dans `/terracraft sauvegarde liste`. |

### 5.5 Performance et capacité

- **Fil serveur** : les 21 traitements par tick sont espacés et légers d'après le code. Le seul gros consommateur
  identifié est la **génération de la carte** (mémoire, relief, téléchargements) : voir l'audit carte, P1 à P5.
- **Mods de performance** déjà en place côté serveur : Lithium, Krypton, FerriteCore, spark. **Chunky est installé**
  sur le serveur mais rien n'indique qu'il soit utilisé : pré-générer les villes de départ en heures creuses retire
  la plus grosse charge des soirées de jeu.
- **Capacité** : les mesures d'octobre (10 joueurs, tick < 14 ms) sont bonnes. Il manque une mesure après **une heure
  de vrai jeu** (mémoire, temps de sauvegarde, taille des JSON) : à faire pendant la séance du § 5.8 avec `/spark
  health` et `/spark profiler`.

### 5.6 Intégration continue et déploiement

| # | Constat | Gravité | Piste gratuite |
|---|---|---|---|
| D1 | **Tout push sur `main` part en production** : la CI met à jour la branche `falix`, Falix redéploie tout seul. Aucune validation humaine, aucune étape de recette. Un changement qui passe les tests mais casse l'expérience part directement chez les joueurs. | Moyenne | Mettre à jour `falix` **seulement sur un tag** (`v*`), ou passer par un *environment* GitHub « production » avec approbation obligatoire (gratuit sur un dépôt public). Le serveur de test local (`serveur-local/`) sert de recette. |
| D2 | **Effet d'un redéploiement à chaud à vérifier** : Falix copie la branche « par-dessus le serveur ». Un jar remplacé pendant que le serveur tourne n'est pris en compte qu'au redémarrage ; à confirmer avec Falix, et prévenir les joueurs avant chaque redémarrage. | À vérifier | Annonce en jeu avant redémarrage, redémarrage planifié. |
| D3 | **Dépôt alourdi par les jars** : `.git` pèse 77 Mo, `mods/` 39 Mo réécrits à chaque version. Chaque mise à jour des mods ajoute des dizaines de Mo à l'historique. | Faible | `install_mods.py` sait déjà tout télécharger avec contrôle sha512 : retirer `mods/*.jar` et le `.mrpack` du suivi Git et les publier seulement dans les releases. |
| D4 | **Version annoncée aux services externes figée** (`TerraCraftGeo/0.2`). | Faible | Lire la version du mod. |
| D5 | **Pas de surveillance extérieure** du serveur. | Faible | Une GitHub Action planifiée (gratuite) qui lance `tools/ping_server.py` toutes les 15 min et poste dans un salon Discord par webhook en cas d'échec. |

### 5.7 Code et maintenabilité

| # | Constat | Piste |
|---|---|---|
| C1 | **`GeoMod.java` (829 lignes)** enregistre tout : contenus, paquets, commandes, 21 ticks, et porte 28 objets statiques créés au chargement (`static final … = new …`), dont la plupart des systèmes de jeu. | Un fichier d'enregistrement par domaine (économie, villes, espace, PNJ, monde) ; `GeoMod` ne fait plus qu'appeler `X.register()`. |
| C2 | **Paquet Java unique** de ~150 classes (`com.terracraft.geo`). | Sous-paquets par domaine ; c'est aussi ce qui permet des tests ciblés. |
| C3 | **4 tests unitaires Java** (campagne, `JsonStore`, PNJ) pour 29 000 lignes ; la logique pure (prix, frais, élections, contrats, monde) n'est pas testée en dehors des scénarios. Les scénarios CI sont excellents mais lents et ne couvrent pas les cas limites. | Tests JUnit sur ce qui ne dépend pas de Minecraft : `Economy` (frais, prix moyen), `TownPolitics` (échéances, défiance), `Contracts`, `RateLimit`, `EarthTerrain` (audit carte, Q1). |
| C4 | **État statique global** : complique les tests et le rechargement à chaud. | Introduire progressivement des objets créés au démarrage du serveur et passés en paramètre. |

### 5.8 Jeu réel : le premier risque

Selon `docs/etat-du-projet.md`, écrans, mixins d'inventaire, HUD, combinaison, carte des étoiles, équilibrage des
combats et des primes **n'ont pas été joués** depuis la 0.15. Les scénarios automatiques simulent des joueurs mais
ne voient ni un écran mal placé, ni une interface confuse, ni un équilibrage frustrant.

Proposition gratuite et rapide :

1. **Séance de 90 minutes à 4 ou 5 joueurs**, whitelist activée, sur le serveur Falix après une sauvegarde.
2. **Parcours imposé** : choix du point de départ → premier abri → première vente → ville → véhicule → reconnexion
   (déjà décrit au lot 5 de `fiabilisation.md`).
3. **Grille d'observation** pour chaque étape : temps passé, blocages, messages incompris, bugs visuels, commande
   tapée sans aide. Un observateur qui ne joue pas prend les notes.
4. **Mesures** pendant la séance : `/spark health` toutes les 15 min, `/eco stats` au début et à la fin, taille de
   `world/terracraft_geo/`.
5. **Retour** : un ticket GitHub par problème, étiqueté par gravité.

### 5.9 Licences et données

Rien à corriger. À garder en tête si des données de l'audit carte sont ajoutées : FABDEM est **non commercial**
(CC BY-NC-SA) ; ODbL pour OSM et les bâtiments Overture (attribution déjà affichée) ; Licence Ouverte pour l'IGN.

---

## 6. Plan d'action global (carte + serveur)

| Ordre | Chantier | Effort | Origine |
|---|---|---|---|
| 1 | Séance de jeu réelle avec grille d'observation | S | § 5.8 |
| 2 | Retirer l'adresse mail personnelle du script de déploiement | S | S4 |
| 3 | Production seulement sur tag ou après approbation | S | D1 |
| 4 | Copie automatique des sauvegardes hors du serveur, restauration testée | S | B1 |
| 5 | Tests unitaires du monde et de la logique économique | M | Carte Q1, C3 |
| 6 | Ne plus figer les zones de carte dégradées | M | Carte G3 |
| 7 | Fil d'écriture unique pour les JSON, retour arrière si l'écriture échoue | M | E1, E2 |
| 8 | Masque terre/mer, filtre Overture, relief par chunk | M | Carte G2, G5, P1 |
| 9 | Limite de fréquence sur les derniers paquets, contrôle de vitesse des avions | S | S1, S2 |
| 10 | Durcir la CI (droits minimaux, actions épinglées, Carpet vérifié) | S | S5 |
| 11 | Montagnes, rivières, tuiles maison, données IGN | M à L | Carte G1, G4, § 5 |
| 12 | Découpage de `GeoMod`, sous-paquets | M | C1, C2 |
| 13 | SQLite pour l'économie et les villes | L | E4 |

S = moins d'une journée · M = quelques jours · L = une à deux semaines.

---

## Annexe — Ce qui a été vérifié

- Les 20 gestionnaires de paquets `registerGlobalReceiver` (`GeoMod.java:321-413`) et leurs fonctions cibles :
  `StartPoints.onChoice`, `Industry.refresh`, `Plane.reportCrash`, `Npcs.talkFromClient` / `talk`,
  `AuctionHouse.buy` / `sell` / `*FromClient`, codecs `StartPointPayload` et `NpcChatMessagePayload`.
- Les sous-commandes de `/terracraft` (permissions), `/eco`, `/hdv`, `/comptoir`.
- Les 21 traitements par tick et leur fréquence.
- La persistance : `JsonStore`, le journal `portefeuilles.json`, `Backups`.
- Les appels réseau du mod (serveur et client) et leurs politiques d'usage.
- `.github/workflows/build.yml`, `deploy/make_falix_branch.sh`, `deploy/FALIX.md`, `deploy/ops.json`,
  `tools/mods.txt`, `tools/install_mods.py`, `mods/LICENCES.md`.

Non vérifié : comportement en jeu (interfaces, équilibrage, mixins), redéploiement à chaud chez Falix, disponibilité
d'un anti-X-ray pour Minecraft 26.3.
