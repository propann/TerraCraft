# TerraCraft Geo — mod Fabric

Mod client + serveur (Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0, Java 25) :

1. **Générateur `terracraft_geo:earth`** : le monde Minecraft est une carte Web Mercator de la Terre. Chaque colonne suit l'altitude réelle (tuiles AWS Terrain Tiles / Terrarium), avec océans et bathymétrie, et des biomes déduits de la latitude et de l'altitude.
2. **Carte du monde en jeu** : à la première connexion, le serveur ouvre la carte (tuiles OpenStreetMap). Glisser = déplacer, molette = zoom, clic = choisir, champ de recherche = ville/rue (Nominatim), « Atterrir ici » = confirmer.
3. **Arrivée** : le serveur précharge le relief autour du point, cherche la terre ferme la plus proche, téléporte le joueur et fixe son point de réapparition.

## Compiler et installer

```bash
./build-mod.sh                                   # serveur-local/mods/
CLIENT_MODS_DIR=/chemin/vers/minecraft/mods ./build-mod.sh   # + client
```

Le client a besoin de Fabric Loader 0.19.5, de Fabric API et de ce mod. Le serveur doit avoir `level-type=terracraft_geo\:earth` dans `server.properties` (un monde existant ne change pas de générateur : il faut un nouveau `level-name`). En solo, le type de monde « TerraCraft : Terre réelle » apparaît dans l'écran de création.

## Commandes

- `/terracraft ou` : latitude, longitude et altitude réelle à ta position (aussi visible dans F3).
- `/terracraft depart` (opérateurs) : rouvrir la carte et changer de point de départ.

## Choix techniques

| Sujet | Choix |
|---|---|
| Projection | Web Mercator, bloc (0, 0) = 0°N 0°E, nord = -Z. Échelle 1 : 1 bloc = 1 m à l'équateur, `cos(lat)` m ailleurs (0,66 m à Paris). Réglable avec `scale` dans `data/terracraft_geo/worldgen/world_preset/earth.json`. |
| Hauteur | Mer réelle = y 63. Altitude convertie à la même échelle que l'horizontale, linéaire sur 60 blocs puis compressée en racine carrée (Everest ≈ y 316). Fonds marins compressés jusqu'à y -54. |
| Relief | Tuiles Terrarium zoom 13 (~19 blocs/pixel, interpolation bicubique) + zoom 10 pour la bathymétrie en pleine mer. Cache disque `terracraft-cache/terrarium/` à côté du serveur. |
| Biomes | Provisoire : latitude + altitude + bruit de variété (`EarthTerrain.zone`). |
| Sécurité | Le serveur n'accepte un point que d'un joueur invité à choisir (première connexion ou `/terracraft depart`). |

## Limites connues (prochaines étapes)

- Pas encore de routes, bâtiments, rivières ni lacs : il faut importer OSM (Overpass) par zone.
- Les biomes ne reflètent pas l'occupation réelle du sol (ESA WorldCover envisagé).
- Le relief des montagnes est fortement écrasé par la limite de hauteur 384 blocs.
- Les tuiles OSM publiques conviennent pour le prototype ; un serveur public devra utiliser son propre serveur de tuiles.

Attributions à afficher : © OpenStreetMap contributors (carte, recherche) ; AWS Terrain Tiles — SRTM, GMTED2010, ETOPO1 (relief).
