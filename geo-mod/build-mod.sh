#!/usr/bin/env bash
# Compile le mod puis l'installe, avec Fabric API, dans serveur-local/mods/.
# Pour installer aussi côté client : CLIENT_MODS_DIR=/chemin/vers/.minecraft/mods ./build-mod.sh
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
if ! command -v javac >/dev/null 2>&1; then
  echo "Le JDK est requis pour compiler le mod : javac est absent."
  echo "Installe openjdk-25-jdk puis relance ce script."
  exit 1
fi
# Le wrapper télécharge Gradle (version et somme SHA-256 figées dans gradle/wrapper/) au premier lancement.
./gradlew --no-daemon -Djava.net.preferIPv4Stack=true build

FABRIC_API_VERSION="$(sed -n 's/^fabric_api_version=//p' gradle.properties)"
FABRIC_API_JAR="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/${FABRIC_API_VERSION}" \
  -name "fabric-api-${FABRIC_API_VERSION}.jar" -print -quit 2>/dev/null || true)"
MOD_VERSION="$(sed -n 's/^mod_version=//p' gradle.properties)"
MOD_JAR="build/libs/terracraft-geo-${MOD_VERSION}.jar"

install_into() {
  local target="$1"
  mkdir -p "$target"
  # Le serveur Falix utilise parfois le nom fixe terracraft-geo.jar ; le supprimer
  # aussi évite que deux copies du même mod (et deux entrypoints client) cohabitent.
  rm -f "$target"/terracraft-geo-*.jar "$target"/terracraft-geo.jar
  cp "$MOD_JAR" "$target/"
  if [ -n "$FABRIC_API_JAR" ] && ! ls "$target"/fabric-api-*.jar >/dev/null 2>&1; then
    cp "$FABRIC_API_JAR" "$target/"
  fi
  echo "Mods installés dans $target"
}

install_into ../serveur-local/mods
if [ -n "${CLIENT_MODS_DIR:-}" ]; then
  install_into "$CLIENT_MODS_DIR"
fi
