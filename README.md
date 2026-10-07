# TerraCraft

Serveur Minecraft survie sur la **Terre réelle** après la chute de l'humanité : on choisit sa ville
sur une carte du monde, on explore des ruines bâties d'après les vraies rues et les vrais immeubles,
on commerce, on répare des véhicules… et on part vers la Lune, l'orbite puis Mars.

**Minecraft Java 26.3 · Fabric 0.19.5 · mod `terracraft-geo`** — tout se fait en jeu, en français.

## Jouer sur le serveur

1. Télécharge le pack client : **[TerraCraft-client.mrpack](https://github.com/propann/TerraCraft/releases/latest/download/TerraCraft-client.mrpack)**
   (ce lien donne toujours la dernière version).
2. Dans **Prism Launcher** ou **Modrinth App** : *Ajouter une instance → Importer* → choisis le fichier.
3. Lance l'instance et connecte-toi au serveur.

Launcher Minecraft officiel : installeurs Windows et Linux dans [`installer/`](installer/README.md).

**Le pack doit avoir la même version que le serveur.** Après une mise à jour du serveur, réimporte le
pack (ou relance l'installeur). Toutes les versions : [Releases](https://github.com/propann/TerraCraft/releases).

### Touches et commandes

| Touche | Rôle |
|---|---|
| `O` | Menu TerraCraft (carte, hôtel des ventes, missions, fiche, combinaison…) |
| `J` | Combinaison spatiale (aussi dans l'inventaire `E`) : casque, combinaison, bottes, jetpack, oxygène |
| `K` | Fiche de personnage : paliers, compétences, découvertes |
| `M` / `'` | Carte Xaero et claims |

`/aide` liste les commandes, `/tuto` affiche l'objectif « Premiers pas » en cours.

## Ce qui est en place (0.7.0)

- **Monde** : relief réel, climats de Köppen, routes, eau, bâtiments OSM/Overture aménagés, carte de départ en jeu.
- **Survie** : inventaire conservé à la mort (−25 % d'expérience), `/sethome` `/home` `/back` `/tpa` (refusés en combat).
- **Accueil** : parcours « Premiers pas » en 6 étapes récompensées, carnet de survie.
- **Économie** : 1 000 crédits au départ, hôtel des ventes par catégories avec prix moyens, comptoir (`/comptoir`), missions.
- **Équipement** : armes à chargeur, véhicules (voiture, camion, moto) avec propriétaire, coffre et partage, avion, jetpack.
- **Espace** : fusée, orbite, Lune, Mars, combinaison spatiale dessinée comme une armure, oxygène automatique.
- **Claims** : Open Parties and Claims ; cartes Xaero.
- **Exploitation** : sauvegardes automatiques, anti-triche vol, journal `[HDV]` `[ECO]` `[MISSION]` `[ANTITRICHE]`, spark.

Suite du projet : [`docs/feuille-de-route.md`](docs/feuille-de-route.md) · état détaillé : [`docs/avancement.md`](docs/avancement.md).

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
2. Pousser sur `main` : la CI compile, **démarre un vrai serveur** (`tools/smoke_server.sh`) puis met à
   jour la branche **`falix`** (copiée par Falix à la racine du serveur ; redémarrer le serveur ensuite).
3. Si le client change (objets, écrans, réseau), pousser un tag `vX.Y.Z` : la CI publie une release avec le jar
   et `TerraCraft-client.mrpack`, que le lien de téléchargement ci-dessus suit automatiquement.

Mods tiers : versions figées dans `tools/mods.lock.json`, identiques sur le serveur et dans le pack.
Pour les mettre à jour : `python3 tools/install_mods.py --update-lock`, tester, puis commiter.
