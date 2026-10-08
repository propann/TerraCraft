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
- [ ] **Missions liées aux lieux réels** : « rejoindre l'hôpital/la gare la plus proche », à partir des données OSM
      déjà téléchargées (points d'intérêt de la phase 7).
- [ ] **Collections** : villes réelles visitées, bunkers, véhicules réparés, minerais ; récompense par palier.
- [ ] **Missions de ville** : objectifs communs (déposer X crédits, poser N modules) avec récompense partagée.
- [ ] **Réputation** : un niveau par métier, débloquant des offres du comptoir.

## Phase 5 — Véhicules et transport

- [x] Propriétaire, verrou, partage, coffre (touche `V`), démontage sûr, avion au clavier, jetpack.
- [ ] **État détaillé** dans l'écran du véhicule : moteur, roues, carburant, dégâts.
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
- [ ] **Hauteurs Overture** seulement quand la donnée est fiable ; plafond par type sinon.
- [ ] **Refus des géométries aberrantes** et rapport de génération (`/terracraft rapport`) listant les bâtiments
      suspects avec leurs coordonnées.
- [ ] **Intérieurs par type** : mobilier, éclairage, entrées, butin contextualisé (pharmacie, armurerie, garage).
- [ ] **Points d'intérêt** : hôpitaux, gares, stations-service, écoles, utilisés par les missions.
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
- [ ] **Stockage partagé de station** entre habitants d'une même ville.
- [x] **Dangers lunaires** : épaves de satellites avec butin, pluies de micrométéorites annoncées (un toit protège,
      fragments à ramasser), rôdeurs plus rapides et plus forts la nuit lunaire.
- [ ] **Objectifs coopératifs** : grande station de ville (N modules, laboratoire, serre) avec récompense collective.
- [ ] **Astéroïdes** : petites zones instanciées, minage de métaux rares, jetpack obligatoire.
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

## Ordre de développement

Fait depuis : phase 0 (mesures), phase 1 (interface, confirmations), phase 6 (événements, PvP), phases 2 et 3
(villes, étals), phase 9 (règles, modération, suivi, course à l'espace).

1. **Séance de test à plusieurs** sur Falix, puis bêta privée (whitelist, canal de retours, course à l'espace).
2. Génération enrichie et points d'intérêt, puis missions liées aux lieux (phases 7 et 4).
3. Espace : stockage partagé de station, grande station de ville, astéroïdes (phase 8).
4. Bêta publique.

## Validation avant chaque déploiement

- `tools/check_assets.py` passe (ressources complètes) et `tools/scenario_test.sh` aussi (il inclut le test de démarrage).
- Le pack client est publié si le client change (nouveaux objets, écrans, paquets).
- Après la release : `python3 tools/sync_client_mods.py` met à jour `mods/` et `TerraCraft-client.mrpack` du dépôt, puis commit.
- `docs/avancement.md` et cette feuille de route sont à jour.
- Aucun système ne supprime silencieusement un objet, un crédit, une ville, une station ou un véhicule.
