# Rejoindre TerraCraft

**Adresse du serveur : `terre1.falixsrv.me`** · Minecraft Java **26.3** avec Fabric · le pack doit avoir la même
version que le serveur (affichée dans la liste des serveurs).

## Méthode 1 — le pack (recommandée, 2 minutes)

1. Installe **[Prism Launcher](https://prismlauncher.org/)** (gratuit, Windows / macOS / Linux) ou **Modrinth App**,
   et connecte ton compte Microsoft.
2. Télécharge la dernière version publiée : **[TerraCraft-client.mrpack](https://github.com/propann/TerraCraft/releases/latest/download/TerraCraft-client.mrpack)**.
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

## Première entrée dans le monde

1. Depuis **Multijoueur**, rejoins TerraCraft avec l’instance et la version du pack.
2. À ta première connexion, attends l’ouverture de la carte. Tu restes spectateur jusqu’à la confirmation de ton point de départ.
3. Zoome vers la région, la ville ou le quartier souhaité, clique sur un point puis confirme.
4. Reste connecté pendant la préparation du relief, des rues et des bâtiments. Le serveur cherche un emplacement praticable et te dépose en chute lente.
5. Tu reçois ton kit de départ et ton journal TERRA EXODUS. Le lieu choisi devient ton point de réapparition.

Le choix de départ ne se présente qu’à la première connexion. Si la carte n’apparaît pas, vérifie que tu as lancé le pack TerraCraft avec Fabric, et non Minecraft vanilla.

## Tes premiers pas

- `/tuto` : les premières étapes de survie.
- `/exodus` : objectifs de campagne ; `/exodus livre` : récupérer ou actualiser le journal.
- `O` : menu TerraCraft et catalogue ; `J` : combinaison spatiale.
- `/aide depart` : parcours d’arrivée ; `/aide` : aide par thème.
- `/regles` : règles ; `/signaler <message>` : signaler un souci.

Le parcours détaillé est dans [docs/entree-en-jeu.md](docs/entree-en-jeu.md).
