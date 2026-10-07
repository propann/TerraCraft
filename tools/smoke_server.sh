#!/usr/bin/env bash
# Test de démarrage : lance un vrai serveur Fabric avec TerraCraft et les mods serveur figés,
# sur un monde neuf, et échoue si le serveur n'atteint pas « Done » (registres invalides,
# mixin cassé, crash au chargement…). Utilisé par la CI avant tout déploiement Falix.
#
# Usage : tools/smoke_server.sh <terracraft-geo.jar> [dossier de travail]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(realpath "$1")"
WORK="${2:-$(mktemp -d)}"
TIMEOUT="${SMOKE_TIMEOUT:-600}"
MC_VERSION="26.3"
LOADER_VERSION="0.19.5"
INSTALLER_VERSION="1.1.2"

mkdir -p "$WORK/mods" "$WORK/config"
cd "$WORK"
if [ ! -f server.jar ]; then
  curl -fsSL -o server.jar \
    "https://meta.fabricmc.net/v2/versions/loader/$MC_VERSION/$LOADER_VERSION/$INSTALLER_VERSION/server/jar"
fi
python3 "$ROOT/tools/install_mods.py" server "$WORK/mods" --stable-names
rm -f mods/terracraft-geo*.jar
cp "$JAR" mods/terracraft-geo.jar
cp "$ROOT/serveur-local/config/openpartiesandclaims-server.toml" config/
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
server-port=25599
query.port=25599
management-server-port=0
view-distance=3
simulation-distance=3
EOF

rm -f console.log
: > console.in
# Commandes envoyées en ajoutant des lignes à console.in.
tail -f console.in | java -Xms512M -Xmx1536M -jar server.jar nogui > console.log 2>&1 &
SERVER=$!
trap 'pkill -P $$ 2>/dev/null || true' EXIT

for _ in $(seq "$TIMEOUT"); do
  if grep -q "Done (" console.log; then
    break
  fi
  if ! kill -0 "$SERVER" 2>/dev/null; then
    break
  fi
  sleep 1
done

if grep -q "Done (" console.log && ! grep -qE "Failed to load registries|Registry loading errors" console.log; then
  echo stop >> console.in
  # Attendre la fin réelle du serveur (sauvegarde comprise) : le suivant peut démarrer sans conflit.
  for _ in $(seq 90); do
    alive=0
    for pid in $(pgrep -x java || true); do
      [ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" = "$(pwd -P)" ] && alive=1
    done
    [ "$alive" = 0 ] && break
    sleep 1
  done
  echo "Test de démarrage réussi : $(grep -o 'Done ([^)]*)' console.log)"
  exit 0
fi

echo "ÉCHEC du test de démarrage. Fin du journal :"
grep -nE "ERROR|Exception|Caused by|Failed" console.log | head -40 || true
tail -40 console.log
exit 1
