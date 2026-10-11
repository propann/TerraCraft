# Feuille de route TerraCraft

Objectif : un serveur survie multijoueur sur la Terre réelle après la chute de l'humanité — exploration des villes
réelles, commerce, villes de joueurs, véhicules, danger, et une progression qui mène jusqu'aux stations lunaires et à
Mars.

Principes : chaque phase doit être **jouable et vérifiable** avant la suivante ; le serveur passe avant l'interface ;
les données réelles et la performance passent avant les effets ; une fonctionnalité n'est cochée que si elle est en
place, et marquée « à tester en jeu » tant qu'aucun joueur ne l'a validée.

Références : [état du projet](etat-du-projet.md) · [avancement](avancement.md) · [commandes](commandes.md) ·
[système solaire](architecture-systeme-solaire.md).

Légende : `[x]` fait · `[~]` fait, à tester en jeu · `[ ]` à faire (avec notre intention).

## Plan d'action (depuis la 0.38, 11 octobre 2026)

Audit du 11 octobre : le serveur Falix tourne en 0.37.1 (4 CPU, 8 Go) ; la 0.38 est prête en local (vaisseau, onglets
créatifs, monde à l'échelle 1:1 de Paris). **Le monde est remis à zéro** : comptes, villes, stations et claims
repartent de rien sur la nouvelle carte. Chaque étape se termine par un déploiement vérifié.

### Étape 1 — Relance du monde (0.38)

- [x] Échelle 1 bloc = 1 m à Paris, une seule carte continue (un joueur parti d'Espagne et un autre d'Allemagne se
      retrouvent en France sur la même carte, sans superposition) ; étages de 4 blocs.
- [x] **Vaisseaux construits** : une construction de blocs de vaisseau (coque, pentes, verrières, réacteurs, réservoirs,
      sas, siège, console) devient un vaisseau ; en vol, ses blocs sont des block displays vanilla (rien à installer
      côté client), puis il est reposé à l'identique (coffres compris) ; à une station, sas contre sas (l'aire
      d'atterrissage est un bloc sous la station). Kit de vaisseau = navette en blocs. Testé par le scénario.
- [x] **Recettes vérifiées** en CI (`tools/check_recipes.py` : ingrédients 26.3, motifs, objets obtenables).
- [x] **Catalogue** (menu `O`) : objets par catégorie, description, recette, « sert à fabriquer » ; descriptions aussi
      dans les info-bulles de l'inventaire. Chaque objet doit avoir sa description et sa catégorie (CI).
- [x] **Politique** : élection du maire (tous les 14 jours, motion de défiance, scrutin de 48 h), registre de la mairie.
- [x] Cache Overture lu une fois et limité à sa zone ; cache OSM indépendant de l'échelle.
- [~] Mémoire à 10 joueurs simultanés : banc refait sur la nouvelle échelle ([mesures](mesures-generation.md)).
- [ ] Test de scénario complet sur la 0.38 (`tools/scenario_test.sh`), puis essai en jeu local (Paris, vaisseau,
      onglets, carte des étoiles, ciel spatial, cockpit).
- [ ] Déploiement : sauvegarde du panel, suppression de `world/` sur Falix ([procédure](../deploy/FALIX.md)), push
      sur `main`, tag `v0.38.0` (pack client), `tools/sync_client_mods.py`.
- [ ] Préchargement (Chunky) autour de Paris et des points de départ proposés, pour une arrivée sans attente.

### Étape 2 — Bâtiments au niveau (phase 7)

- [ ] **Classification** maison / immeuble / commerce / bureau / industrie / équipement / tour à partir des tags OSM
      et des classes OpenMapTiles ; style, toit et intérieur par classe.
- [x] **Rapport de génération** `/terracraft rapport [rayon]` : bâtiments par étages et toits, le plus haut, bâtiments
      suspects (hauteur des données réduite) avec coordonnées ; journal `[GEN]`.
- [ ] **Façades à la Minecraft** : rez-de-chaussée commerçant plus haut en centre-ville, lucarnes dans les mansardes,
      cours intérieures fermées, entrées alignées sur la rue.
- [ ] **Densité** : centre, banlieue, campagne, zones industrielles (mobilier urbain, voitures abandonnées, végétation).
- [ ] Cache Overture préparé pour Paris intramuros puis les grandes villes ; mesure avec et sans cache.

### Étape 3 — Objets lisibles (interface)

- [x] Onglets créatifs TerraCraft (Espace, Stations, Vaisseaux, Industrie, Véhicules, Combat, Matériaux).
- [x] **Catalogue en survie** : nos objets par catégorie avec leur description et leur recette (menu `O`).
- [ ] Écran « Mon personnage » unifié (fiche, métier, plans, stations).
- [ ] Textures des armes et pièces à harmoniser.

### Étape 4 — Espace (phase 8)

- [ ] Test en jeu de la carte des étoiles, de la console de navigation, du cockpit et du ciel spatial ; corriger.
- [ ] Le vaisseau sur la carte des étoiles (icône, autonomie restante) et `/station` qui montre les quais libres.
- [x] Vaisseau personnalisable avec les blocs de vaisseau (coque, verrière, réacteurs) autour du siège de pilote.
- [ ] Vaisseau : rotation en vol, plusieurs sas, cartes des vaisseaux (où est garé le mien ?).
- [ ] Politique, suite : lois municipales votées (taxe, ville ouverte), écran de la mairie.
- [ ] PNJ vivants (idée) : habitants et marchands dialoguant via une IA française (Mistral), avec prompts par rôle.
- [ ] Laboratoire de station ; garder la Terre utile (ressources terrestres pour les améliorations avancées).

### Étape 5 — Dette technique (phase 10)

- [ ] Découper `GeoMod` (enregistrements), `OsmCells` (téléchargement / rastérisation) et `GeoChunkGenerator`
      (terrain / bâtiments / décoration).
- [ ] Écritures JSON hors du fil serveur (file d'écriture unique, toujours atomique).
- [ ] Tests unitaires du monde (projection, hauteurs, étages, classification) exécutés en CI sans serveur.

### Étape 6 — Ouverture

- [ ] Séance de test à plusieurs sur Falix (`/spark health`, `/terracraft suivi`), corrections.
- [ ] Bêta privée (whitelist, canal de retours), course à l'espace le soir de l'ouverture, puis bêta publique.

## Phase 0 — Stabilisation technique

Bloquante pour l'ouverture publique. Critère de sortie : 30 minutes de jeu à plusieurs sans crash, blocage de
génération ni perte d'objet.

- [x] Sauvegardes automatiques du monde, rotation, restauration testée.
- [x] Données joueurs atomiques (`.bak`), versions de mods figées, test de démarrage et test de scénario en CI.
- [x] **Carte neuve sur des points réels** : `tools/geo_bench.sh` (bot spectateur, `/terracraft aller <lat> <lon>`,
      `/terracraft generation`) ; 8 lieux mesurés le 2026-10-08, voir [mesures-generation.md](mesures-generation.md)
      (Paris 31 ms/chunk au pire, tick < 2 ms, aucun téléchargement raté). Ajouter des lieux au besoin.
- [ ] **Avec et sans cache Overture** : comparer temps et rendu ; documenter quand préparer le cache.
- [ ] **Bâtiments géants, vides ou dupliqués** : rapport de génération (voir phase 7) pour les repérer.
- [x] **Mesures** : 1, 5 et 10 joueurs simultanés (tick 2 / 9 / 11 ms), mémoire, taille du monde ; seuils de RAM
      dans `deploy/FALIX.md` (3 Go jusqu'à 5 joueurs, 5 Go pour 10). Reste : mémoire après une heure de jeu réel.
- [~] Véhicules, armes, claims, économie, combinaison, avion : vérifiés côté serveur, à valider en séance de jeu.

## Phase 1 — Accueil et interface joueur

- [x] Menu TerraCraft (`O`), `/aide`, carnet de survie, touches affichées.
- [x] Parcours « Premiers pas » (6 étapes récompensées).
- [x] Accueil des habitués : titre, solde, contrats, ville, métier, nouveautés depuis la dernière visite.
- [x] Liste des serveurs (MOTD avec version, icône) et liste des joueurs (Tab).
- [x] **Visuel** : menu `O` en tuiles avec icônes, barres de vie au-dessus des ennemis et des animaux blessés ou
      visés, chat `Pseudo » message`, arrivées et départs lisibles, ville en préfixe coloré (chat, Tab, au-dessus
      de la tête), jour et nuit au rythme normal de Minecraft (la météo reste réelle).
- [x] **Uniformiser l'interface** : toutes les fenêtres (menu `O`, fiche `K`, missions, marché, atelier, carte des
      étoiles, carte du monde) et les indicateurs partagent le style de `Ui` (cadre, bandeau, liseré doré, jauges).
- [x] **Confirmations** avant les actions coûteuses : fonder une ville (500 crédits), la dissoudre (dernier habitant),
      vendre à moins de la moitié du prix moyen (chat : [Confirmer] / [Annuler], 30 s ; écran du marché : boîte de
      dialogue). Les erreurs disent quoi faire ensuite (solde manquant, maire à remplacer…).
- [ ] **Écran « Mon personnage » unifié** (fiche, métier, plans, stations) au lieu de commandes éparses.

## Phase 2 — Claims, villes et groupes

- [x] Villes : maire, habitants, trésorerie, `/ville tp`, titres d'entrée et de sortie.
- [x] Propriétaire du territoire affiché en changeant de zone.
- [~] **Claims de ville** : les chunks revendiqués par un habitant à moins de 256 blocs du centre comptent comme la
      ville (titre d'entrée, vagues…), et s'affichent « Ville de X · terrain de Pseudo » ; `/ville` montre le
      territoire. À vérifier en jeu avec de vrais claims (non couvert par le test automatique).
- [x] **Rôles de ville** : le maire nomme des adjoints (`/ville adjoint <joueur>`, bascule) qui invitent, excluent
      les simples habitants et paient depuis la trésorerie ; centre-ville et passation restent au maire.
- [x] **Protection des stations et bases** : 48 blocs autour de chaque balise, réservés au propriétaire, aux habitants
      de sa ville et aux opérateurs en créatif (casser, poser, ouvrir) ; journal `[PROTECTION]`. Sur Terre, les claims
      Open Parties and Claims protègent tout (exceptions réglées sur « personne »).
- [ ] **Protection des véhicules et coffres** dans les claims d'autrui (aujourd'hui : verrou du propriétaire).
- [ ] **Taxe de ville légère** seulement si elle finance un service visible (balise de ville, garage, téléporteur).

## Phase 3 — Économie et commerce

- [x] Hôtel des ventes par catégories, prix moyens, vente depuis l'écran, comptoir, frais 2 %, `/eco stats`.
- [x] Sources : missions, contrats du jour, tutoriel, ventes.
- [x] **Magasins de joueurs** : étal de marché (laine, planches, coffre, or) qui vend l'objet de son stock à prix fixe,
      même vendeur hors ligne ; paiement de compte à compte ; stock inaccessible aux entonnoirs ; seul le propriétaire
      casse l'étal (`/etal`, `/etal prix`, `/etal ajouter`, `/etal acheter`, Maj + clic droit).
- [x] **Primes** : 300 crédits par boss de bunker, vagues nocturnes récompensées, primes de joueurs en zone PvP.
- [ ] **Relevé hebdomadaire** de `/eco stats` et ajustement des prix du comptoir si l'argent s'accumule.

## Phase 4 — Missions et progression

- [x] Journal des missions, contrats du jour, familles (exploration, combat, véhicules, économie, villes, espace).
- [x] Métiers avec bonus réels.
- [x] **Missions liées aux lieux réels** : découverte par type de lieu, contrats du jour « Tournée des ruines » (3 lieux),
      « Urgences » (hôpital ou pharmacie), « Réseau de transport » (gare ou station-service) ; chaque lieu compte une
      fois par joueur.
- [x] **Collections** : paliers de lieux réels visités (5, 20, 50), de soins (5) et de transports (5), en missions
      permanentes. Reste : bunkers, véhicules réparés, minerais.
- [ ] **Missions de ville** : objectifs communs (déposer X crédits, poser N modules) avec récompense partagée.
- [x] **Réputation** : offres réservées du comptoir par métier et niveau de compétence (carburant de fusée pour les
      pilotes, munitions en gros, moteur et roues, détecteur de pétrole, batterie).

## Phase 5 — Véhicules et transport

- [x] Propriétaire, verrou, partage, coffre (touche `V`), démontage sûr, avion au clavier, jetpack.
- [x] **Industrie du carburant** : gisements (flaques, détecteur), pompe à pétrole, panneaux solaires et câbles,
      tuyaux, raffinerie (brut → essence + kérosène), réservoirs, pompe à essence ; bidon vide rendu après chaque
      plein. Rendement : ~1 bidon par minute sur un riche gisement avec 2 panneaux, ~5 min sur un petit.
- [x] **Industrie, suite** : stations-service en ruine aux emplacements réels (OSM amenity=fuel : auvent, pompes
      reliées à une cuve enterrée de 2 000 à 8 000 mB), batteries de stockage pour la nuit, écran à jauges des machines.
- [x] **Groupe électrogène** : brûle de l'essence quand panneaux et batteries manquent (5 mB par unité et par seconde).
- [x] **Éclairage électrique** (lampe allumée quand le réseau a de l'énergie) et **réseaux d'environ 1 000 blocs**
      (oléoducs, lignes électriques).
- [x] **Tableau de bord** des véhicules terrestres : vitesse, carburant ou batterie, autonomie, état, pièces manquantes.
- [ ] **Garage de ville** : réparation et plein contre crédits.
- [ ] **Rôles clairs** : moto rapide et fragile, voiture polyvalente, camion lent à grand coffre (réglages chiffrés).
- [ ] **Test multi-joueurs** : collisions, pentes, frontières de chunks, passagers, reconnexion en roulant.

## Phase 6 — Combat, danger et apocalypse

- [x] Ravitaillements militaires réguliers.
- [~] Armes à chargeur, recul, zoom, grenades ; anti-triche vol.
- [x] **Convoi militaire en panne** (toutes les 50 à 80 min) : camion à réparer et garder, caisse de matériel, escorte
      armée (zombies et squelettes casqués, pillards), annonce et fumée.
- [x] **Zones contaminées** fixes (18 % des carrés de 1 024 blocs, rayon 40 à 110) : poison et faim sans casque et
      combinaison, wither au cœur, compteur à l'approche, cache de matériel au centre (une par zone).
- [x] **Vague nocturne sur une ville** : une chance sur sept à la tombée de la nuit pour chaque ville défendue (un
      habitant à moins de 64 blocs du centre) ; trois vagues convergent vers le centre ; 70 % repoussés avant l'aube :
      100 + 50 crédits par vague pour la trésorerie, découverte « Rempart ».
- [x] **Zones PvP explicites** : PvE partout, PvP seulement dans les cercles déclarés (`/pvp creer <rayon> <nom>`),
      titre à l'entrée et à la sortie ; primes sur les joueurs (`/prime`), argent bloqué, gagnées en zone PvP.
- [x] **Boss rares** : un bunker sur cinq gardé par un « Commandant du bunker » (120 PV, barre de boss) ; insigne du
      commandant, diamants, netherite, prime de 300 crédits, découverte.
- [ ] **Textures et icônes** des armes et pièces à reprendre (cohérence visuelle).

## Phase 7 — Monde vivant et génération avancée

- [ ] **Classification fiable** maison / immeuble / commerce / industrie / tour à partir des tags OSM et Overture.
- [x] **Échelle et étages (0.38)** : 1 bloc = 1 m à Paris (échelle 0,6583, une seule carte Mercator continue pour la
      planète : Madrid 1,16 m, Berlin 0,93 m par bloc) ; étages de 4 blocs partout (nombre réel d'étages, ~1,3× le
      réel en hauteur), fenêtres de 2 blocs. Monde neuf obligatoire (`deploy/FALIX.md`).
- [x] **Hauteurs mesurées conservées** (tours jusqu'à 250 m) avec garde-fou d'élancement (≤ 6 × la largeur moyenne) ;
      plafond par type quand la hauteur est devinée.
- [x] **Cache Overture** lu une seule fois et appliqué seulement à la zone qu'il couvre (avant : relu à chaque
      cellule, et les bâtiments OpenFreeMap disparaissaient partout ailleurs) ; cache indépendant de l'échelle.
- [ ] **Refus des géométries aberrantes** et rapport de génération (`/terracraft rapport`) listant les bâtiments
      suspects avec leurs coordonnées.
- [x] **Intérieurs par type** : butin contextualisé, deux fois plus de coffres, mobilier selon le lieu (lits et
      alambics à l'hôpital, cellules au commissariat, rayons au supermarché, pupitres à l'école, rails à la gare…).
- [x] **Points d'intérêt** : hôpitaux, pharmacies, commissariats, écoles, gares, supermarchés, casernes, mairies,
      stations-service (OSM, avec leur nom) ; `/lieux` ; découverte au premier passage (vérifié : 68 lieux au centre
      de Paris).
- [ ] **Densité variable** entre centre, banlieue, campagne et zones industrielles.

## Phase 8 — Espace et endgame

- [x] Orbites terrestre et lunaire, Lune, Mars, carburant par trajet (escales).
- [x] **Station orbitale d'abord** : depuis la Terre on ne va qu'en orbite ; le kit de station orbitale, chargé dans la
      fusée, déploie au premier vol une station complète (salle de travail, tunnels vitrés, stockage, quai d'amarrage
      avec pinces, sas étanches). La Lune et Mars partent d'un quai de station.
- [x] **Fusées à 1, 2, 3 ou 4 réservoirs** (8 à 20 doses, autant de charges utiles), propulseurs visibles, hublots.
- [x] **Bases lunaire et martienne en kit** (charge utile) déployées à l'atterrissage : aire avec pinces, sas, salle de vie.
- [x] **Rover lunaire** (charge utile ou caisse) : électrique, recharge solaire à l'arrêt, remballé par son propriétaire.
- [x] **Sous-sol lunaire vivant** : cavernes géantes, sanctuaires (salle en dôme, pyramide, gardiens, puits marqué),
      donjons enfouis, cristaux lumineux, artefacts extraterrestres (exigés par la navigation martienne).
- [x] **Carte des étoiles** : écran de navigation (destinations, coûts, obstacles, stations) à la place du choix au
      clic ; `/fusee carte`, `/fusee cap <destination>`.
- [x] Stations en kit (modules pressurisés), balise d'arrivée, `/station`.
- [x] Plans de fusée et atelier de station ; Mars exige la navigation martienne.
- [x] **Stockage partagé** : terminal logistique, stock commun de 54 cases par ville, le même dans tous les terminaux
      (Terre, stations, bases) ; `/ville stock [deposer|retirer]` devant un terminal.
- [x] **Dangers lunaires** : épaves de satellites avec butin, pluies de micrométéorites annoncées (un toit protège,
      fragments à ramasser), rôdeurs plus rapides et plus forts la nuit lunaire.
- [x] **Objectif coopératif** : grande station de ville — 10 modules posés par les habitants réunis : 1 500 crédits pour
      la trésorerie, une fois (non couvert par le test automatique). Serre hydroponique (nourriture dans l'espace) ;
      reste : laboratoire.
- [x] **Ceinture d'astéroïdes** : nouvelle destination (depuis l'orbite de Mars, 3 doses, navigation martienne), rochers
      flottants rocheux, métalliques et glacés (fer, or, titane, diamants, hélium-3), quai à l'arrivée, carte des
      étoiles, découverte et étape de la course ; récupération dans le vide (toutes les orbites).
- [~] **Vaisseau spatial (0.38)** : kit (3 réservoirs, 2 coques, 2 moteurs, ogive, ailerons) livré monté à 4 réservoirs ;
      tunnel-sas sans butin déployé à l'amarrage et replié au décollage ; blocs de vaisseau (coque, verrière, réacteur,
      siège, console de navigation) ; onglets TerraCraft du menu créatif dont « Vaisseaux » ; cockpit et ciel spatial.
- [ ] **Garder la Terre utile** : ressources terrestres indispensables aux améliorations avancées (commerce Terre ↔ espace).

## Phase 9 — Bêta publique et exploitation

- [x] Journaux des transactions, villes, véhicules, largages, métiers, signalements.
- [x] `/signaler` avec alerte aux opérateurs ; pack client synchronisé, lien stable, versions publiées.
- [ ] **Bêta privée** avec whitelist et un canal de retours.
- [x] **Règles publiées** (PvP, claims, triche, comportement) dans le README et en jeu (`/regles`, première connexion).
- [x] **Modérateurs sans être opérateurs** : rôle intégré (`/moderateurs ajouter`), outils `/mod` (expulser,
      silence, avertir, aller, signalements), journal `[MOD]`. LuckPerms (disponible pour 26.3) n'est pas utilisé : il ne
      règle pas les commandes du mod ; à reconsidérer si une hiérarchie fine devient nécessaire.
- [x] **Événement d'ouverture** : course à l'espace (`/terracraft course demarrer`) ; le premier à chaque étape
      (orbite, station orbitale, Lune, base lunaire, sanctuaire, Mars) gagne 200 à 500 crédits ; titre pour tous ;
      tableau `/course`.
- [x] **Suivi** (`/terracraft suivi`, activite.json) : joueurs uniques, actifs 24 h / 7 j, retour après le premier
      jour, temps de jeu, joueurs en ligne, tick moyen. Reste : missions et contrats remplis, erreurs.

## Phase 10 — Qualité du code et performance

Serveur Falix : 4 CPU, 8 Go. Règle : génération hors du fil principal, rien de bloquant sur le fil serveur, caches
bornés en mémoire.

- [x] Génération hors du fil principal ; accès OSM depuis le fil serveur par `OsmCells.cellIfLoaded`.
- [x] Cache Overture chargé une fois ; tuiles vectorielles décodées en mémoire (12) ; cache disque partagé.
- [ ] Découpage des trois gros fichiers, écritures JSON asynchrones, tests unitaires du monde (voir étape 5).

## Ordre de développement

Voir le [plan d'action](#plan-daction-depuis-la-038-11-octobre-2026) : relance du monde (0.38), bâtiments, objets
lisibles, espace, dette technique, ouverture.

## Validation avant chaque déploiement

- `tools/check_assets.py` passe (ressources complètes) et `tools/scenario_test.sh` aussi (il inclut le test de démarrage).
- Le pack client est publié si le client change (nouveaux objets, écrans, paquets).
- Après la release : `python3 tools/sync_client_mods.py` met à jour `mods/` et `TerraCraft-client.mrpack` du dépôt, puis commit.
- `docs/avancement.md` et cette feuille de route sont à jour.
- Aucun système ne supprime silencieusement un objet, un crédit, une ville, une station ou un véhicule.
