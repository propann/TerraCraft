#!/usr/bin/env python3
"""Télécharge les mods de tools/mods.txt depuis Modrinth (sha512 vérifié).

Usage :
  python3 tools/install_mods.py server serveur-local/mods
  python3 tools/install_mods.py client <instance>/minecraft/mods [--shaders <instance>/minecraft/shaderpacks]
  python3 tools/install_mods.py --update-lock
  --stable-names : enregistre chaque mod sous « <slug>.jar » (déploiement par copie, sans doublons)

Les versions utilisées sont figées dans tools/mods.lock.json : un build ou un déploiement
installe toujours exactement les mêmes fichiers. Pour passer aux dernières versions, lancer
--update-lock, tester le serveur et le client, puis commiter le fichier de verrouillage.
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
USER_AGENT = "TerraCraftGeo/0.2 (+https://github.com/propann/TerraCraft)"
LOCK_FILE = Path(__file__).with_name("mods.lock.json")


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


def read_lock():
    return json.loads(LOCK_FILE.read_text(encoding="utf-8")) if LOCK_FILE.exists() else {}


def resolve(slug, kind):
    """Version figée dans mods.lock.json, sinon la dernière (et on prévient)."""
    pinned = read_lock().get(slug)
    if pinned:
        return json.loads(get(f"{API}/version/{pinned['id']}"))
    print(f"  ⚠ {slug} absent de {LOCK_FILE.name} : dernière version utilisée (lancer --update-lock)",
          file=sys.stderr)
    return latest(slug, kind)


def manifest_entries():
    manifest = Path(__file__).with_name("mods.txt")
    for line in manifest.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].split()
        if line:
            yield line[0], line[1], line[2] if len(line) > 2 else "mod"


def update_lock():
    lock = {}
    for slug, _where, kind in manifest_entries():
        version = latest(slug, kind)
        lock[slug] = {"id": version["id"], "version": version["version_number"]}
        print(f"  {slug:28} {version['version_number']}")
    LOCK_FILE.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{LOCK_FILE} mis à jour : teste serveur et client avant de commiter.")


STABLE = "--stable-names" in sys.argv


def install(slug, kind, target):
    version = resolve(slug, kind)
    file = next(f for f in version["files"] if f["primary"])
    name = f"{slug}.jar" if STABLE and kind == "mod" else file["filename"]
    destination = target / name
    if (target / (name + ".disabled")).exists():
        print(f"  désactivé  {name} (laissé tel quel)")
        return name
    if destination.exists() and hashlib.sha512(destination.read_bytes()).hexdigest() == file["hashes"]["sha512"]:
        print(f"  ok      {name}")
        return name
    data = get(file["url"])
    if hashlib.sha512(data).hexdigest() != file["hashes"]["sha512"]:
        raise SystemExit(f"{slug} : somme sha512 invalide, téléchargement refusé")
    # Retire les anciennes versions du même projet avant d'écrire la nouvelle.
    if not STABLE:
        prefix = file["filename"].split("-")[0].lower()
        for old in target.glob("*.jar" if kind == "mod" else "*.zip"):
            if old.name != file["filename"] and old.name.lower().startswith(prefix):
                old.unlink()
    destination.write_bytes(data)
    print(f"  ajouté  {name}  ({file['filename']})")
    return name


def main():
    if "--update-lock" in sys.argv:
        update_lock()
        return
    if len(sys.argv) < 3 or sys.argv[1] not in ("server", "client"):
        raise SystemExit(__doc__)
    side = sys.argv[1]
    mods_dir = Path(sys.argv[2])
    shaders_dir = Path(sys.argv[sys.argv.index("--shaders") + 1]) if "--shaders" in sys.argv else None
    mods_dir.mkdir(parents=True, exist_ok=True)
    for slug, where, kind in manifest_entries():
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
