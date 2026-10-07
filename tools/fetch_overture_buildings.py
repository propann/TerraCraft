#!/usr/bin/env python3
"""Télécharge un cache de bâtiments Overture pour TerraCraft.

Le serveur lit ensuite ``terracraft-cache/osm/scale-1.0/overture-buildings.geojson``.
Les routes restent fournies par les tuiles vectorielles habituelles.

Exemple :
  uvx overturemaps download --bbox=2.28,48.84,2.30,48.86 -f geojson \
      --type=building -o serveur-local/terracraft-cache/osm/scale-1.0/overture-buildings.geojson

Ce wrapper documente et vérifie le format attendu. ``overturemaps`` est le client officiel
Overture ; il lit le GeoParquet cloud et ne télécharge que la bbox demandée.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path, help="GeoJSON produit par overturemaps")
    parser.add_argument("output", type=Path, help="cache overture-buildings.geojson")
    args = parser.parse_args()

    data = json.loads(args.input.read_text(encoding="utf-8"))
    if data.get("type") != "FeatureCollection":
        raise SystemExit("Le fichier doit être une FeatureCollection GeoJSON")
    kept = []
    for feature in data.get("features", []):
        geometry = feature.get("geometry") or {}
        if geometry.get("type") not in {"Polygon", "MultiPolygon"}:
            continue
        properties = feature.setdefault("properties", {})
        # Les champs Overture sont gardés tels quels : height, num_floors, min_height,
        # roof_height, roof_shape, facade_material, roof_material, etc.
        properties.setdefault("building", "building")
        kept.append(feature)
    result = {"type": "FeatureCollection", "features": kept}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False), encoding="utf-8")
    print(f"Cache Overture écrit : {args.output} ({len(kept)} bâtiments)")


if __name__ == "__main__":
    main()
