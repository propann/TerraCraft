#!/usr/bin/env python3
"""Reconstruit le dossier mods/ du dépôt à partir du pack client publié (TerraCraft-client.mrpack).

Les mods dont la licence permet la redistribution sont copiés dans mods/ (empreinte SHA-512 vérifiée). Ceux qui
l'interdisent (Xaero : tous droits réservés) ne sont pas republiés : les scripts mods/telecharger-restants.* les
récupèrent depuis Modrinth au même endroit. Le shader (licence propre) reste dans le pack et l'installeur.

Usage : python3 tools/sync_client_mods.py [chemin/vers/pack.mrpack]   (sans argument : dernière release GitHub)
"""
import hashlib
import json
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

REPO = "propann/TerraCraft"
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "mods"
HEADERS = {"User-Agent": "propann/TerraCraft sync_client_mods"}
# Licences relevées sur Modrinth ; seul ce qui est listé ici est republié.
LICENSES = {
    "fabric-api": ("Apache-2.0", "https://modrinth.com/mod/fabric-api"),
    "open-parties-and-claims": ("LGPL-3.0", "https://modrinth.com/mod/open-parties-and-claims"),
    "ForgeConfigAPIPort": ("MPL-2.0", "https://modrinth.com/mod/forge-config-api-port"),
    "ferritecore": ("MIT", "https://modrinth.com/mod/ferrite-core"),
    "sodium": ("PolyForm Shield 1.0.0 — https://polyformproject.org/licenses/shield/1.0.0/",
               "https://modrinth.com/mod/sodium"),
    "iris": ("LGPL-3.0", "https://modrinth.com/mod/iris"),
    "DistantHorizons": ("LGPL-3.0", "https://modrinth.com/mod/distanthorizons"),
    "modmenu": ("MIT", "https://modrinth.com/mod/modmenu"),
}
RESTRICTED = {"xaerominimap": "Xaero's Minimap", "xaeroworldmap": "Xaero's World Map"}


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=120) as response:
        return response.read()


def latest_pack():
    release = json.loads(fetch(f"https://api.github.com/repos/{REPO}/releases/latest"))
    asset = next(a for a in release["assets"] if a["name"].endswith(".mrpack"))
    return fetch(asset["browser_download_url"]), release["tag_name"]


def family(name):
    for key in list(LICENSES) + list(RESTRICTED):
        if name.startswith(key):
            return key
    return None


def main():
    if len(sys.argv) > 1:
        data, tag = Path(sys.argv[1]).read_bytes(), Path(sys.argv[1]).name
    else:
        data, tag = latest_pack()
    with tempfile.TemporaryDirectory() as work:
        pack = Path(work) / "pack.mrpack"
        pack.write_bytes(data)
        with zipfile.ZipFile(pack) as archive:
            index = json.loads(archive.read("modrinth.index.json"))
            ours = archive.read("overrides/mods/terracraft-geo.jar")
    OUT.mkdir(exist_ok=True)
    for old in OUT.glob("*.jar"):
        old.unlink()
    published, restricted = [], []
    for entry in index["files"]:
        path = entry["path"]
        if not path.startswith("mods/"):
            continue  # Shader : licence propre, laissé au pack et à l'installeur.
        name = path.split("/", 1)[1]
        key = family(name)
        if key in RESTRICTED:
            restricted.append({"file": name, "url": entry["downloads"][0], "sha512": entry["hashes"]["sha512"],
                               "label": RESTRICTED[key]})
            continue
        if key is None:
            sys.exit(f"Licence inconnue pour {name} : ajoute-la dans LICENSES ou RESTRICTED avant de republier.")
        blob = fetch(entry["downloads"][0])
        if hashlib.sha512(blob).hexdigest() != entry["hashes"]["sha512"]:
            sys.exit(f"Empreinte incorrecte : {name}")
        (OUT / name).write_bytes(blob)
        published.append((name, *LICENSES[key]))
    (OUT / "terracraft-geo.jar").write_bytes(ours)
    (OUT / "a-telecharger.json").write_text(json.dumps(restricted, indent=2, ensure_ascii=False) + "\n")
    lines = [f"# Licences des mods de ce dossier ({tag})", "",
             "| Fichier | Licence | Source |", "|---|---|---|",
             "| terracraft-geo.jar | TerraCraft (ce dépôt) | https://github.com/propann/TerraCraft |"]
    lines += [f"| {name} | {licence} | {source} |" for name, licence, source in sorted(published)]
    lines += ["", "Non republiés (licence « tous droits réservés ») : téléchargés depuis Modrinth par "
              "`telecharger-restants.sh` / `telecharger-restants.ps1` :", ""]
    lines += [f"- {r['label']} — `{r['file']}` — {r['url']}" for r in restricted]
    (OUT / "LICENCES.md").write_text("\n".join(lines) + "\n")
    print(f"mods/ synchronisé sur {tag} : {len(published) + 1} jars, {len(restricted)} à télécharger.")


if __name__ == "__main__":
    main()
