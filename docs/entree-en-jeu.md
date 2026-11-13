# Bien démarrer sur TerraCraft

Version de référence : **0.41.0**. Ce guide décrit le parcours réel, de l’installation du client à la première sortie dans le monde.

## Avant la connexion

Utilise le pack client TerraCraft fourni dans [REJOINDRE.md](../REJOINDRE.md), avec Fabric et la même version que le serveur. Le mod client est nécessaire pour afficher la carte de départ. Sans lui, le serveur le signale et ne peut pas ouvrir l’écran de choix.

## Choisir son point de départ

1. Connecte-toi au serveur. À ta première arrivée, la carte du monde s’ouvre automatiquement. Tu restes en mode spectateur pendant ce choix ; tu n’es pas encore déposé dans le monde.
2. Zoome vers la région, la ville ou le quartier où tu veux commencer. Clique sur le point de départ souhaité.
3. Confirme le point. Le serveur vérifie les coordonnées et prépare le relief, les rues et les bâtiments autour de la zone. Reste connecté pendant ce chargement.
4. Le serveur cherche un emplacement praticable hors de l’eau et des bâtiments, puis te dépose au-dessus du sol en chute lente. Le titre « Jour 1 » marque ton arrivée.
5. Tu reçois le kit de départ : quatre pains, huit torches, une épée en pierre, une boussole et le journal TERRA EXODUS. Le point choisi devient aussi ton point de réapparition.

Le choix est enregistré avec ton compte. Aux connexions suivantes, tu reviens dans le monde au lieu de revoir la carte de départ.

## Tes premiers pas

- `/tuto` : suis les premières étapes de survie.
- `/exodus` : consulte les objectifs de la campagne.
- `/exodus livre` : récupère ou actualise ton journal.
- `/aide depart` : revois les étapes d’arrivée ; `/aide survie` et `/aide exodus` ouvrent les aides correspondantes.
- `O` : ouvre le menu TerraCraft et son catalogue.
- `/regles` : consulte les règles du serveur.

Le journal commence par les consignes de départ, puis révèle ses récits au fil des actes. Les objectifs affichés dépendent de ta progression réelle.

## Si la carte ne s’ouvre pas

Vérifie que tu as lancé l’instance TerraCraft avec Fabric, et non un profil Minecraft vanilla. Le pack client doit correspondre à la version indiquée dans la liste des serveurs. Si le chargement reste bloqué, signale-le avec `/signaler` et précise le lieu choisi.

## Connexions suivantes

Le serveur affiche ton accueil, ton solde, les contrats du jour, ta ville et ton métier, ainsi que les nouveautés depuis ta dernière visite. Le menu `O`, `/missions` et `/aide` restent disponibles à tout moment.

## Référence d’implémentation

Le parcours est géré par `StartPoints`, `Welcome` et `Arrival` dans `geo-mod`. La carte n’est requise que pour un compte sans choix enregistré. La sélection est validée par le serveur ; les coordonnées non sollicitées ou invalides sont refusées.
