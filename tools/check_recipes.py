#!/usr/bin/env python3
"""Contrôle des recettes du mod : chaque recette est valide (ingrédients et résultat existants en 26.3, motif et clé
cohérents) et chaque objet du mod s'obtient en survie (recette, butin d'un bloc ou d'un coffre, ou source déclarée
dans OBTAINED_OTHERWISE). Affiche aussi la liste des recettes, pour relecture de l'équilibrage.

Usage : python3 tools/check_recipes.py [--liste]   (code de sortie 1 en cas de problème)
Les objets et tags vanilla viennent de tools/data/vanilla-26.3.json (extrait du jar de Minecraft 26.3).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "geo-mod/src/main/java/com/terracraft/geo"
DATA = ROOT / "geo-mod/src/main/resources/data"
VANILLA = json.loads((ROOT / "tools/data/vanilla-26.3.json").read_text())

# Objets sans recette ni butin de bloc : d'où ils viennent (à garder exact quand le code change).
OBTAINED_OTHERWISE = {
    "alien_artifact": "coffres des sanctuaires lunaires (MoonUnderground)",
    "commander_badge": "butin du Commandant du bunker (Bosses)",
    "docking_tunnel_hull": "posé et retiré par le vaisseau, jamais obtenable",
    "docking_tunnel_window": "posé et retiré par le vaisseau, jamais obtenable",
    "docking_tunnel_light": "posé et retiré par le vaisseau, jamais obtenable",
    "helium3_crystals": "généré sur la Lune ; cassé, il donne des éclats d'hélium-3",
    "oil_puddle": "repère de gisement généré dans le monde, ne se ramasse pas",
}

code = "\n".join(p.read_text() for p in SRC.rglob("*.java"))
blocks = set(re.findall(r'\bblock\("([a-z0-9_]+)"', code))
items = set(re.findall(r'\b(?:item|gun)\("([a-z0-9_]+)"', code)) | blocks
vanilla_items = set(VANILLA["items"])
vanilla_tags = set(VANILLA["item_tags"])
mod_tags = {p.stem for p in (DATA / "terracraft_geo/tags/item").glob("*.json")} if (DATA / "terracraft_geo/tags/item").exists() else set()
problems = []


def known(ref, where):
    """Vérifie un identifiant d'objet ou de tag (« #minecraft:planks »)."""
    if ref.startswith("#"):
        namespace, path = ref[1:].split(":", 1)
        ok = path in vanilla_tags if namespace == "minecraft" else path in mod_tags if namespace == "terracraft_geo" else False
    else:
        namespace, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        ok = path in vanilla_items if namespace == "minecraft" else path in items if namespace == "terracraft_geo" else False
    if not ok:
        problems.append(f"{where} : {ref} n'existe pas en 26.3")


def ingredients(value):
    """Un ingrédient : chaîne, liste de chaînes, ou objet {item|tag}."""
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [r for v in value for r in ingredients(v)]
    if isinstance(value, dict):
        return [value["item"]] if "item" in value else ["#" + value["tag"]] if "tag" in value else []
    return []


def short(ref):
    return ref.replace("terracraft_geo:", "").replace("minecraft:", "mc:")


outputs = {}
listing = []
for file in sorted((DATA / "terracraft_geo/recipe").glob("*.json")):
    recipe = json.loads(file.read_text())
    where = f"recette {file.stem}"
    kind = recipe["type"].split(":")[1]
    result = recipe.get("result", {})
    result_id = result["id"] if isinstance(result, dict) else result
    count = result.get("count", 1) if isinstance(result, dict) else 1
    known(result_id, where)
    outputs.setdefault(result_id, []).append(file.stem)
    used = []
    if kind == "crafting_shaped":
        pattern = recipe["pattern"]
        keys = recipe["key"]
        chars = {c for row in pattern for c in row if c != " "}
        if len({len(row) for row in pattern}) != 1 or len(pattern) > 3 or max(len(r) for r in pattern) > 3:
            problems.append(f"{where} : motif irrégulier {pattern}")
        for c in chars - set(keys):
            problems.append(f"{where} : le motif utilise « {c} » absent de la clé")
        for c in set(keys) - chars:
            problems.append(f"{where} : la clé « {c} » n'est pas utilisée dans le motif")
        for c, value in keys.items():
            n = sum(row.count(c) for row in pattern)
            used += [f"{n}× {short(r)}" for r in ingredients(value)[:1]]
            for ref in ingredients(value):
                known(ref, where)
    elif kind == "crafting_shapeless":
        for value in recipe["ingredients"]:
            for ref in ingredients(value):
                known(ref, where)
            used.append(short(ingredients(value)[0]))
    else:
        for ref in ingredients(recipe["ingredient"]):
            known(ref, where)
        used.append(f"{kind} {short(ingredients(recipe['ingredient'])[0])}")
    listing.append(f"{short(result_id):28} ×{count:<2} ← " + ", ".join(used))

# Butins de blocs et de coffres du mod : ce qu'ils peuvent donner.
dropped = set()
for file in (DATA / "terracraft_geo/loot_table").rglob("*.json"):
    dropped |= set(re.findall(r'"terracraft_geo:([a-z0-9_]+)"', file.read_text()))
# Objets donnés par le code (butins dynamiques, comptoir, kits) : new ItemStack(ModX.NAME…)
constants = dict(re.findall(r'public static final \w+(?:<[^>]*>)? ([A-Z0-9_]+) = (?:block|item|gun)\("([a-z0-9_]+)"', code))
given = {constants[c] for c in re.findall(r'new ItemStack\(\w+\.([A-Z0-9_]+)', code) if c in constants}

for name in sorted(items):
    if f"terracraft_geo:{name}" in outputs or name in OBTAINED_OTHERWISE:
        continue
    if name in dropped or name in given:
        continue
    problems.append(f"{name} : aucune recette, aucun butin, aucune source déclarée (OBTAINED_OTHERWISE)")
for name in OBTAINED_OTHERWISE:
    if name not in items:
        problems.append(f"OBTAINED_OTHERWISE : {name} n'existe plus")

if "--liste" in sys.argv:
    print("\n".join(listing))
if problems:
    print("\n".join(problems))
    sys.exit(1)
print(f"{len(listing)} recettes, {len(items)} objets vérifiés.")
