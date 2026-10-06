#!/usr/bin/env python3
"""Télécharge les mods de tools/mods.txt depuis Modrinth (sha512 vérifié).

Usage :
  python3 tools/install_mods.py server serveur-local/mods
  python3 tools/install_mods.py client <instance>/minecraft/mods [--shaders <instance>/minecraft/shaderpacks]
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

GAME_VERSION = "26.3"
LOADER = "fabric"
API = "https://api.modrinth.com/v2"
USER_AGENT = "TerraCraftGeo/0.2 (prototype de serveur Minecraft local)"


def get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def latest(slug, kind):
    params = {"game_versions": json.dumps([GAME_VERSION])}
    if kind == "mod":
        params["loaders"] = json.dumps([LOADER])
    versions = json.loads(get(f"{API}/project/{slug}/version?{urllib.parse.urlencode(params)}"))
    if kind == "shader":
        # Les shaders ne déclarent pas toujours la dernière version du jeu : on prend la plus récente.
        versions = versions or json.loads(get(f"{API}/project/{slug}/version"))
    if not versions:
        raise SystemExit(f"{slug} : aucune version pour {GAME_VERSION}/{LOADER}")
    release = [v for v in versions if v["version_type"] == "release"]
    return (release or versions)[0]


def install(slug, kind, target):
    version = latest(slug, kind)
    file = next(f for f in version["files"] if f["primary"])
    destination = target / file["filename"]
    if destination.exists() and hashlib.sha512(destination.read_bytes()).hexdigest() == file["hashes"]["sha512"]:
        print(f"  ok      {file['filename']}")
        return file["filename"]
    data = get(file["url"])
    if hashlib.sha512(data).hexdigest() != file["hashes"]["sha512"]:
        raise SystemExit(f"{slug} : somme sha512 invalide, téléchargement refusé")
    # Retire les anciennes versions du même projet avant d'écrire la nouvelle.
    prefix = file["filename"].split("-")[0].lower()
    for old in target.glob("*.jar" if kind == "mod" else "*.zip"):
        if old.name != file["filename"] and old.name.lower().startswith(prefix) and old.name.lower() != "terracraft-geo":
            old.unlink()
    destination.write_bytes(data)
    print(f"  ajouté  {file['filename']}")
    return file["filename"]


def main():
    if len(sys.argv) < 3 or sys.argv[1] not in ("server", "client"):
        raise SystemExit(__doc__)
    side = sys.argv[1]
    mods_dir = Path(sys.argv[2])
    shaders_dir = Path(sys.argv[sys.argv.index("--shaders") + 1]) if "--shaders" in sys.argv else None
    mods_dir.mkdir(parents=True, exist_ok=True)
    manifest = Path(__file__).with_name("mods.txt")
    for line in manifest.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].split()
        if not line:
            continue
        slug, where = line[0], line[1]
        kind = line[2] if len(line) > 2 else "mod"
        if where not in ("both", side):
            continue
        if kind == "shader":
            if shaders_dir:
                shaders_dir.mkdir(parents=True, exist_ok=True)
                install(slug, kind, shaders_dir)
            continue
        install(slug, kind, mods_dir)


if __name__ == "__main__":
    main()
