# TerraCraft sur Falix

Cette branche `falix` contient uniquement ce que Falix doit copier à la racine du serveur (`/home/container/`) :

- `mods/` : TerraCraft et les mods serveur, sous des **noms fixes** (`terracraft-geo.jar`, `fabric-api.jar`…). Une mise à jour remplace donc le fichier au lieu d'en ajouter un second.
- `config/openpartiesandclaims-server.toml` : claims en mode permissif.

La localisation serveur OPAC reste volontairement `en_us` : la version 0.32.8 ne contient pas de
traduction serveur française. Les messages et commandes TerraCraft sont, eux, en français.

Elle est générée par `deploy/make_falix_branch.sh` depuis `main`. Ne pas la modifier à la main.

## Réglages du panel Falix (une seule fois)

1. **Logiciel** : Fabric, Minecraft **26.3**, loader **0.19.5**.
2. **Java** : **25** (image Java 25 dans les paramètres de démarrage).
3. **RAM** : le maximum de l'offre (8 Go : laisse environ 7 Go au serveur).
4. **GitHub** : dépôt `propann/TerraCraft`, branche **`falix`**, champ « Deploy into » **vide** (racine), « Deploy on every push » activé.
5. **Bedrock / Geyser : désactivé.** Falix installe Geyser-Fabric, qui n'existe pas en 26.3 et empêche le démarrage : supprime `mods/Geyser-Fabric*.jar` (et Floodgate).

## `server.properties` : rien d'obligatoire

- Le préréglage **Défaut** (`level-type=minecraft:normal`) est remplacé par la Terre post-apocalyptique : un monde neuf est automatiquement TerraCraft. Si un monde `world/` existait avant l'installation du mod, supprime-le une fois.
- Pour une bêta cohérente, utiliser `gamemode=survival`, `force-gamemode=true`, `difficulty=normal`, `online-mode=true` et `white-list=true`. Ouvrir la whitelist seulement après un test de connexion avec plusieurs joueurs.
- Le mod active `allow-flight` au démarrage, pour éviter les expulsions sur la Lune, en fusée ou pendant la chute d'arrivée.
  Le contrôle anti-vol vanilla est donc remplacé par celui de TerraCraft : un joueur (ou sa voiture) qui plane plus de 3 s sur Terre est ramené au sol, les opérateurs connectés sont prévenus et la console affiche `[ANTITRICHE]`. Pas d'expulsion automatique.
- Journal des transactions dans la console : `[HDV]`, `[ECO]`, `[MISSION]`. Mesures de performance : `/spark tps`, `/spark health`.
- **Administrateurs** : `ops.json` est fourni par cette branche et réécrit à chaque déploiement. Pour ajouter un admin, modifie `deploy/ops.json` dans `main`.
- Optionnel : `max-tick-time=300000` (plus de marge pendant les premiers téléchargements) et `view-distance=8` si le serveur n'a que 4 Go.

## Le serveur a besoin d'internet

Le monde est généré à la demande depuis :

- relief : AWS Terrain Tiles ;
- villes : OpenFreeMap ;
- météo : Open-Meteo.

Les données sont mises en cache dans `terracraft-cache/` à la racine du serveur. Ne supprime pas ce dossier : il évite de tout retélécharger.

## Joueurs

Chaque joueur importe le pack client `TerraCraft-client.mrpack` (publié dans les Releases GitHub) dans Prism Launcher : *Ajouter une instance → Importer*.

## Sauvegardes du monde

TerraCraft sauvegarde le monde tout seul (aucun mod de sauvegarde n'existe en 26.3) :

- toutes les **6 heures** (première sauvegarde 30 min après le démarrage), dans `backups/` à la racine du serveur ;
- les **3** archives les plus récentes sont gardées ; les plus anciennes sont supprimées ;
- la sauvegarde est refusée si l'espace disque libre est inférieur à la taille du monde ;
- réglages dans `config/terracraft-backup.json` (`enabled`, `intervalMinutes`, `firstDelayMinutes`, `keep`, `directory`).

Commandes (opérateurs) : `/terracraft sauvegarde` pour sauvegarder tout de suite, `/terracraft sauvegarde liste` pour voir les archives. La console affiche `[SAUVEGARDE]`.

Pense aussi aux sauvegardes du panel Falix : une copie hors du serveur protège contre la perte du serveur lui-même.

### Restaurer une sauvegarde

1. **Arrête** le serveur.
2. Renomme le dossier du monde (par exemple `world` → `world-avant-restauration`) : ne le supprime qu'une fois la restauration vérifiée.
3. Décompresse l'archive choisie de `backups/` **à la racine du serveur** : elle recrée le dossier du monde avec son nom d'origine.
4. Redémarre et vérifie le monde, les comptes (`/argent`) et les annonces (`/hdv`).

Procédure testée le 7 octobre 2026 : sauvegarde, rotation, archive intègre (`unzip -t`), restauration et redémarrage sans erreur.

