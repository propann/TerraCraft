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
- [ ] **Carte neuve sur 10 à 20 points réels** (centre-ville dense, banlieue, zone industrielle, campagne, montagne,
      côte). Intention : un script admin qui téléporte un bot sur une liste de coordonnées, attend la génération et
      relève le temps par chunk, les erreurs de téléchargement et les bâtiments suspects.
- [ ] **Avec et sans cache Overture** : comparer temps et rendu ; documenter quand préparer le cache.
- [ ] **Bâtiments géants, vides ou dupliqués** : rapport de génération (voir phase 7) pour les repérer.
- [ ] **Mesures** : `/spark tps`, `/spark health`, mémoire et taille du monde avec 1, 5 et 10 joueurs ; seuils notés
      dans `deploy/FALIX.md` (distance de vue, mémoire).
- [~] Véhicules, armes, claims, économie, combinaison, avion : vérifiés côté serveur, à valider en séance de jeu.

## Phase 1 — Accueil et interface joueur

- [x] Menu TerraCraft (`O`), `/aide`, carnet de survie, touches affichées.
- [x] Parcours « Premiers pas » (6 étapes récompensées).
- [x] Accueil des habitués : titre, solde, contrats, ville, métier, nouveautés depuis la dernière visite.
- [x] Liste des serveurs (MOTD avec version, icône) et liste des joueurs (Tab).
- [ ] **Uniformiser l'interface** : une seule palette (celle de la combinaison), mêmes en-têtes et boutons pour tous
      les écrans ; reprendre la fiche `K` et la carte de départ dans ce style.
- [ ] **Confirmations** avant les actions coûteuses (fonder une ville, vendre très en dessous du prix moyen, quitter
      une ville en tant que maire) et messages d'erreur qui disent quoi faire ensuite.
- [ ] **Écran « Mon personnage » unifié** (fiche, métier, plans, stations) au lieu de commandes éparses.

## Phase 2 — Claims, villes et groupes

- [x] Villes : maire, habitants, trésorerie, `/ville tp`, titres d'entrée et de sortie.
- [x] Propriétaire du territoire affiché en changeant de zone.
- [ ] **Claims de ville** : les chunks revendiqués par les habitants autour du centre comptent comme ville ; afficher
      « Ville de X » plutôt que le pseudo du propriétaire.
- [ ] **Rôles de ville** (maire, adjoint, habitant) avec droits sur la trésorerie et les invitations.
- [ ] **Protection des véhicules et coffres** dans les claims d'autrui (aujourd'hui : verrou du propriétaire).
- [ ] **Taxe de ville légère** seulement si elle finance un service visible (balise de ville, garage, téléporteur).

## Phase 3 — Économie et commerce

- [x] Hôtel des ventes par catégories, prix moyens, vente depuis l'écran, comptoir, frais 2 %, `/eco stats`.
- [x] Sources : missions, contrats du jour, tutoriel, ventes.
- [ ] **Magasins de joueurs** : un bloc « étal » posé dans son claim, qui vend un objet à prix fixe même hors ligne.
- [ ] **Primes** sur les monstres rares et les événements (pas sur les joueurs avant les règles PvP).
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
- [ ] **Événements** : convoi à attaquer (camion escorté de monstres), zone de contamination (dégâts sans combinaison),
      vague nocturne sur une ville.
- [ ] **Zones PvP explicites** (affichées à l'entrée) et PvE partout ailleurs ; primes seulement en zone PvP.
- [ ] **Boss rares** dans les bunkers profonds, avec butin unique.
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
- [ ] **Bases lunaire et martienne en kit** (charge utile) déployées à l'atterrissage, avec quai et oxygène.
- [ ] **Rover lunaire** (charge utile) : véhicule électrique déposé à l'arrivée, recharge à la base.
- [ ] **Sous-sol lunaire vivant** : cavernes géantes, pyramides et donjons extraterrestres, cristaux, butin alien.
- [ ] **Carte des étoiles** : écran de navigation (destinations, coûts, conditions) à la place du choix au clic.
- [x] Stations en kit (modules pressurisés), balise d'arrivée, `/station`.
- [x] Plans de fusée et atelier de station ; Mars exige la navigation martienne.
- [ ] **Stockage partagé de station** entre habitants d'une même ville.
- [ ] **Épaves et dangers lunaires** : épaves de sondes avec butin, pluies de micrométéorites (abri requis),
      rôdeurs plus forts la nuit lunaire.
- [ ] **Objectifs coopératifs** : grande station de ville (N modules, laboratoire, serre) avec récompense collective.
- [ ] **Astéroïdes** : petites zones instanciées, minage de métaux rares, jetpack obligatoire.
- [ ] **Garder la Terre utile** : ressources terrestres indispensables aux améliorations avancées (commerce Terre ↔ espace).

## Phase 9 — Bêta publique et exploitation

- [x] Journaux des transactions, villes, véhicules, largages, métiers, signalements.
- [x] `/signaler` avec alerte aux opérateurs ; pack client synchronisé, lien stable, versions publiées.
- [ ] **Bêta privée** avec whitelist et un canal de retours.
- [ ] **Règles publiées** (PvP, claims, triche, comportement) dans le README et en jeu.
- [ ] **Permissions** (LuckPerms) : modérateurs sans être opérateurs.
- [ ] **Événement d'ouverture** : course à la première station lunaire.
- [ ] **Suivi** : joueurs actifs, retour à 24 h, missions et contrats remplis, annonces, TPS, erreurs.

## Ordre de développement

1. Séance de test à plusieurs et phase 0 (mesures, carte neuve).
2. Uniformisation de l'interface et confirmations (phase 1).
3. Événements et règles PvP (phase 6).
4. Claims de ville et magasins de joueurs (phases 2 et 3).
5. Génération enrichie et points d'intérêt, puis missions liées aux lieux (phases 7 et 4).
6. Espace : épaves, stockage partagé, station de ville, astéroïdes (phase 8).
7. Bêta publique (phase 9).

## Validation avant chaque déploiement

- `tools/scenario_test.sh` passe (il inclut le test de démarrage).
- Le pack client est publié si le client change (nouveaux objets, écrans, paquets).
- `docs/avancement.md` et cette feuille de route sont à jour.
- Aucun système ne supprime silencieusement un objet, un crédit, une ville, une station ou un véhicule.
