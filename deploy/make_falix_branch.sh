#!/usr/bin/env bash
# Fabrique et pousse la branche « falix » : uniquement les fichiers à copier à la racine du
# serveur Falix (/home/container/). Falix copie le dépôt par-dessus le serveur sans rien
# supprimer : les jars ont donc des noms FIXES (sinon une mise à jour laisserait l'ancienne
# version en place et le serveur planterait sur un mod en double).
#
# Usage : deploy/make_falix_branch.sh            (construit, commit et pousse)
#         deploy/make_falix_branch.sh --no-push  (construit et commit seulement)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="falix"
WORK="$(mktemp -d)"
trap 'git -C "$ROOT" worktree remove --force "$WORK/tree" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

echo "== Compilation du mod"
(cd "$ROOT/geo-mod" && "${GRADLE_BIN:-$HOME/.cache/terracraft-gradle/9.7.1/gradle-9.7.1/bin/gradle}" --no-daemon -q build -x test)
VERSION="$(sed -n 's/^mod_version=//p' "$ROOT/geo-mod/gradle.properties")"

echo "== Mods serveur (Modrinth, sha512 vérifié)"
python3 "$ROOT/tools/install_mods.py" server "$WORK/mods" --stable-names
cp "$ROOT/geo-mod/build/libs/terracraft-geo-$VERSION.jar" "$WORK/mods/terracraft-geo.jar"

echo "== Branche $BRANCH"
git -C "$ROOT" fetch -q origin "$BRANCH" 2>/dev/null || true
if git -C "$ROOT" rev-parse -q --verify "origin/$BRANCH" >/dev/null; then
  git -C "$ROOT" worktree add -q -B "$BRANCH" "$WORK/tree" "origin/$BRANCH"
else
  git -C "$ROOT" worktree add -q --detach "$WORK/tree"
  git -C "$WORK/tree" checkout -q --orphan "$BRANCH"
fi
git -C "$WORK/tree" rm -rq --cached . >/dev/null 2>&1 || true
find "$WORK/tree" -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +

mkdir -p "$WORK/tree/mods" "$WORK/tree/config"
cp "$WORK/mods/"*.jar "$WORK/tree/mods/"
cp "$ROOT/serveur-local/config/openpartiesandclaims-server.toml" "$WORK/tree/config/"
cp "$ROOT/deploy/FALIX.md" "$WORK/tree/TERRACRAFT-FALIX.md"
# Administrateurs du serveur (réécrit à chaque déploiement : ajoute-les ici, pas dans la console).
cp "$ROOT/deploy/ops.json" "$WORK/tree/ops.json"

cd "$WORK/tree"
git add -A
if git diff --cached --quiet; then
  echo "Rien de nouveau à déployer."
  exit 0
fi
git -c user.name="$(git config user.name || echo azoth)" -c user.email="$(git config user.email || echo 000.enzo.000@gmail.com)" \
  commit -q -m "deploy: TerraCraft $VERSION pour Falix" -m "Construit depuis $(git -C "$ROOT" rev-parse --short HEAD)."
echo "Commit $(git rev-parse --short HEAD) sur $BRANCH : $(ls mods | tr '\n' ' ')"
if [ "${1:-}" != "--no-push" ]; then
  git push -q origin "$BRANCH"
  echo "Poussé : Falix va redéployer automatiquement."
fi
