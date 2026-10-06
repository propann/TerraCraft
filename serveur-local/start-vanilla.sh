#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_JAR="${SERVER_DIR}/minecraft_server.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "Java est requis pour lancer Minecraft Java Edition."
  echo "Installe un JRE compatible, puis relance ce script."
  exit 1
fi

if [ ! -f "$SERVER_JAR" ]; then
  echo "Fichier minecraft_server.jar absent dans $SERVER_DIR"
  echo "Télécharge le serveur officiel depuis https://www.minecraft.net/download/server"
  exit 1
fi

if [ ! -f "${SERVER_DIR}/eula.txt" ]; then
  echo "Premier lancement : Minecraft va créer eula.txt."
  echo "Lis l'EULA officielle, puis confirme-la manuellement avant de relancer."
fi

cd "$SERVER_DIR"
exec java -Xms2G -Xmx4G -jar "$SERVER_JAR" nogui
