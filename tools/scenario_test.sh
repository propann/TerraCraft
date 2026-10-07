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
# Orbite lunaire : module de station et balise (Bob regarde le sol, orienté vers le sud)
cmd "execute in terracraft_geo:moon_orbit run forceload add -16 -16 16 16" 3
cmd "execute in terracraft_geo:moon_orbit run fill -6 150 -6 6 150 6 minecraft:stone" 2
cmd "execute in terracraft_geo:moon_orbit run tp Bob 0 151 0 0 90" 3
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:station_module"
cmd "player Bob use once" 2
cmd "execute in terracraft_geo:moon_orbit if block 0 151 4 terracraft_geo:oxygen_distributor" 1
cmd "execute in terracraft_geo:moon_orbit if block 3 152 4 air" 1
cmd "execute in terracraft_geo:moon_orbit run tp Bob 0.5 151 -4.5 0 45" 2
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:station_beacon"
cmd "player Bob use once" 2
# Vol en fusée : Terre → orbite lunaire, arrivée à la balise de Bob (5 doses sur 8)
cmd "tp Bob 5.5 201 5.5" 2
cmd "summon terracraft_geo:rocket 10.5 201 10.5 {Parts:15b,Fuel:8,Target:11b}" 2
cmd "ride Bob mount @e[type=terracraft_geo:rocket,limit=1,sort=nearest]" 2
cmd "execute as Bob run terracraft fusee decoller" 25
cmd "data get entity Bob Dimension" 1
cmd "data get entity Bob Pos" 1
cmd "data get entity @e[type=terracraft_geo:rocket,limit=1] Fuel" 1
# Atelier de station : plans débloqués en orbite lunaire, améliorations installées sur la fusée
cmd "execute in terracraft_geo:moon_orbit run setblock 5 151 -2 terracraft_geo:station_workshop" 1
cmd "give Bob terracraft_geo:titanium_ingot 10"
cmd "give Bob terracraft_geo:rocket_tank"
cmd "give Bob terracraft_geo:helium3_shard 6"
cmd "give Bob minecraft:chest 2"
cmd "give Bob minecraft:iron_ingot 8"
cmd "give Bob minecraft:redstone_block" 2
cmd "execute as Bob run atelier installer tank"
cmd "execute as Bob run atelier installer ion"
cmd "execute as Bob run atelier installer cargo"
cmd "execute as Bob run atelier installer mars_nav"
cmd "execute as Bob at Bob run data get entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] Upgrades" 1
cmd "execute as Bob at Bob run data merge entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] {Target:4b}" 1
cmd "execute as Bob run terracraft fusee decoller" 2
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
check(log.count("Test passed") >= 2, "module de station : distributeur d'oxygène au centre, porte ouverte")
check(any(s.get("modules") == 1 for s in stats), "compteur de modules de station")
stations = json.loads((data / "stations.json").read_text())
check(len(stations) == 1 and stations[0]["dimension"] == "terracraft_geo:moon_orbit", "balise de station enregistrée en orbite lunaire")
check('Bob has the following entity data: "terracraft_geo:moon_orbit"' in log, "vol en fusée jusqu'à l'orbite lunaire")
pos = re.findall(r"Bob has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]", log)
check(bool(pos) and abs(float(pos[-1][0]) - 2.5) < 1 and abs(float(pos[-1][2]) + 2.5) < 1, "fusée posée à côté de la balise de station")
check("Rocket has the following entity data: 3" in log, "carburant : 5 doses consommées (Terre → orbite lunaire)")
plans = sorted(p for r in progression.values() for p in (r.get("plans") or []))
check(plans == ["CARGO", "ION", "TANK"], f"plans débloqués par l'exploration, pas la navigation martienne (obtenu {plans})")
check("Rocket has the following entity data: 7b" in log, "atelier : réservoir étendu, moteur ionique et soute installés")
check(log.count("[ATELIER]") == 3, "trois installations journalisées, navigation martienne refusée")
check("Décollage refusé" in log, "vers Mars sans navigation martienne : décollage refusé")
check(not any("Exception" in line and "spark" not in line for line in log.splitlines()),
      "aucune exception dans le journal (hors spark)")
sys.exit(1 if failures else 0)
EOF
