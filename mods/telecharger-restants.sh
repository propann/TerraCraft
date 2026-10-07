#!/usr/bin/env bash
# Télécharge dans ce dossier les mods que leur licence ne permet pas de republier (Xaero), depuis Modrinth,
# avec vérification de l'empreinte SHA-512. Usage : ./telecharger-restants.sh
set -euo pipefail
cd "$(dirname "$0")"
python3 - <<'PY'
import hashlib, json, urllib.request
for mod in json.load(open("a-telecharger.json")):
    request = urllib.request.Request(mod["url"], headers={"User-Agent": "propann/TerraCraft"})
    blob = urllib.request.urlopen(request, timeout=120).read()
    if hashlib.sha512(blob).hexdigest() != mod["sha512"]:
        raise SystemExit("Empreinte incorrecte : " + mod["file"])
    open(mod["file"], "wb").write(blob)
    print("ok", mod["file"])
PY
echo "Dossier complet : copie tous les .jar dans le dossier mods de ton Minecraft (Fabric 26.3, loader 0.19.5)."
