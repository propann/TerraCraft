# Licences des mods client

Ce répertoire ne versionne plus de fichiers `.jar`. La procédure manuelle télécharge les fichiers depuis Modrinth, suivant les versions figées dans `tools/mods.lock.json`, puis vérifie leurs empreintes SHA-512.

Les licences des fichiers redistribuables sont déclarées dans `tools/sync_client_mods.py` et dans la page de chaque projet Modrinth. Les mods Xaero restent téléchargés directement par le joueur, conformément à leurs conditions de redistribution. Les shaders sont référencés dans le pack client et ne sont pas copiés dans ce répertoire.

Pour reconstruire l'inventaire et son tableau de licences à partir du pack de la dernière release : `python3 tools/sync_client_mods.py`.
