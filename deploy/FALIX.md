# TerraCraft sur Falix

Cette branche `falix` contient uniquement ce que Falix doit copier à la racine du serveur (`/home/container/`) :

- `mods/` : TerraCraft et les mods serveur, sous des **noms fixes** (`terracraft-geo.jar`, `fabric-api.jar`…). Une mise à jour remplace donc le fichier au lieu d'en ajouter un second.
- `config/openpartiesandclaims-server.toml` : claims en mode permissif.

Elle est générée par `deploy/make_falix_branch.sh` depuis `main`. Ne pas la modifier à la main.

## Réglages du panel Falix (une seule fois)

1. **Logiciel** : Fabric, Minecraft **26.3**, loader **0.19.5**.
2. **Java** : **25** (image Java 25 dans les paramètres de démarrage).
3. **RAM** : le maximum de l'offre (8 Go : laisse environ 7 Go au serveur).
4. **GitHub** : dépôt `propann/TerraCraft`, branche **`falix`**, champ « Deploy into » **vide** (racine), « Deploy on every push » activé.

## `server.properties` à modifier (gestionnaire de fichiers Falix)

**Avant le premier démarrage**, ou alors supprime ensuite le dossier `world/` : le type de monde n'est appliqué qu'à la création. Ne touche pas à `server-port` ni à `server-ip`, c'est Falix qui les gère.

```properties
level-type=terracraft_geo\:earth
gamemode=survival
difficulty=normal
allow-flight=true
max-tick-time=300000
view-distance=10
simulation-distance=8
spawn-protection=0
white-list=true
enforce-whitelist=true
motd=TerraCraft - la Terre apres la chute
```

- `allow-flight=true` évite d'être expulsé pour « vol » avec la gravité lunaire, en fusée ou pendant la chute d'arrivée.
- `max-tick-time=300000` laisse le temps aux premières zones d'être générées (téléchargement du relief et des villes).
- Liste blanche : ajoute tes amis dans la console Falix avec `whitelist add <pseudo>`, et donne-toi les droits avec `op <pseudo>`.

## Le serveur a besoin d'internet

Le monde est généré à la demande depuis :

- relief : AWS Terrain Tiles ;
- villes : OpenFreeMap ;
- météo : Open-Meteo.

Les données sont mises en cache dans `terracraft-cache/` à la racine du serveur. Ne supprime pas ce dossier : il évite de tout retélécharger.

## Joueurs

Chaque joueur importe le pack client `TerraCraft-client.mrpack` (publié dans les Releases GitHub) dans Prism Launcher : *Ajouter une instance → Importer*.
