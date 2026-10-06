#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST="$(mktemp)"
VERSION="$(mktemp)"
trap 'rm -f "$MANIFEST" "$VERSION"' EXIT

command -v curl >/dev/null 2>&1 || { echo "curl est requis."; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 est requis."; exit 1; }

echo "Récupération du manifeste officiel Mojang..."
curl -fL --retry 3 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json -o "$MANIFEST"
VERSION_URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versions"][0]["url"])' "$MANIFEST")"
curl -fL --retry 3 "$VERSION_URL" -o "$VERSION"
SERVER_URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["downloads"]["server"]["url"])' "$VERSION")"
VERSION_NAME="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$VERSION")"

echo "Téléchargement du serveur Java officiel ${VERSION_NAME}..."
curl -fL --retry 3 "$SERVER_URL" -o "${SERVER_DIR}/minecraft_server.jar.part"
mv "${SERVER_DIR}/minecraft_server.jar.part" "${SERVER_DIR}/minecraft_server.jar"
echo "Serveur installé dans ${SERVER_DIR}/minecraft_server.jar"
echo "L'EULA n'a pas été acceptée automatiquement. Lance start-vanilla.sh pour créer eula.txt."
