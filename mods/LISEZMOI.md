# Mods du client TerraCraft

Ce dossier contient les mods à installer sur chaque machine qui rejoint le serveur, alignés sur la dernière version
publiée (voir `LICENCES.md`).

1. Installe **Fabric** pour Minecraft **26.3** (loader **0.19.5**) : https://fabricmc.net/use/installer/
2. Dans ce dossier, lance `telecharger-restants.sh` (Linux / macOS) ou `telecharger-restants.ps1` (Windows) : il
   ajoute les deux cartes Xaero, que leur licence interdit de republier ici.
3. Copie tous les `.jar` dans le dossier `mods` de ton Minecraft (`%APPDATA%\.minecraft\mods` sous Windows,
   `~/.minecraft/mods` sous Linux) après avoir mis de côté les anciens.

Plus simple : le pack `TerraCraft-client.mrpack` (Prism Launcher, Modrinth App) ou l'installeur du dossier
`installer/` font tout cela d'un coup, shader compris.

Ce dossier est régénéré à chaque version par `python3 tools/sync_client_mods.py`.
