# Serveur local

Ce dossier accueillera le serveur de développement, ses mondes, sa configuration et ses plugins ou mods.

## Serveur évolutif Fabric

Le serveur final sera basé sur Fabric côté client et serveur. Cette base permettra de faire évoluer le projet avec nos propres mods : sélection d'une zone, génération cartographique, météo, permissions, sauvegardes et véhicules.

Le launcher Fabric actuellement présent est `fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar`. Pour lancer le serveur :

```bash
./start-fabric.sh
```

Le prototype web de sélection est conservé comme outil de diagnostic uniquement. Il ne fait pas partie du parcours joueur final.

Pour tester séparément le prototype de données et de météo :

```bash
./start-map.sh
```

Ouvre ensuite `http://localhost:8080`, clique sur un point et confirme. Le choix et la météo sont écrits dans `data/selection.json`. L'étape suivante déplacera ce flux dans l'écran Minecraft Fabric.

Le script `fetch-vanilla.sh` reste disponible pour obtenir une base officielle de comparaison. Il ne représente pas encore le serveur final et ne crée ni n'accepte automatiquement l'EULA.

## Ordre d'évolution

1. serveur Fabric local et connexion avec un monde vide ;
2. mod de sélection du point de départ ;
3. génération d'une zone choisie ;
4. import relief, routes et météo ;
5. véhicules et gameplay ;
6. tests de stabilité et déploiement progressif.

## Règles de développement

- Ne pas mettre de secrets, comptes ou fichiers de production dans le dépôt.
- Faire des sauvegardes avant toute modification de monde.
- Documenter la version de Minecraft, le logiciel serveur et chaque extension installée.
- Tester d'abord en local avant toute ouverture à d'autres joueurs.

La plateforme retenue est Minecraft Java Edition sur PC avec Fabric côté client et serveur. Le mod de localisation sera séparé du monde et désactivable à tout moment.
