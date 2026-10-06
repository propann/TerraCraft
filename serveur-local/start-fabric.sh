#!/usr/bin/env bash
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_JAR="$(find "$SERVER_DIR" -maxdepth 1 -type f -name 'fabric-server-*.jar' -print -quit)"

if ! command -v java >/dev/null 2>&1; then
  echo "Java est requis pour lancer le serveur Fabric."
  echo "Installe un JRE compatible, puis relance ce script."
  exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
if [ "${JAVA_MAJOR:-0}" -lt 25 ]; then
  echo "Ce serveur Minecraft 26.3 nécessite Java 25 ou supérieur."
  echo "Java détecté : ${JAVA_MAJOR:-inconnu}"
  echo "Installe openjdk-25-jre puis relance ce script."
  exit 1
fi

if [ -z "$SERVER_JAR" ]; then
  echo "Aucun serveur Fabric trouvé dans $SERVER_DIR"
  echo "Télécharge le launcher Fabric avant de relancer ce script."
  exit 1
fi

cd "$SERVER_DIR"
exec java -Xms1G -Xmx3G -XX:+UseG1GC -jar "$SERVER_JAR" nogui
