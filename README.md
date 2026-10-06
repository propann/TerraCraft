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

