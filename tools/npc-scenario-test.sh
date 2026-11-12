#!/usr/bin/env bash
# Test PNJ sur un vrai serveur Fabric avec un modèle HTTP simulé, sans clé ni modèle téléchargé.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=test-server-lib.sh
source "$ROOT/tools/test-server-lib.sh"
prepare_test_workdir "${1:-}" "${2:-}"
"$ROOT/tools/smoke_server.sh" "$JAR" "$WORK"
cd "$WORK"
rm -rf world
rm -f mock-port mock-requests.jsonl
MOCK_PID=""
cleanup() {
  cleanup_test_server
  if [ -n "$MOCK_PID" ]; then
    kill "$MOCK_PID" 2>/dev/null || true
    wait "$MOCK_PID" 2>/dev/null || true
  fi
  rm -f mods/carpet.jar
}
trap cleanup EXIT
python3 "$ROOT/tools/mock_npc_ai.py" --ready "$WORK/mock-port" --log "$WORK/mock-requests.jsonl" > mock.log 2>&1 &
MOCK_PID=$!
for _ in $(seq 100); do
  [ -s mock-port ] && break
  kill -0 "$MOCK_PID" 2>/dev/null || break
  sleep 0.1
done
[ -s mock-port ] || { cat mock.log; exit 1; }
python3 - "$(cat mock-port)" <<'PY'
import json, sys
from pathlib import Path
Path('config/terracraft-npcs.json').write_text(json.dumps({
    'ai_enabled': True, 'provider': 'ollama', 'model': 'pnj-test',
    'endpoint': f'http://127.0.0.1:{int(sys.argv[1])}/api/chat', 'timeout_seconds': 1,
}))
PY
curl -fsSL -o mods/carpet.jar 'https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar'

start_game() {
  start_test_server 1536M "$1"
  trap cleanup EXIT
  for _ in $(seq 600); do
    grep -q 'Done (' "$1" && return 0
    kill -0 "$TEST_SERVER_PID" 2>/dev/null || break
    sleep 1
  done
  tail -40 "$1"
  return 1
}
cmd() { echo "$1" >&"$TEST_INPUT"; sleep "${2:-1}"; }
spawn_player() {
  cmd 'player Alice spawn at 0 201 0'
  for _ in $(seq 30); do
    grep -qi 'alice\[local\] logged in' "$1" && return 0
    kill -0 "$TEST_SERVER_PID" 2>/dev/null || break
    sleep 1
  done
  tail -40 "$1"
  return 1
}

start_game npc.log
cmd 'forceload add -16 -16 16 16' 3
cmd 'fill -8 200 -8 8 200 8 minecraft:stone' 2
spawn_player npc.log
cmd 'gamemode survival Alice'
cmd 'execute as Alice at Alice run pnj creer lea'
cmd 'execute as Alice at Alice run pnj dire BONJOUR' 5
cmd 'execute as Alice at Alice run pnj reclamer'
cmd 'execute as Alice at Alice run pnj dire INVALIDE' 5
cmd 'execute as Alice at Alice run pnj dire PANNE' 5
cmd 'execute as Alice at Alice run pnj dire LENT' 5
cmd 'say PNJ_SERVEUR_DISPONIBLE'
cmd 'execute as Alice at Alice run pnj dire SOUVENIR' 5
stop_test_server 90
cleanup_test_server

start_game npc-restart.log
spawn_player npc-restart.log
cmd 'execute as Alice at Alice run pnj dire RETOUR' 5
cmd 'execute as Alice at Alice run pnj reclamer'
stop_test_server 90

python3 - "$WORK" <<'PY'
import json, sys
from pathlib import Path
work = Path(sys.argv[1])
log = (work/'npc.log').read_text(errors='replace')
restart = (work/'npc-restart.log').read_text(errors='replace')
data = work/'world/terracraft_geo'
memory = json.loads((data/'pnj-memoires.json').read_text())
requests = [json.loads(line) for line in (work/'mock-requests.jsonl').read_text().splitlines()]
def check(condition, message):
    print(('ok : ' if condition else 'ÉCHEC : ') + message)
    if not condition:
        raise SystemExit(1)
check('IA_BONJOUR' in log, 'dialogue HTTP en français depuis un mob identifié')
check(len(memory) == 1, 'même mémoire joueur/PNJ conservée au redémarrage')
saved = next(iter(memory.values()))
check('MEMOIRE_REPRISE' in restart, 'souvenir transmis au modèle après redémarrage')
check(len(saved['history']) <= 6, 'mémoire conversationnelle bornée')
check(any('Léa' in r['identity']['name'] for r in requests), 'identité pré-promptée')
check({r['message'] for r in requests} >= {'BONJOUR','INVALIDE','PANNE','LENT','SOUVENIR','RETOUR'}, 'tous les cas testés ont atteint le modèle simulé')
check(log.count('[PNJ] Réponse locale utilisée') >= 3, 'contrat inventé, panne HTTP et délai déclenchent le repli')
check('PNJ_SERVEUR_DISPONIBLE' in log, 'serveur actif après expiration du délai IA')
check(saved['quest'] in {q['quest_id'] for q in requests[-1]['available_quests']}, 'contrat conservé choisi parmi les contrats autorisés')
check('contrat:invente' not in json.dumps(memory), 'contrat inventé jamais persisté')
balances = json.loads((data/'balances.json').read_text())
check(sum(balances.values()) == 1000, 'réclamation prématurée répétée : aucun crédit ajouté')
PY
