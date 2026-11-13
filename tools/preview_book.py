#!/usr/bin/env python3
"""Prévisualise un brouillon de livre selon la pagination approximative du mod."""

from __future__ import annotations

import argparse
import json
import re
import sys
import textwrap
from pathlib import Path

LINE_WIDTH = 18
LINES_PER_PAGE = 13
MAX_PAGES = 100


def pages_from_text(source: str) -> list[str]:
    pages: list[str] = []
    for section in re.split(r"(?m)^---+\s*$", source):
        lines: list[str] = []
        for paragraph in section.strip("\n").split("\n"):
            if not paragraph:
                lines.append("")
                continue
            lines.extend(textwrap.wrap(
                paragraph,
                width=LINE_WIDTH,
                break_long_words=True,
                break_on_hyphens=False,
                replace_whitespace=False,
                drop_whitespace=True,
            ) or [""])
        for start in range(0, len(lines), LINES_PER_PAGE):
            pages.append("\n".join(lines[start:start + LINES_PER_PAGE]))
    return pages


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("brouillon", type=Path, help="Texte UTF-8 ; --- seul sur sa ligne sépare les sections")
    parser.add_argument("--json", action="store_true", help="Afficher les pages en tableau JSON")
    args = parser.parse_args()

    try:
        source = args.brouillon.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"Impossible de lire {args.brouillon} : {exc}", file=sys.stderr)
        return 2

    pages = pages_from_text(source)
    if not pages:
        print("Le brouillon ne contient aucun texte.", file=sys.stderr)
        return 2
    if len(pages) > MAX_PAGES:
        print(f"Livre trop long : {len(pages)} pages (maximum {MAX_PAGES}).", file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(pages, ensure_ascii=False, indent=2))
    else:
        print(f"Aperçu approximatif : {len(pages)} page(s), {LINE_WIDTH} caractères par ligne, "
              f"{LINES_PER_PAGE} lignes par page.\n")
        for index, page in enumerate(pages, start=1):
            print(f"===== PAGE {index}/{len(pages)} =====")
            print(page)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
