# TerraCraft Geo — mod Fabric

Mod client + serveur (Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0, Java 25) :

1. **Générateur `terracraft_geo:earth`** : le monde Minecraft est une carte Web Mercator de la Terre. Chaque colonne suit l'altitude réelle (tuiles AWS Terrain Tiles / Terrarium), avec océans et bathymétrie, et des biomes déduits de la latitude et de l'altitude.
2. **Carte du monde en jeu** : à la première connexion, le serveur ouvre la carte (tuiles OpenStreetMap). Glisser = déplacer, molette = zoom, clic = choisir, champ de recherche = ville/rue (Nominatim), « Atterrir ici » = confirmer.
3. **Villes réelles** : routes, ponts, rivières, lacs, forêts, parcs et bâtiments (hauteur réelle, étages, fenêtres, portes côté rue, toits en pente ou mansardes, éclairage intérieur) depuis les tuiles vectorielles OpenFreeMap (OpenStreetMap, zoom 14).
4. **Ciel réel** : heure solaire locale (une journée Minecraft = 24 h réelles) et météo Open-Meteo (pluie, neige, orage) à la position du premier joueur connecté.
5. **Lampadaires** le long des routes (rues éclairées, pas de monstres).
   **Arbres urbains** (`UrbanTrees.java`) : parcs et jardins denses (grille de 6 blocs, 65 %), pelouses clairsemées (12 blocs, 25 %), alignements d'avenue à 3 blocs de la chaussée des grands axes (environ tous les 8 blocs, avec une fosse de terre). Ce sont les arbres vanilla, choisis selon le climat (grands chênes sur les avenues tempérées, épicéas au nord, acacias sous les tropiques), placés à l'étape de décoration. Les terrains de sport restent sans arbres.
6. **Ponts** : tablier entre 4 et 12 blocs au-dessus de l'eau (pas calé sur le relief SRTM, gonflé par les toits en ville), rampes de 1 bloc pour 3 vers la route au sol, remblai sous les rampes basses, parapets, piliers tous les 16 blocs.
7. **Mode post-apocalypse** (`"apocalypse": true` dans le préréglage, réglé à la création du monde ; `Apocalypse.java`) : le monde a redémarré sans humains. 10 % des bâtiments sont effondrés en gravats et 30 % ont perdu leurs étages supérieurs (sommets déchiquetés, sans toit). Partout : vitres brisées à 70 %, murs moussus ou troués, lierre sur les façades, toiles d'araignée, planchers troués. Routes fissurées où l'herbe perce, voitures abandonnées, arbres sauvages dans les rues, lampadaires morts. Aucune lumière : les monstres hantent la ville.
8. **Arrivée** : le serveur précharge le relief autour du point, cherche la terre ferme la plus proche, téléporte le joueur et fixe son point de réapparition.

## Compiler et installer

```bash
./build-mod.sh                                   # serveur-local/mods/
CLIENT_MODS_DIR=/chemin/vers/minecraft/mods ./build-mod.sh   # + client
```

Le client a besoin de Fabric Loader 0.19.5, de Fabric API et de ce mod. Le serveur doit avoir `level-type=terracraft_geo\:earth` dans `server.properties` (un monde existant ne change pas de générateur : il faut un nouveau `level-name`). En solo, le type de monde « TerraCraft : Terre réelle » apparaît dans l'écran de création.

## Commandes

- `/terracraft ou` : latitude, longitude et altitude réelle à ta position (aussi visible dans F3).
- `/terracraft depart` (opérateurs) : rouvrir la carte et changer de point de départ.
- `/argent` : afficher son solde en crédits TerraCraft.
- `/hdv` : ouvrir l'hôtel des ventes dans le chat. Tiens un objet en main puis `/hdv vendre <prix>` pour créer une annonce ; les boutons permettent d'acheter ou de retirer une annonce.
- `/hdv page <numero>` : parcourir les pages du marché.
- `/eco donner <joueur> <montant>` (opérateurs) : créditer un joueur pour les récompenses, événements ou tests.

L'économie et les annonces sont sauvegardées côté serveur dans `<monde>/terracraft_geo/balances.json`
et `hotel-des-ventes.json`. Les commandes de vente et d'achat sont accessibles aux joueurs ; la
commande d'administration respecte le niveau OP standard.

## Choix techniques

| Sujet | Choix |
|---|---|
| Projection | Web Mercator, bloc (0, 0) = 0°N 0°E, nord = -Z. Échelle 1 : 1 bloc = 1 m à l'équateur, `cos(lat)` m ailleurs (0,66 m à Paris). Réglable avec `scale` dans `data/terracraft_geo/worldgen/world_preset/earth.json`. |
| Hauteur | Dimension `terracraft_geo:earth` de 1536 blocs (y -64 à 1471). Mer réelle = y 63. Altitude à la même échelle que l'horizontale, **linéaire sur 1200 blocs** (≈ 800 m réels en France : villes et collines à l'échelle exacte), puis compressée en racine carrée (Everest ≈ y 1440). |
| Relief | Tuiles Terrarium zoom 13 (~19 blocs/pixel, interpolation bicubique) + zoom 10 pour la bathymétrie en pleine mer. Cache disque `terracraft-cache/terrarium/` à côté du serveur. |
| Biomes | Climats réels de Köppen-Geiger (Beck et al. 2023, 1991-2020, 0,1°, CC BY 4.0) : `KoppenMap`, données `assets/terracraft_geo/koppen_0p1.bin.gz` générées par `tools/make_koppen.py`. L'altitude (alpages, sommets) et OSM (forêts, parcs, eau) restent prioritaires. |
| Sécurité | Le serveur n'accepte un point que d'un joueur invité à choisir (première connexion ou `/terracraft depart`). |

## Limites connues (prochaines étapes)

- Le type de bâtiment n'est pas dans les tuiles OpenMapTiles : il est deviné (hauteur + emprise). Les styles sont dans `BuildingStyles.java`.
- Overpass a été abandonné (trop lent, erreurs 504/429) au profit d'OpenFreeMap (CDN). Cache : `terracraft-cache/osm/`.
- Au-dessus d'environ 800 m d'altitude réelle, le relief est compressé.
- Les tuiles OSM publiques conviennent pour le prototype ; un serveur public devra utiliser son propre serveur de tuiles.

Attributions à afficher : © OpenStreetMap contributors (carte, recherche) ; AWS Terrain Tiles — SRTM, GMTED2010, ETOPO1 (relief).

## Mods tiers

La liste est dans `tools/mods.txt`. Pour installer ou mettre à jour depuis Modrinth (somme sha512 vérifiée) :

```bash
python3 tools/install_mods.py server serveur-local/mods
python3 tools/install_mods.py client <instance>/minecraft/mods --shaders <instance>/minecraft/shaderpacks
```

- **Client** : Sodium, Iris + Complementary Reimagined, Distant Horizons, cartes Xaero (claims sur la carte avec OPAC), Mod Menu.
- **Serveur** : Lithium, FerriteCore, Krypton, Chunky (`/chunky center <x> <z>`, `/chunky radius 500`, `/chunky start` pour pré-générer une ville).
- Open Parties and Claims 0.32.8 + Forge Config API Port 26.3.1 : claims et groupes. Réglages permissifs dans `serveur-local/config/openpartiesandclaims-server.toml` (2000 chunks par joueur, zone de 21×21 chunks par action).

## Couche spatiale (à venir)

Ni Galacticraft ni Ad Astra n'existent pour 26.3. Galacticraft 5 (TeamGalacticraft, Fabric 1.21.1) est sous licence MIT : code réutilisable avec attribution. Ad Astra : code MIT, mais textures, modèles et sons « All Rights Reserved », donc interdits. Idée : Lune et Mars générées depuis les reliefs réels NASA (LOLA, MOLA) avec le même moteur que la Terre.

## Gameplay post-apocalypse (paquet `content` + `Wasteland.java`)

### Véhicules à assembler
1. Fabriquer un **châssis** (voiture : 5 fer en U ; camion : 8 fer) et le poser au sol (clic droit).
2. Clic droit sur le châssis avec **4 roues**, un **moteur**, un **radiateur** et une **batterie**. Le **turbo** est optionnel (+40 % de vitesse, meilleure accélération).
3. Ajouter un **bidon d'essence** (environ 4 min de conduite ; jusqu'à 4 bidons).
4. Clic droit pour monter, ZQSD pour conduire. Accroupi + clic droit affiche l'état du véhicule. Frapper le véhicule le casse et rend toutes ses pièces.

Voiture : 2 places, rapide. Camion : 4 places, plus lent, tourne moins vite. Les véhicules montent les marches d'un bloc.

| Pièce | Recette |
|---|---|
| Roue ×2 | 4 algues séchées autour d'un lingot de fer |
| Moteur | fer, piston, fer / fer, redstone, fer |
| Radiateur | cuivre, barreaux de fer, cuivre (×2 lignes) |
| Batterie | 2 cuivre + 2 redstone + fer |
| Turbo | 2 fer + cuivre + poudre de blaze |
| Bidon d'essence | fer + 2 charbons |
| Munitions ×8 | cuivre + poudre à canon |

### Armes
Tir instantané, avec un chargeur : la barre de l'objet indique les balles restantes. Le rechargement est automatique quand le chargeur est vide ; accroupi + clic recharge à la demande. Tir à la tête ×1,75, dégâts réduits au-delà de la moitié de la portée, flash au départ du coup et recul de la visée.

| Arme | Dégâts | Portée | Chargeur | Particularité |
|---|---|---|---|---|
| Pistolet | 6 | 40 | 12 | rapide |
| Mitraillette | 3,5 | 30 | 30 | automatique (maintenir le clic) |
| Fusil | 12 | 90 | 8 | précis |
| Fusil de précision | 24 | 160 | 5 | lunette dans la recette |
| Fusil à pompe | 6×3,5 | 20 | 6 | dispersion |
| Grenade | explosion | lancer | — | ne détruit aucun bloc |
| Machette | mêlée | — | — | épée en fer rapide |

### Monde (`"apocalypse": true`)
- **Bâtiments** : coffres « ruine » (nourriture, matériaux, munitions, 40 % de chance de pièce ou d'arme), générateurs de monstres au rez-de-chaussée, zombies postés aux étages.
- **Caves à monstres** : chapelets de salles enterrées (1 zone sur 3 par carré de 192 blocs), avec générateurs, coffre et toiles d'araignée. Elles ont souvent un puits d'accès dans un parc ou un terrain vague.
- **Bunkers** : salle bétonnée de 12×12 à environ 14 blocs sous terre (30 % des carrés de 320 blocs), trappe en bois et échelle, 2 coffres « bunker » (armes, pièces, munitions) et parfois un générateur de zombies.
- **Épaves** : environ 1 chunk de route sur 80 contient une voiture ou un camion incomplet (jamais roulant) à réparer.

Textures générées par `tools/gen_textures.py` (Python pur, déterministe).

## Entrée dans le monde (`Arrival.java`)
Après le choix sur la carte, le joueur est largué 48 blocs au-dessus du point choisi, en chute ralentie. Un titre « Jour 1 » s'affiche ; la première fois, il reçoit un kit (pain, torches, épée en pierre, boussole) et le **carnet de survie** (règles, recettes, véhicules, fusée, Lune). La réapparition se fait au sol.

## L'espace (`Rocket.java`, `Space.java`, `MoonChunkGenerator.java`)
Conception inspirée d'Ad Astra, mais écrite pour ce mod : le code d'Ad Astra vise la 1.21.1 et dépend de bibliothèques absentes en 26.3, et ses textures, modèles et sons sont « All Rights Reserved ».

- **Fusée** : poser la coque, puis clic droit avec le moteur-fusée, le réservoir, le cône et les ailerons, puis 4 carburants de fusée. Monter (clic droit), puis **Espace** : compte à rebours de 3 s, décollage, passage Terre ↔ Lune et descente freinée. Au retour, on atterrit au point de départ sur Terre (mémorisé dans la fusée).
- **Lune** (`terracraft_geo:moon`) : régolithe gris, mers de basalte, cratères de 8 à 150 blocs avec remparts. Ciel noir, sans nuages ni pluie, on ne peut pas y dormir. **Gravité ×0,17**, chutes amorties.
- **Combinaison et oxygène** : le casque-combinaison est obligatoire pour décoller. Sans lui, ou avec sa réserve vide, on suffoque (2 dégâts/s). Il contient 10 min d'air (sa barre de durabilité). Une bouteille d'oxygène (clic droit) recharge 5 min. La plateforme orbitale et les distributeurs fournissent une bulle d'air.
- **Recettes** : coque (fer + blocs de cuivre), moteur-fusée (fer, bloc de redstone, haut fourneau), réservoir (cuivre + seau), cône et ailerons (fer), carburant (bidon d'essence + bloc de charbon + poudre à canon), casque (fer + verre), oxygène (fer, 2 cuivres, algue). On trouve aussi des pièces dans les **bunkers**.
- Commande admin : `/terracraft vehicule voiture|camion` fait apparaître un véhicule complet.

## Lune peuplée et base orbitale
- **Ressources lunaires** :
  - **titane** en filons sous la surface, à fondre en lingots (pioche en fer requise) ;
  - **glace** au fond des grands cratères ;
  - **cristaux d'hélium-3** lumineux en surface (2 à 4 éclats ; 4 éclats = 1 carburant de fusée, pour rentrer) ;
  - **épaves de satellites** avec un coffre (titane, hélium, oxygène, éléments de station).
- **Ennemis lunaires** (la nuit et dans l'ombre) : **Rôdeur lunaire** (araignée grise, rapide, 22 PV) et **Astronaute perdu** (zombie en combinaison, 30 PV, armure 6, ne brûle pas au soleil).
- **Orbite** (`terracraft_geo:orbit`) : le vide, gravité ×0,08, pas d'air. Avant le décollage, accroupi + clic droit (main vide) sur la fusée choisit la destination : Terre, Lune ou orbite. À la première arrivée, une **plateforme d'amarrage** de 9×9 est construite à y=150, avec lampes et distributeur d'oxygène.
- **Blocs de station** (titane) : coque ×4, plancher ×6, hublot ×4 (avec du verre), lampe ×4 (avec de la pierre lumineuse), **distributeur d'oxygène** (titane, bouteilles d'oxygène, bloc de redstone), qui donne une bulle d'air respirable de 8 blocs de rayon.

## Progression et fiche de personnage (`Progression.java`, touche **K**)

**Compétences** (20 niveaux chacune, XP par action ; à la mort, on perd 10 % de la progression du niveau en cours) :

| Compétence | XP | Bonus par niveau |
|---|---|---|
| Combat | monstres tués (5), ennemis lunaires (12) | +2 % de dégâts (armes à feu comprises), rechargement −2 % |
| Exploration | zone (20), bunker (60), cave (40), coffre (6) | +0,5 % de vitesse, +0,1 chance au butin |
| Mécanique | véhicule assemblé (80), 100 blocs conduits (4) | −2 % de consommation d'essence |
| Espace | lancement (100), Lune et orbite (150), titane et hélium (5) | −3 % de consommation d'oxygène |

**Rang** (paliers et récompenses) :
- Points gagnés : nouvelle zone d'environ 10 km (+5), bunker (+25), cave (+15), coffre fouillé (+2), monstre (+1), ennemi lunaire (+3), véhicule assemblé (+20), 100 blocs en véhicule (+1), lancement de fusée (+40), titane ou hélium miné (+2), et 16 découvertes (de +5 à +80).
- **10 paliers** (50, 150, 300, 500, 800, 1200, 1700, 2300, 3000, 4000 pts). Chacun donne une amélioration permanente et du matériel :

| Palier | Amélioration | Récompense |
|---|---|---|
| 1 Endurci | +1 cœur | pistolet + 16 munitions |
| 2 Pieds légers | +5 % de vitesse | châssis de voiture + 4 roues |
| 3 Poumons d'acier | oxygène 2× plus durable | moteur, radiateur, batterie, 2 bidons |
| 4 Coriace | +1 cœur | fusil à pompe + 24 munitions |
| 5 Peau tannée | +2 d'armure | casque spatial + 3 oxygènes |
| 6 Survivant aguerri | +10 % de dégâts | coque, cône, ailerons |
| 7 Cœur vaillant | +1 cœur | moteur-fusée, réservoir, 4 carburants |
| 8 Atterrissage | chutes amorties +4 | 16 coques de station + distributeur |
| 9 Fouineur | +1 chance (butin) | fusil + 32 munitions + turbo |
| 10 Légende des ruines | +2 cœurs | châssis de camion + 4 roues + 5 diamants |

- Données sauvegardées dans `<monde>/terracraft_geo/progression.json`.
- Tests serveur : le mod Carpet (`/player … spawn`) sert de joueur factice, **uniquement sur le serveur de test** (pas dans `tools/mods.txt`).

## Intérieurs des bâtiments (`BuildingInterior.java`)
Échelles dans un coin intérieur sur deux (accès à tous les étages), cloisons blanches sur une grille de 7 blocs avec passages de porte, mobilier (bibliothèques, établis, tables, chaises, chaudrons, plantes, métiers), toits à quatre pans en escaliers orientés, balcons filants aux étages des immeubles.
