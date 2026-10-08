#!/usr/bin/env python3
"""Contrôle des ressources du mod : chaque objet, bloc et entité enregistré dans le code doit avoir ses traductions
(français et anglais), sa définition d'objet, son modèle et ses textures ; chaque bloc son état de bloc et sa table
de butin. Évite les cubes violets et les noms « item.terracraft_geo.xxx » en jeu.

Usage : python3 tools/check_assets.py   (code de sortie 1 s'il manque quelque chose)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "geo-mod/src/main/java/com/terracraft/geo"
RES = ROOT / "geo-mod/src/main/resources"
ASSETS = RES / "assets/terracraft_geo"
DATA = RES / "data/terracraft_geo"

code = "\n".join(p.read_text() for p in SRC.rglob("*.java"))
blocks = set(re.findall(r'\bblock\("([a-z0-9_]+)"', code))
items = set(re.findall(r'\b(?:item|gun)\("([a-z0-9_]+)"', code)) - blocks
entities = set(re.findall(r'\b(?:entity|register|vehicle)\("([a-z0-9_]+)"', code)) - blocks - items
entities |= set(re.findall(r'EntityType\.Builder.*?\.build\(.*?"([a-z0-9_]+)"', code))
lang = {name: json.loads((ASSETS / "lang" / name).read_text()) for name in ("fr_fr.json", "en_us.json")}
problems = []


def need(path, what):
    if not path.exists():
        problems.append(f"{what} : {path.relative_to(ROOT)} absent")


def textures(model_path):
    """Textures référencées par un modèle (et ses parents du mod)."""
    model = json.loads(model_path.read_text())
    for value in model.get("textures", {}).values():
        sprite = value["sprite"] if isinstance(value, dict) else value
        if sprite.startswith("terracraft_geo:"):
            need(ASSETS / "textures" / (sprite.split(":", 1)[1] + ".png"), f"texture de {model_path.stem}")
    parent = model.get("parent", "")
    if parent.startswith("terracraft_geo:"):
        parent_path = ASSETS / "models" / (parent.split(":", 1)[1] + ".json")
        need(parent_path, f"modèle parent de {model_path.stem}")
        if parent_path.exists():
            textures(parent_path)


for name in sorted(items | blocks):
    kind = "block" if name in blocks else "item"
    for file, entries in lang.items():
        if f"{kind}.terracraft_geo.{name}" not in entries:
            problems.append(f"{name} : traduction {kind}.terracraft_geo.{name} absente de {file}")
    definition = ASSETS / "items" / f"{name}.json"
    need(definition, f"{name} : définition d'objet")
    if definition.exists():
        for ref in re.findall(r'"model":\s*"terracraft_geo:([^"]+)"', definition.read_text()):
            model = ASSETS / "models" / f"{ref}.json"
            need(model, f"{name} : modèle {ref}")
            if model.exists():
                textures(model)
    if kind == "block":
        need(ASSETS / "blockstates" / f"{name}.json", f"{name} : état de bloc")
        need(DATA / "loot_table/blocks" / f"{name}.json", f"{name} : table de butin")

for name in sorted(entities):
    if not any(f"entity.terracraft_geo.{name}" in entries for entries in lang.values()):
        continue  # Noms d'enregistrement qui ne sont pas des entités (sons, etc.).
    for file, entries in lang.items():
        if f"entity.terracraft_geo.{name}" not in entries:
            problems.append(f"{name} : traduction entity.terracraft_geo.{name} absente de {file}")

print(f"{len(items)} objets, {len(blocks)} blocs vérifiés.")
for problem in problems:
    print("  MANQUE", problem)
sys.exit(1 if problems else 0)
