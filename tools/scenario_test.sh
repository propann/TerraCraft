#!/usr/bin/env bash
# Test de scénario : deux faux joueurs (mod Carpet, uniquement pour ce test) jouent une partie
# courte sur un serveur neuf, puis on vérifie les données : villes, économie (comptoir, hôtel des
# ventes, frais, trésorerie), combinaison spatiale et respiration sur la Lune.
#
# Usage : tools/scenario_test.sh <terracraft-geo.jar> [dossier de travail]
# Prérequis : un dossier préparé par tools/smoke_server.sh (même dossier de travail).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(realpath "$1")"
WORK="${2:-$(mktemp -d)}"
CARPET_URL="https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar"

rm -rf "$WORK/world"
"$ROOT/tools/smoke_server.sh" "$JAR" "$WORK" > "$WORK/smoke.out" 2>&1 || { cat "$WORK/smoke.out"; exit 1; }
cd "$WORK"
# Le serveur du test de démarrage peut encore écrire le monde : on attend qu'il soit arrêté.
for _ in $(seq 60); do
  busy=0
  for pid in $(pgrep -x java || true); do
    [ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" = "$(pwd -P)" ] && busy=1
  done
  [ "$busy" = 0 ] && break
  sleep 1
done
rm -rf world
curl -fsSL -o mods/carpet.jar "$CARPET_URL"
trap 'rm -f "$WORK/mods/carpet.jar"; pkill -P $$ 2>/dev/null || true' EXIT

: > console.in
tail -f console.in | java -Xmx2G -jar server.jar nogui > scenario.log 2>&1 &
for _ in $(seq 600); do grep -q "Done (" scenario.log && break; sleep 1; done
grep -q "Done (" scenario.log || { echo "ÉCHEC : le serveur ne démarre pas"; tail -30 scenario.log; exit 1; }

cmd() { echo "$1" >> console.in; sleep "${2:-1}"; }
# Plateforme en hauteur : le point 0, 0 de la Terre réelle est en plein Atlantique.
cmd "forceload add -64 -64 63 63" 6
cmd "fill -60 200 -60 60 200 60 minecraft:stone" 3
cmd "player Alice spawn at 0 201 0" 4
cmd "player Bob spawn at 5 201 5" 4
cmd "gamemode survival Alice"
cmd "gamemode survival Bob"
# Villes
cmd "execute as Alice at Alice run ville creer Bourg Neuf" 2
cmd "execute as Alice run ville inviter Bob"
cmd "execute as Bob run ville rejoindre"
cmd "execute as Bob run ville deposer 200"
cmd "execute as Alice run ville payer Bob 50"
cmd "execute as Bob run tp Bob 900 220 900" 2
cmd "execute as Bob run ville tp" 2
# Économie
cmd "execute as Bob run comptoir 1"
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:fuel_can 1"
cmd "execute as Bob run hdv vendre 100"
cmd "execute as Alice run hdv acheter 1"
# Métier, signalement, largage militaire
cmd "execute as Bob run metier choisir pilote"
cmd "execute as Alice run metier choisir mecanicien"
cmd "execute as Bob run signaler Test automatique : tout va bien"
cmd "execute as Alice at Alice run terracraft largage" 2
cmd "execute as Bob run missions" 1
# Combinaison spatiale (clic droit avec chaque pièce)
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:space_helmet"
cmd "player Bob use once" 2
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:oxygen_tank 16"
cmd "player Bob use once" 2
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:jetpack"
cmd "player Bob use once" 2
# Respiration sur la Lune
cmd "execute in terracraft_geo:moon run forceload add 0 0" 3
cmd "execute in terracraft_geo:moon run fill -3 150 -3 3 150 3 minecraft:stone" 2
cmd "execute in terracraft_geo:moon run tp Bob 0 151 0" 8
cmd 'data get entity Bob "fabric:attachments"' 2
cmd "data get entity Bob Health" 1
cmd "save-all flush" 3
cmd "stop" 2
# Laisser le serveur finir sa sauvegarde avant de quitter (sinon le monde reste à moitié écrit).
for _ in $(seq 60); do
  pgrep -f "server.jar nogui" >/dev/null || break
  alive=0
  for pid in $(pgrep -x java || true); do
    [ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" = "$(pwd -P)" ] && alive=1
  done
  [ "$alive" = 0 ] && break
  sleep 1
done

python3 - "$WORK" <<'EOF'
import json, re, sys
from pathlib import Path
work = Path(sys.argv[1])
data = work / "world" / "terracraft_geo"
log = (work / "scenario.log").read_text(errors="replace")
failures = []

def check(condition, message):
    print(("  ok    " if condition else "  ÉCHEC ") + message)
    if not condition:
        failures.append(message)

towns = json.loads((data / "villes.json").read_text())
check(len(towns) == 1 and towns[0]["name"] == "Bourg Neuf", "ville fondée")
check(len(towns[0]["members"]) == 2, "Bob a rejoint la ville")
check(towns[0]["treasury"] == 150, "trésorerie = 200 déposés - 50 payés")
balances = sorted(json.loads((data / "balances.json").read_text()).values())
check(balances == [400, 888], f"soldes Alice 400 / Bob 888 (obtenu {balances})")
eco = json.loads((data / "economie.json").read_text())
created, destroyed = sum(eco["created"].values()), sum(eco["destroyed"].values())
check(created == sum(balances) + 150 + destroyed, "aucun crédit perdu ni créé : créés = comptes + trésorerie + détruits")
check(eco["destroyed"].get("frais_hdv") == 2 and eco["destroyed"].get("comptoir") == 60, "frais de 2 % et comptoir")
check(re.search(r"Teleported Bob|Bienvenue au centre", log) is not None or "ville tp" in log, "retour au centre-ville")
suit = re.findall(r'"terracraft_geo:space_suit": \[(.*?)\]\}', log)
check(bool(suit) and "space_helmet" in suit[-1] and "jetpack" in suit[-1] and "oxygen_tank" in suit[-1],
      "casque, bouteilles et jetpack portés par clic droit")
check(bool(suit) and '"minecraft:damage"' in suit[-1], "le casque consomme de l'oxygène sur la Lune")
check(re.search(r"Bob has the following entity data: 20\.0f", log) is not None, "Bob respire (aucun dégât)")
progression = json.loads((data / "progression.json").read_text())
jobs = sorted(r.get("job") or "" for r in progression.values())
check(jobs == ["MECANICIEN", "PILOTE"], f"métiers enregistrés (obtenu {jobs})")
stats = [r.get("stats", {}) for r in progression.values()]
check(any(s.get("listings") == 1 for s in stats) and any(s.get("shop") == 1 for s in stats), "compteurs vente et comptoir")
check(any(s.get("town") == 1 for s in stats) and any(s.get("job") == 1 for s in stats), "compteurs ville et métier")
contracts = json.loads((data / "contrats.json").read_text())
check(len(contracts) == 2 and all("baseline" in c for c in contracts.values()), "journée de contrats ouverte pour les deux joueurs")
reports = json.loads((data / "signalements.json").read_text())
check(len(reports) == 1 and reports[0]["player"] == "Bob", "signalement enregistré")
check("[LARGAGE] Caisse en" in log, "caisse de ravitaillement larguée sur la plateforme")
check(not any("Exception" in line and "spark" not in line for line in log.splitlines()),
      "aucune exception dans le journal (hors spark)")
sys.exit(1 if failures else 0)
EOF
