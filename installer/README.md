# Installer TerraCraft (joueurs)

Les installeurs prennent toujours la **dernière version publiée** sur GitHub : relance-les à chaque mise à jour du serveur.

## Le plus simple : Prism Launcher ou Modrinth App
Télécharge **[TerraCraft-client.mrpack](https://github.com/propann/TerraCraft/releases/latest/download/TerraCraft-client.mrpack)** (toujours la dernière version), puis : *Ajouter une instance → Importer*. Les mods graphiques (Sodium, Iris, Distant Horizons, shaders) sont proposés en option.

## Launcher Minecraft officiel
- **Windows** : télécharge [`install-windows.ps1`](install-windows.ps1), clic droit → *Exécuter avec PowerShell*.
- **Linux / macOS** : `curl -fsSL https://raw.githubusercontent.com/propann/TerraCraft/main/installer/install-linux.sh | bash`

L'installeur :
1. installe Fabric 26.3 (loader 0.19.5) si besoin ;
2. déplace tes anciens mods dans `mods-avant-terracraft-<date>` ;
3. télécharge les mods requis (sommes SHA-512 vérifiées) et TerraCraft.

Ensuite, choisis le profil **fabric-loader-26.3** dans le launcher et connecte-toi au serveur.

**Connexion refusée (« mods différents » ou « registres ») ?** Ton pack n'a pas la même version que le serveur : réimporte le pack ou relance l'installeur.
