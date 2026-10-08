# TerraCraft

Serveur Minecraft survie sur la **Terre réelle** après la chute de l'humanité : on choisit sa ville
sur une carte du monde, on explore des ruines bâties d'après les vraies rues et les vrais immeubles,
on commerce, on répare des véhicules… et on part vers la Lune, l'orbite puis Mars.

**Minecraft Java 26.3 · Fabric 0.19.5 · mod `terracraft-geo`** — tout se fait en jeu, en français.

## Jouer sur le serveur

**Adresse : `terre1.falixsrv.me`** — guide complet : **[REJOINDRE.md](REJOINDRE.md)**.

1. Télécharge le pack **[TerraCraft-client.mrpack](https://github.com/propann/TerraCraft/raw/main/TerraCraft-client.mrpack)**
   (dans ce dépôt, toujours à jour).
2. Dans **Prism Launcher** ou **Modrinth App** : *Ajouter une instance → Importer* → choisis le fichier.
3. Lance l'instance → **Multijoueur** : le serveur TerraCraft est déjà dans la liste.

Launcher Minecraft officiel : installeurs Windows et Linux dans [`installer/`](installer/README.md). Installation à la
main : dossier [`mods/`](mods/LISEZMOI.md). **Le pack doit avoir la même version que le serveur** : après une mise à
jour, réimporte-le. Toutes les versions : [Releases](https://github.com/propann/TerraCraft/releases).

## Règles

1. Respect : pas d'insultes, de harcèlement ni de propos haineux.
2. PvE partout : on ne combat d'autres joueurs que dans les zones PvP (`/pvp`).
3. Pas de vol ni de destruction sur le terrain d'autrui ; protège le tien avec les claims (touche `M`).
4. Pas de triche : clients modifiés, X-ray, exploitation de bugs ou duplication. Un bug ? `/signaler`.
5. Pas de constructions offensantes, ni de lag volontaire (machines géantes, spam d'entités).
6. Les décisions des modérateurs s'appliquent ; contestation calme via `/signaler`.

Les mêmes règles s'affichent en jeu à la première connexion et avec `/regles`.

## Ce qui est en place (0.37.0)

- **Monde** : la Terre réelle (relief, climats de Köppen, routes, eau, bâtiments OSM/Overture aménagés), météo réelle,
  jour et nuit normaux ; ruines, bunkers, caves, épaves ; lieux réels (`/lieux` : hôpitaux, gares, commissariats…)
  avec un butin à leur image.
- **Entrée** : pack à importer (serveur déjà dans la liste), carte de départ en jeu, règles, carnet de survie, parcours
  « Premiers pas », accueil des habitués avec les nouveautés.
- **Interface** : menu `O` en tuiles, fiche `K`, aide par thèmes (`/aide`), barres de vie, chat soigné, ville en
  préfixe des pseudos, une seule palette pour toutes les fenêtres.
- **Survie et danger** : inventaire conservé à la mort, `/home` `/back` `/tpa` ; convois militaires, zones
  contaminées, boss de bunker, vagues nocturnes sur les villes, ravitaillements militaires.
- **Combat** : PvE partout, PvP seulement dans les zones déclarées (`/pvp`), primes sur les joueurs, armes à chargeur.
- **Économie** : 1 000 crédits au départ, hôtel des ventes, comptoir, étals de joueurs (`/etal`), missions, contrats
  du jour, métiers et réputation (offres réservées), confirmations avant les actions coûteuses.
- **Villes** : maire, adjoints, trésorerie, stock commun (terminal logistique, de la Terre à la Lune), territoire étendu
  par les claims des habitants ; claims Open Parties and
  Claims, cartes Xaero.
- **Véhicules** : voiture, camion, moto, avion, rover lunaire, jetpack ; tableau de bord (vitesse, carburant, autonomie).
- **Industrie du carburant** : gisements de pétrole, pompes, panneaux solaires, câbles, tuyaux, raffinerie, réservoirs,
  pompe à essence, batteries, groupe électrogène, lampes électriques, serre hydroponique ; stations-service en ruine aux vraies adresses ; bidons à remplir et à rendre.
- **Espace** : station orbitale d'abord, fusées à 1-4 réservoirs, carte des étoiles, orbite lunaire, Lune, Mars, ceinture d'astéroïdes ;
  bases en kit, protégées autour de leur balise ; plans et atelier de station ; sous-sol lunaire (cavernes,
  sanctuaires, pyramides), micrométéorites ; combinaison spatiale ; course à l'espace (`/course`).
- **Exploitation** : sauvegardes automatiques, modérateurs (`/mod`), suivi (`/terracraft suivi`), mesures de
  génération, journaux par thème, anti-triche vol, spark.

## Documentation

| Document | Contenu |
|---|---|
| [`docs/commandes.md`](docs/commandes.md) | Toutes les touches et commandes (joueurs et administrateurs), journaux, fichiers de données |
| [`docs/etat-du-projet.md`](docs/etat-du-projet.md) | Analyse : ce que nous avons, niveau de vérification, risques, priorités |
| [`docs/feuille-de-route.md`](docs/feuille-de-route.md) | Phases, ce qui est fait, nos intentions pour la suite |
| [`docs/avancement.md`](docs/avancement.md) | Liste de contrôle de la version en cours et tests restants |
| [`docs/entree-en-jeu.md`](docs/entree-en-jeu.md) | Parcours d'un joueur, de la liste des serveurs aux premiers pas |
| [`docs/architecture-systeme-solaire.md`](docs/architecture-systeme-solaire.md) | Espace : destinations, carburant, stations, plans |
| [`docs/localisation-et-monde.md`](docs/localisation-et-monde.md) | Génération de la Terre réelle |
| [`deploy/FALIX.md`](deploy/FALIX.md) | Hébergement Falix, sauvegardes et restauration |

## Développement

| Dossier | Contenu |
|---|---|
| `geo-mod/` | Le mod Fabric (serveur + client) |
| `serveur-local/` | Serveur de test local (`start-fabric.sh`) |
| `tools/` | Mods tiers (`mods.txt`, versions figées dans `mods.lock.json`), pack client, textures, test de démarrage |
| `deploy/` | Déploiement Falix ([`FALIX.md`](deploy/FALIX.md) : réglages, sauvegardes, restauration) |
| `installer/` | Installeurs joueurs |
| `docs/`, `etude/` | Décisions, architecture, feuille de route, étude des serveurs existants |

Compiler et installer en local : `geo-mod/build-mod.sh` (avec `CLIENT_MODS_DIR=…` pour une instance client).

### Publier une version

1. Monter `mod_version` dans `geo-mod/gradle.properties`.
2. Tester : `tools/scenario_test.sh <jar>` joue une partie avec deux faux joueurs (Carpet, test uniquement) : 30 vérifications (villes, économie, espace, liste des serveurs). `tools/ping_server.py` montre ce que voit la liste des serveurs.
3. Pousser sur `main` : la CI compile, **démarre un vrai serveur** (`tools/smoke_server.sh`) puis met à
   jour la branche **`falix`** (copiée par Falix à la racine du serveur ; redémarrer le serveur ensuite).
4. Si le client change (objets, écrans, réseau), pousser un tag `vX.Y.Z` : la CI publie une release avec le jar
   et `TerraCraft-client.mrpack`, que le lien de téléchargement ci-dessus suit automatiquement.

Mods tiers : versions figées dans `tools/mods.lock.json`, identiques sur le serveur et dans le pack.
Pour les mettre à jour : `python3 tools/install_mods.py --update-lock`, tester, puis commiter.
