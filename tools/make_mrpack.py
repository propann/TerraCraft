#!/usr/bin/env python3
"""Fabrique le pack client TerraCraft au format Modrinth (.mrpack), importable dans Prism.

Usage : python3 tools/make_mrpack.py <terracraft-geo.jar> <sortie.mrpack>

Les mods tiers sont référencés par leurs URL Modrinth (avec sha1/sha512) ; seul le jar
TerraCraft est embarqué dans le pack (overrides/mods). Les mods graphiques qui exigent
OpenGL (Sodium, Iris, Distant Horizons, shaders) sont marqués optionnels.
"""
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from install_mods import API, GAME_VERSION, LOADER, USER_AGENT  # noqa: E402

FABRIC_LOADER = "0.19.5"
OPTIONAL = {"sodium", "iris", "distanthorizons", "complementary-reimagined"}


def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def version_of(slug, kind):
    params = {"game_versions": json.dumps([GAME_VERSION])}
    if kind == "mod":
        params["loaders"] = json.dumps([LOADER])
    versions = get_json(f"{API}/project/{slug}/version?{urllib.parse.urlencode(params)}")
    if not versions and kind == "shader":
        versions = get_json(f"{API}/project/{slug}/version")
    release = [v for v in versions if v["version_type"] == "release"]
    return (release or versions)[0]


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    mod_jar, output = Path(sys.argv[1]), Path(sys.argv[2])
    version = mod_jar.stem.split("-")[-1] if "-" in mod_jar.stem else "dev"
    files = []
    manifest = Path(__file__).with_name("mods.txt")
    for line in manifest.read_text(encoding="utf-8").splitlines():
        parts = line.split("#", 1)[0].split()
        if not parts or parts[1] not in ("both", "client"):
            continue
        slug, kind = parts[0], parts[2] if len(parts) > 2 else "mod"
        v = version_of(slug, kind)
        f = next(x for x in v["files"] if x["primary"])
        folder = "shaderpacks" if kind == "shader" else "mods"
        files.append({
            "path": f"{folder}/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "optional" if slug in OPTIONAL else "required", "server": "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
        print(f"  {'option' if slug in OPTIONAL else 'requis'}  {f['filename']}")
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": "TerraCraft",
        "summary": "La Terre réelle après la chute : villes OSM, ruines, véhicules, fusées et Lune.",
        "files": files,
        "dependencies": {"minecraft": GAME_VERSION, "fabric-loader": FABRIC_LOADER},
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        pack.write(mod_jar, "overrides/mods/terracraft-geo.jar")
    print(f"Pack écrit : {output} ({len(files)} mods référencés + TerraCraft)")


if __name__ == "__main__":
    main()
