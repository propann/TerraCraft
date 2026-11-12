#!/usr/bin/env bash
# Campagne sur Fabric : preuves, livre, gardien, permissions, reçus et redémarrage.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=test-server-lib.sh
source "$ROOT/tools/test-server-lib.sh"
prepare_test_workdir "${1:-}" "${2:-}"
"$ROOT/tools/smoke_server.sh" "$JAR" "$WORK"
cd "$WORK"
rm -rf world
cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    for log in campaign.log campaign-restart.log; do
      if [ -f "$log" ]; then
        echo "Journal de diagnostic : $log"
        tail -100 "$log"
      fi
    done
    for data in world/terracraft_geo/terra-exodus.json world/terracraft_geo/portefeuilles.json; do
      if [ -f "$data" ]; then cat "$data"; fi
    done
  fi
  cleanup_test_server
  rm -f mods/carpet.jar
}
trap cleanup EXIT
curl -fsSL -o mods/carpet.jar 'https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar'
# Les préparations sont explicites : une fouille préexistante et une arme donnée par la console.
python3 - "$ROOT" <<'PY'
import hashlib,json,re,sys,uuid
from pathlib import Path
player=str(uuid.UUID(bytes=hashlib.md5(b'OfflinePlayer:Alice').digest(),version=3))
root=Path('world/terracraft_geo');root.mkdir(parents=True)
(root/'progression.json').write_text(json.dumps({player:{'stats':{'loot':1}}}))
Path('alice-id').write_text(player)
Path('usercache.json').write_text(json.dumps([{'name':'Alice','uuid':player,'expiresOn':'2099-12-31 23:59:59 +0000'}]))
source=(Path(sys.argv[1])/'geo-mod/src/main/java/com/terracraft/geo/ExodusQuests.java').read_text()
ids=re.findall(r'q\("(q[1-5]_[a-z0-9_]+)"',source)
Path('setup-quests').write_text('\n'.join(q for q in ids if q not in {'q1_1_ashes','q4_3_orion_glyphs','q5_2_sanctuary','q5_3_artifact'})+'\n')
# Premier centre contaminé de la zone environnante, même arithmétique entière signée que Java.
mask=(1<<64)-1
for x in range(-2,3):
 for z in range(-2,3):
  h=((x*0x9E3779B97F4A7C15)^(z*0xC2B2AE3D27D4EB4F)^0x5DEECE66D)&mask
  h^=h>>31;h=(h*0xBF58476D1CE4E5B9)&mask;h^=h>>29
  signed=h if h<(1<<63) else h-(1<<64)
  if signed%100<18:
   Path('test-zone').write_text(f'{x*1024+160+(h>>8)%704} {z*1024+160+(h>>28)%704}\n')
   raise SystemExit
raise SystemExit('Aucune zone contaminée de test')
PY
EXODUS_ALICE_ID="$(cat alice-id)"
read -r EXODUS_ZONE_X EXODUS_ZONE_Z < test-zone
start_game() {
  start_test_server 1536M "$1"
  trap cleanup EXIT
  for _ in $(seq 600); do
    grep -q 'Done (' "$1" && return 0
    kill -0 "$TEST_SERVER_PID" 2>/dev/null || break
    sleep 1
  done
  tail -50 "$1"
  return 1
}
cmd() { echo "$1" >&"$TEST_INPUT"; sleep "${2:-0.5}"; }
players() {
  cmd 'player Alice spawn at 0 201 0' 2
  cmd 'player Bob spawn at 3 201 0' 2
  for _ in $(seq 30); do
    if grep -qi 'alice\[local\] logged in' "$1" && grep -qi 'bob\[local\] logged in' "$1"; then return 0; fi
    sleep 1
  done
  return 1
}
start_game campaign.log
cmd 'forceload add -16 -16 16 16' 2
cmd 'fill -8 200 -8 8 200 8 minecraft:stone' 1
players campaign.log
cmd 'gamemode survival Alice'
cmd 'gamemode survival Bob'
cmd 'effect give Bob minecraft:resistance 99999 255 true'
cmd 'effect give Alice minecraft:resistance 99999 255 true'
cmd 'give Alice minecraft:stone_sword'
cmd 'execute as Alice run exodus livre'
cmd 'execute as Alice run exodus livre'
cmd 'scoreboard objectives add exodus_test dummy'
cmd 'execute store result score book_count exodus_test run clear Alice minecraft:written_book 0'
cmd 'scoreboard players get book_count exodus_test'
cmd 'data get entity Alice Inventory' 1
cmd 'execute as Alice at Alice run pnj creer helene_vance'
cmd 'execute as Alice at Alice run pnj dire Bonjour commandant' 1
cmd 'execute as Alice run exodus reclamer q5_3_artifact'
cmd 'execute as Alice run exodus reclamer inconnue'
cmd 'execute as Alice run exodus reclamer q1_1_ashes'
cmd 'execute as Alice run exodus reclamer q1_1_ashes'
# Une vraie raffinerie produit à partir d'un réservoir de brut de test.
cmd 'time set 6000'
cmd 'setblock 2 201 0 terracraft_geo:refinery'
cmd 'setblock 2 202 0 terracraft_geo:solar_panel'
cmd 'setblock 2 201 1 terracraft_geo:solar_panel'
cmd 'data merge block 2 201 0 {CRUDE:4000}'
cmd 'item replace entity Alice weapon.mainhand with minecraft:air'
cmd 'player Alice look at 2.5 201.5 0.5'
cmd 'player Alice use once' 4
cmd 'player Bob look at 2.5 201.5 0.5'
cmd 'player Bob use once' 4
cmd 'setblock 2 201 0 minecraft:air'
cmd 'give Alice terracraft_geo:fuel_can 10'
# Les autres étapes intermédiaires sont des fixtures administrateur sans paiement.
while IFS= read -r quest; do cmd "exodus admin valider Alice $quest" 0.1; done < setup-quests
# Archive martienne réellement générée, puis récolte d'une serre alimentée sur Mars.
cmd 'execute in terracraft_geo:mars run forceload add 128 128 143 143' 5
cmd 'execute in terracraft_geo:mars positioned 136 0 136 positioned over motion_blocking run tp Alice ~ ~ ~' 2
cmd 'execute as Alice at Alice run exodus archives'
cmd 'execute as Alice run exodus reclamer q4_3_orion_glyphs'
cmd 'execute at Alice run setblock ~2 ~ ~ terracraft_geo:greenhouse'
cmd 'execute at Alice run setblock ~3 ~ ~ minecraft:barrel'
cmd 'execute at Alice run setblock ~2 ~1 ~ terracraft_geo:solar_panel'
cmd 'execute at Alice run setblock ~2 ~ ~1 terracraft_geo:battery_bank'
cmd 'execute at Alice run data merge block ~2 ~ ~1 {Charge:1200}'
cmd 'execute at Alice run player Alice look at ~2.5 ~0.5 ~0.5'
cmd 'player Alice use once'
cmd 'execute at Alice run data merge block ~2 ~ ~ {Growth:44}' 4
cmd 'execute in terracraft_geo:asteroids run forceload add 128 128 143 143' 5
cmd 'execute in terracraft_geo:asteroids run tp Alice 136 129 136' 2
cmd 'execute as Alice at Alice run exodus epreuve' 2
cmd 'execute in terracraft_geo:asteroids run damage @e[tag=terracraft_orion_guardian,limit=1] 500 minecraft:player_attack by Alice' 2
cmd 'execute as Alice run exodus reclamer q5_2_sanctuary'
cmd 'execute as Alice run exodus reclamer q5_2_sanctuary'
cmd 'execute as Alice run exodus noyau'
cmd 'execute as Alice run exodus noyau'
# Fondation au cœur d'une contamination naturelle ; aucune combinaison équipée.
cmd "execute in minecraft:overworld run forceload add $EXODUS_ZONE_X $EXODUS_ZONE_Z" 2
cmd "execute in minecraft:overworld run fill $((EXODUS_ZONE_X - 4)) 249 $((EXODUS_ZONE_Z - 4)) $((EXODUS_ZONE_X + 4)) 249 $((EXODUS_ZONE_Z + 4)) minecraft:stone"
cmd "execute in minecraft:overworld run tp Alice $EXODUS_ZONE_X 250 $EXODUS_ZONE_Z" 1
cmd "execute in minecraft:overworld run tp Bob $EXODUS_ZONE_X 250 $EXODUS_ZONE_Z"
cmd 'execute as Alice at Alice run ville creer Refuge Orion'
cmd 'execute as Alice run confirmer' 1
cmd 'execute as Alice run ville inviter Bob'
cmd 'execute as Bob run ville rejoindre'
cmd 'execute at Alice run setblock ~2 ~ ~ terracraft_geo:town_registry'
cmd 'effect clear Alice'
cmd 'effect give Alice minecraft:resistance 99999 255 true' 3
cmd 'execute store success score poison_before exodus_test if entity @a[name=Alice,nbt={active_effects:[{id:"minecraft:poison"}]}]'
cmd 'scoreboard players get poison_before exodus_test'
cmd 'item replace entity Alice weapon.mainhand with terracraft_geo:alien_artifact'
cmd 'execute as Alice run exodus contribuer'
cmd 'execute as Alice run exodus reclamer q5_3_artifact' 10
cp world/terracraft_geo/terra-exodus.json rejected-core.json
# Une copie administrative conserve le nonce authentifié : un seul dépôt reste possible.
EXODUS_CORE_ID="$(python3 - "$EXODUS_ALICE_ID" <<'PYCORE'
import json,sys
from pathlib import Path
print(json.loads(Path('world/terracraft_geo/terra-exodus.json').read_text())['players'][sys.argv[1]]['core'])
PYCORE
)"
cmd "item replace entity Alice weapon.mainhand with terracraft_geo:alien_artifact[minecraft:custom_data={orion_owner:\"$EXODUS_ALICE_ID\",orion_core:\"$EXODUS_CORE_ID\"}]"
cmd 'execute as Alice run exodus contribuer' 1
cmd 'execute as Alice run exodus retirer'
cp world/terracraft_geo/terra-exodus.json withdrawn-core.json
cmd 'execute as Alice run exodus noyau'
cmd "item replace entity Alice weapon.mainhand with terracraft_geo:alien_artifact[minecraft:custom_data={orion_owner:\"$EXODUS_ALICE_ID\",orion_core:\"$EXODUS_CORE_ID\"}]"
cmd 'execute as Alice run exodus contribuer' 1
cmd 'execute as Alice run exodus contribuer'
cmd 'execute as Bob run exodus restaurer'
cmd 'execute as Alice run exodus reclamer q5_3_artifact'
stop_test_server 90
cleanup_test_server
cp world/terracraft_geo/terra-exodus.json deposited-core.json
cp world/terracraft_geo/portefeuilles.json first-wallet.json
# Injection d'un état de reprise : versement déjà inscrit, fin de quête encore en attente.
python3 - "$EXODUS_ALICE_ID" <<'PYREPLAY'
import json,sys
from pathlib import Path
file=Path('world/terracraft_geo/terra-exodus.json')
data=json.loads(file.read_text());journal=data['players'][sys.argv[1]]
journal['done'].remove('q1_1_ashes');journal['pending']='q1_1_ashes'
text=json.dumps(data);file.write_text(text);Path('pending-replay.json').write_text(text)
PYREPLAY

start_game campaign-restart.log
players campaign-restart.log
cmd "execute in minecraft:overworld run tp Alice $EXODUS_ZONE_X 250 $EXODUS_ZONE_Z"
cmd "execute in minecraft:overworld run tp Bob $EXODUS_ZONE_X 250 $EXODUS_ZONE_Z"
cmd 'execute as Bob run exodus restaurer'
cmd 'execute as Alice run exodus contribuer'
cmd 'execute as Alice run exodus restaurer' 1
cmd 'effect clear Alice' 3
cmd 'execute store success score poison_after exodus_test if entity @a[name=Alice,nbt={active_effects:[{id:"minecraft:poison"}]}]'
cmd 'scoreboard players get poison_after exodus_test'
cmd 'execute as Alice run exodus reclamer q5_3_artifact'
cmd 'execute as Alice run exodus reclamer q5_3_artifact'
cmd 'execute as Alice run exodus restaurer'
cmd 'execute as Alice run exodus retirer'
cmd 'execute as Alice run exodus livre'
cmd 'execute store result score book_count exodus_test run clear Alice minecraft:written_book 0'
cmd 'scoreboard players get book_count exodus_test'
cmd 'save-all flush' 1
cp world/terracraft_geo/terra-exodus.json completed-campaign.json
# Une remise à zéro de progression ne réinitialise ni paiements ni noyaux consommés.
cmd 'exodus admin reinitialiser Alice'
cmd 'give Alice minecraft:stone_sword'
cmd 'execute as Alice run exodus reclamer q1_1_ashes'
stop_test_server 90
python3 "$ROOT/tools/check_campaign_scenario.py" "$WORK"
