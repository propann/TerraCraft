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

## Parcours complet (0.13, 7 octobre 2026)

1. **Liste des serveurs** : icône TerraCraft (installée par le mod si le serveur n'en a pas) et MOTD sur deux lignes
   avec la version. Un MOTD personnalisé est respecté.
2. **Connexion d'un nouveau joueur** : message de bienvenue, carte du monde ouverte (spectateur en attendant), recherche
   ou clic sur un lieu ; le serveur précharge le relief, les rues et les bâtiments, cherche la terre ferme hors des
   bâtiments et largue le joueur (chute lente), avec le titre « Jour 1 ».
3. **Kit de départ** : pain, torches, épée en pierre, boussole, carnet de survie (touches, véhicules, armes, espace,
   stations, plans).
4. **Premiers pas** : six objectifs récompensés affichés à droite de l'écran (`/tuto`).
5. **Connexions suivantes** : titre « Bon retour », solde, contrats du jour, ville, métier, nouveautés depuis la
   dernière version vue, boutons Missions / Ma ville / Marché / Aide.
6. **Liste des joueurs (Tab)** : en-tête TerraCraft, joueurs en ligne, version, raccourcis.

Le choix du point de départ est enregistré dans `<monde>/terracraft_geo/start_points.json`. Reste à faire : aperçu
météo avant confirmation.
