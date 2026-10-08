#!/usr/bin/env bash
# Mesure de génération (phase 0) : un faux joueur spectateur (Carpet, test uniquement) visite une liste de lieux
# réels sur un monde neuf ; pour chacun on relève les chunks générés, le temps moyen et maximal par chunk, les
# téléchargements de tuiles retentés ou abandonnés, et le tick moyen du serveur. Le rapport (Markdown) est écrit
# dans le fichier donné, ou affiché.
#
# Usage : tools/geo_bench.sh <terracraft-geo.jar> [dossier de travail] [rapport.md]
# Variables : WAIT (secondes par lieu, 60 par défaut), VIEW (distance de vue, 6 par défaut).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$(realpath "$1")"
WORK="${2:-$(mktemp -d)}"
REPORT="${3:-}"
WAIT="${WAIT:-60}"
VIEW="${VIEW:-6}"
CARPET_URL="https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar"

# Lieu;latitude;longitude — centre-ville dense, banlieue, industrie, campagne, montagne, côte, mégapoles.
PLACES=(
  "Paris (centre-ville);48.8566;2.3522"
  "Créteil (banlieue);48.7904;2.4556"
  "Le Havre (port, industrie);49.4810;0.1300"
  "Beauce (campagne);48.2000;1.6000"
  "Chamonix (montagne);45.9237;6.8694"
  "Marseille (côte);43.2965;5.3698"
  "New York (Manhattan);40.7580;-73.9855"
  "Tokyo (Shinjuku);35.6938;139.7034"
)

mkdir -p "$WORK"
rm -rf "$WORK/world"
"$ROOT/tools/smoke_server.sh" "$JAR" "$WORK" > "$WORK/smoke.out" 2>&1 || { cat "$WORK/smoke.out"; exit 1; }
cd "$WORK"
for _ in $(seq 60); do
  busy=0
  for pid in $(pgrep -x java || true); do
    [ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" = "$(pwd -P)" ] && busy=1
  done
  [ "$busy" = 0 ] && break
  sleep 1
done
rm -rf world
sed -i "s/^view-distance=.*/view-distance=$VIEW/; s/^simulation-distance=.*/simulation-distance=4/" server.properties
curl -fsSL -o mods/carpet.jar "$CARPET_URL"
trap 'rm -f "$WORK/mods/carpet.jar"; pkill -P $$ 2>/dev/null || true' EXIT

: > console.in
tail -f console.in | java -Xmx2G -jar server.jar nogui > bench.log 2>&1 &
for _ in $(seq 600); do grep -q "Done (" bench.log && break; sleep 1; done
grep -q "Done (" bench.log || { echo "ÉCHEC : le serveur ne démarre pas"; tail -30 bench.log; exit 1; }
cmd() { echo "$1" >> console.in; sleep "${2:-1}"; }

cmd "player Mesure spawn at 0 300 0" 5
cmd "gamemode spectator Mesure" 1
for place in "${PLACES[@]}"; do
  IFS=';' read -r name lat lon <<< "$place"
  echo "== $name"
  cmd "say BENCH_DEBUT $name" 0
  cmd "terracraft generation reset" 0
  cmd "execute as Mesure run terracraft aller $lat $lon" "$WAIT"
  cmd "terracraft generation" 1
  cmd "terracraft suivi" 1
done
# Charge : 5 puis 10 joueurs génèrent en même temps, chacun dans une ville différente (les premiers lieux sont déjà
# en mémoire : les suivants s'ajoutent, comme sur un vrai serveur).
CITIES=("51.5074;-0.1278" "52.5200;13.4050" "41.9028;12.4964" "40.4168;-3.7038" "50.8503;4.3517"
        "45.4642;9.1900" "48.2082;16.3738" "52.3676;4.9041" "47.3769;8.5417" "38.7223;-9.1393")
for count in 5 10; do
  echo "== $count joueurs simultanés"
  cmd "say BENCH_DEBUT $count joueurs simultanés" 0
  cmd "terracraft generation reset" 0
  for i in $(seq 1 "$count"); do
    IFS=';' read -r lat lon <<< "${CITIES[$((i - 1))]}"
    cmd "player Charge$i spawn at 0 300 0" 2
    cmd "gamemode spectator Charge$i" 0
    cmd "execute as Charge$i run terracraft aller $lat $lon" 0
  done
  sleep "$((WAIT + 30))"
  cmd "terracraft generation" 1
  cmd "terracraft suivi" 1
  cmd "say BENCH_MONDE $(du -sm world | cut -f1)" 1
done
cmd "stop" 2
for _ in $(seq 120); do pgrep -f "server.jar nogui" >/dev/null || break; sleep 1; done

python3 - "$WORK/bench.log" "$REPORT" "$WAIT" "$VIEW" "$(git -C "$ROOT" rev-parse --short HEAD)" <<'PY'
import datetime, re, sys
log_path, report, wait, view, sha = sys.argv[1:6]
log = open(log_path, errors="replace").read()
rows = []
for block in log.split("BENCH_DEBUT ")[1:]:
    name = block.split("\n", 1)[0].strip()
    gen = re.search(r"\[GEN\] chunks=(\d+) moyenne=([\d.]+)ms max=([\d.]+)ms reessais=(\d+) echecs=(\d+) memoire=(\d+)/\d+Mo", block)
    tick = re.search(r"\[SUIVI\].*tick ([\d.]+) ms", block)
    if gen:
        rows.append((name, *gen.groups(), tick.group(1) if tick else "?"))
errors = len(re.findall(r"/ERROR\]", log))
lines = [f"# Mesures de génération — {datetime.date.today().isoformat()}", "",
         f"Commit `{sha}` · distance de vue {view} · {wait} s par lieu · monde neuf, sans cache de tuiles.", "",
         "| Lieu | Chunks | Moyenne (ms/chunk) | Max (ms) | Réessais | Échecs | Mémoire (Mo) | Tick moyen (ms) |",
         "|---|---:|---:|---:|---:|---:|---:|---:|"]
lines += [f"| {n} | {c} | {m} | {x} | {r} | {e} | {mem} | {t} |" for n, c, m, x, r, e, mem, t in rows]
sizes = re.findall(r"BENCH_MONDE (\d+)", log)
lines += ["", f"Erreurs dans le journal du serveur : {errors}. Taille du monde après 5 puis 10 joueurs : "
          + " puis ".join(f"{x} Mo" for x in sizes) + "."]
text = "\n".join(lines) + "\n"
print(text)
if report:
    open(report, "w").write(text)
PY
