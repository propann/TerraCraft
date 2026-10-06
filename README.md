# Projet serveur Minecraft

Projet d'étude et de prototypage local : comparer les grands serveurs Minecraft, identifier ce qui les rend intéressants, puis construire notre propre expérience jouable en local.

## Dossiers

- `etude/` : comparaison des serveurs existants et idées retenues.
- `serveur-local/` : fichiers de notre serveur de test.
- `docs/` : décisions, architecture et feuille de route.

## Première direction

Nous cherchons un serveur original, lisible et amusant, avec une identité forte plutôt qu'une copie d'un serveur connu. Nous comparerons les mécaniques qui fonctionnent ailleurs, puis nous combinerons uniquement celles qui servent notre concept.

Le serveur sera d'abord testé en local. Un déploiement public ne sera envisagé qu'après un prototype stable, amusant et testé avec plusieurs joueurs.

## Plateforme choisie

Le projet cible **Minecraft Java Edition sur PC** avec un serveur **Fabric modifiable**. Le vanilla servira uniquement de base de test ; la version évolutive intégrera notre mod de sélection géographique, d'import de données et de génération du monde.

**Point d'entrée unique : Minecraft.** Le joueur ne devra pas ouvrir une page externe. La carte mondiale, le zoom, le choix de la rue et la confirmation du point d'arrivée seront affichés dans une interface Fabric directement en jeu.

## Prochaine étape

Voir `geo-mod/README.md` pour l'état du mod et `docs/feuille-de-route.md` pour la suite (import OSM : routes, bâtiments, eau).


## Joueurs : installer le jeu

Voir `installer/README.md` : installeurs Windows et Linux pour le launcher officiel (Fabric + mods de la dernière release), ou import du `.mrpack` dans Prism ou Modrinth App.

## Règles de survie

On garde son inventaire en mourant, mais on perd 25 % de son expérience. Téléportation : `/tpa <joueur>` (puis `/tpaccept` ou `/tpdeny`), `/sethome` et `/home`, `/back` (lieu de la mort).

## Économie et hôtel des ventes

Chaque joueur commence avec 1 000 crédits. `/argent` affiche le solde. Pour vendre une ressource,
tiens-la en main et utilise `/hdv vendre <prix>` ; `/hdv` affiche les annonces avec des boutons
cliquables pour acheter ou retirer. Les comptes et annonces sont persistants dans le dossier du monde.

## Boucle de jeu retenue

1. Choisir une ville réelle sur la carte et sécuriser un premier abri.
2. Explorer les bâtiments, récupérer des ressources et vendre le surplus à l'hôtel des ventes.
3. Former un groupe, revendiquer son secteur et améliorer voiture, équipement électrique et base.
4. Fabriquer une combinaison chargée, une fusée et partir vers la Lune puis l'orbite.

Chaque fonctionnalité doit renforcer cette boucle : accueil immédiat, objectifs courts, progression
visible et commandes compréhensibles en français.

## Déploiement (Falix)

- **Automatique** : à chaque push sur `main`, GitHub Actions compile le mod et met à jour la branche `falix` (`.github/workflows/build.yml`). Un tag `vX.Y.Z` publie une release avec le jar et le pack client.

- `deploy/make_falix_branch.sh` compile le mod et met à jour la branche **`falix`**, qui ne contient que `mods/` (noms de fichiers fixes) et `config/`. Falix la copie à la racine du serveur à chaque push.
- Réglages du panel et lignes de `server.properties` : voir `deploy/FALIX.md`.
- Pack client pour Prism : `python3 tools/make_mrpack.py geo-mod/build/libs/terracraft-geo-<version>.jar dist/TerraCraft-client.mrpack`, publié dans les Releases GitHub.
