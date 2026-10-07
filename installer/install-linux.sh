#!/usr/bin/env bash
# Installeur TerraCraft (launcher Minecraft officiel, Linux / macOS).
# Installe Fabric 26.3 puis les mods de la dernière version publiée sur GitHub.
set -euo pipefail

REPO="propann/TerraCraft"
MC="${MINECRAFT_DIR:-$HOME/.minecraft}"
[ "$(uname)" = "Darwin" ] && MC="${MINECRAFT_DIR:-$HOME/Library/Application Support/minecraft}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

need() { command -v "$1" >/dev/null 2>&1 || { echo "Il faut installer : $1"; exit 1; }; }
need curl; need python3; need unzip

echo "== Dernière version de TerraCraft"
URL="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | python3 -c '
import json,sys
assets=json.load(sys.stdin)["assets"]
print(next(a["browser_download_url"] for a in assets if a["name"].endswith(".mrpack")))')"
curl -fsSL "$URL" -o "$WORK/pack.mrpack"
unzip -q "$WORK/pack.mrpack" -d "$WORK/pack"

echo "== Fabric 26.3 (loader 0.19.5)"
if ! ls "$MC/versions" 2>/dev/null | grep -q 'fabric-loader-0.19.5-26.3'; then
  need java
  curl -fsSL https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar -o "$WORK/fabric.jar"
  PROFILE=""
  [ -f "$MC/launcher_profiles.json" ] || PROFILE="-noprofile"
  java -jar "$WORK/fabric.jar" client -mcversion 26.3 -loader 0.19.5 -dir "$MC" $PROFILE
fi

echo "== Mods"
mkdir -p "$MC/mods"
if [ -n "$(ls -A "$MC/mods" 2>/dev/null)" ]; then
  BACKUP="$MC/mods-avant-terracraft-$(date +%Y%m%d-%H%M%S)"
  mv "$MC/mods" "$BACKUP" && mkdir -p "$MC/mods"
  echo "Anciens mods déplacés dans $BACKUP"
fi
python3 - "$WORK/pack/modrinth.index.json" "$MC" <<'PY'
import hashlib, json, sys, urllib.request
index, root = json.load(open(sys.argv[1])), sys.argv[2]
for f in index["files"]:
    if f["env"]["client"] != "required":
        continue
    data = urllib.request.urlopen(urllib.request.Request(f["downloads"][0], headers={"User-Agent": "TerraCraft-installer"})).read()
    if hashlib.sha512(data).hexdigest() != f["hashes"]["sha512"]:
        sys.exit("Somme de contrôle invalide : " + f["path"])
    open(f"{root}/{f['path']}", "wb").write(data)
    print("  installé", f["path"])
PY
# Liste Multijoueur : posée seulement si le joueur n'en a pas encore (on n'écrase jamais la sienne).
if [ -f "$WORK/pack/overrides/servers.dat" ]; then
  if [ ! -f "$MC/servers.dat" ]; then
    cp "$WORK/pack/overrides/servers.dat" "$MC/servers.dat"
    echo "Serveur TerraCraft ajouté à la liste Multijoueur."
  fi
  rm "$WORK/pack/overrides/servers.dat"
fi
cp -r "$WORK/pack/overrides/." "$MC/"
echo
echo "TerraCraft est installé. Dans le launcher Minecraft, choisis le profil « fabric-loader-26.3 »,"
echo "puis Multijoueur → serveur TerraCraft (adresse : terre1.falixsrv.me)."
