# Entrée en jeu : choix du point de départ

## Règle principale

Minecraft est le seul point d'entrée. Aucun navigateur ni formulaire externe ne sera nécessaire pour jouer.

## Parcours joueur

```text
Lancement du client Minecraft Fabric
  → connexion au serveur
  → écran « Choisir mon point de départ »
  → carte mondiale interactive
  → zoom vers pays / ville / rue
  → clic sur une position
  → aperçu de la zone et météo
  → confirmation
  → import des données
  → génération des chunks
  → arrivée du joueur dans le monde
```

## Architecture technique

- Un mod Fabric client ouvre une `Screen` Minecraft dédiée.
- La carte est rendue dans cette interface ; les contrôles de zoom et de sélection restent utilisables à la souris.
- Le client envoie au serveur uniquement le point confirmé et les paramètres nécessaires.
- Le serveur valide la position, récupère ou lit le cache cartographique, demande la météo et lance la génération.
- La page web actuelle reste un outil de test local pour vérifier les API ; elle ne doit pas être requise par le joueur.

## État actuel (6 octobre 2026)

Implémenté dans `geo-mod/` : le serveur ouvre la carte à la première connexion (joueur en spectateur en attendant), le joueur cherche ou clique un lieu, le serveur précharge le relief, cherche la terre ferme la plus proche et le téléporte. Le choix est enregistré dans `<monde>/terracraft_geo/start_points.json`. Reste à faire : aperçu météo avant confirmation, routes et bâtiments OSM.
