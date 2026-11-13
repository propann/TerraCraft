# Installer les mods client

Les fichiers `.jar` ne sont plus stockés dans Git. Le pack et l'installeur sont les chemins recommandés :
- Importer le pack `TerraCraft-client.mrpack` de la dernière release dans Prism Launcher ou Modrinth App.
- Ou lancer l'installeur du dossier `installer/` pour le launcher Minecraft officiel.

Pour préparer manuellement ce dossier depuis un clone du dépôt, avec Python 3 et une connexion Internet :

1. Depuis la racine du dépôt, exécute `python3 tools/sync_client_mods.py`. Le script récupère le pack de la dernière release et télécharge les mods redistribuables avec vérification SHA-512.
2. Dans ce dossier, lance `./mods/telecharger-restants.sh` (Linux/macOS) ou `./mods/telecharger-restants.ps1` (Windows). Ces scripts récupèrent les deux cartes Xaero directement depuis Modrinth ; elles ne sont pas redistribuées par le dépôt.
3. Copie les `.jar` obtenus dans le dossier `mods` de l'instance Fabric 26.3, loader 0.19.5.

Le dossier local ainsi créé correspond à la release téléchargée. Répète la procédure après une mise à jour du serveur. Les versions et empreintes SHA-512 sont tenues à jour par `tools/mods.lock.json` et `tools/sync_client_mods.py`.
