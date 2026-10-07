# Rejoindre TerraCraft

**Adresse du serveur : `terre1.falixsrv.me`** · Minecraft Java **26.3** avec Fabric · le pack doit avoir la même
version que le serveur (affichée dans la liste des serveurs).

## Méthode 1 — le pack (recommandée, 2 minutes)

1. Installe **[Prism Launcher](https://prismlauncher.org/)** (gratuit, Windows / macOS / Linux) ou **Modrinth App**,
   et connecte ton compte Microsoft.
2. Télécharge le pack : **[TerraCraft-client.mrpack](https://github.com/propann/TerraCraft/raw/main/TerraCraft-client.mrpack)**
   (dans ce dépôt, toujours à jour).
3. Dans le launcher : *Ajouter une instance → Importer* → choisis le fichier → *OK*.
4. Lance l'instance → **Multijoueur** : le serveur **TerraCraft** est déjà dans la liste. Clique, c'est parti.

Le pack installe tout seul Fabric, les mods (dont les cartes Xaero) et le shader. Sodium, Iris, Distant Horizons et le
shader sont proposés en option : décoche-les si ton PC est modeste.

## Méthode 2 — launcher Minecraft officiel

- **Windows** : télécharge [`installer/install-windows.ps1`](installer/install-windows.ps1), clic droit →
  *Exécuter avec PowerShell*.
- **Linux / macOS** : `curl -fsSL https://raw.githubusercontent.com/propann/TerraCraft/main/installer/install-linux.sh | bash`

L'installeur pose Fabric, met tes anciens mods de côté, installe ceux de TerraCraft et ajoute le serveur à ta liste
Multijoueur si tu n'en as pas encore. Choisis ensuite le profil **fabric-loader-26.3** dans le launcher.

## Méthode 3 — à la main

Le dossier [`mods/`](mods/LISEZMOI.md) contient les mods du client ; son `LISEZMOI.md` explique les trois étapes
(Fabric, script pour les cartes Xaero, copie des `.jar`). Ajoute ensuite le serveur `terre1.falixsrv.me`.

## Après une mise à jour du serveur

Réimporte le pack (méthode 1) ou relance l'installeur (méthode 2). **Connexion refusée** avec « mods différents » ou
« registres » : ton pack n'a pas la version du serveur.

## Une fois en jeu

Choisis ton point de départ sur la carte du monde réel, puis touche **O** pour le menu (carte, missions, marché,
carte des étoiles…), **J** pour la combinaison spatiale, `/aide` pour le reste. Un souci : `/signaler <message>`.
