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
tail -f console.in | java -Xmx1536M -jar server.jar nogui > scenario.log 2>&1 &
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
cmd "execute as Alice at Alice run ville creer Bourg Neuf" 1
cmd "execute as Alice run confirmer" 2
cmd "execute as Alice run ville inviter Bob"
cmd "execute as Bob run ville rejoindre"
cmd "execute as Bob run ville deposer 200"
cmd "execute as Alice run ville payer Bob 50"
cmd "execute as Alice run ville adjoint Bob" 1
cmd "execute as Bob run ville payer Alice 10" 1
cmd "execute as Bob run tp Bob 900 220 900" 2
cmd "execute as Bob run ville tp" 2
# Économie
cmd "execute as Bob run comptoir 1"
cmd "item replace entity Bob weapon.mainhand with terracraft_geo:fuel_can 1"
cmd "execute as Bob run hdv vendre 100"
cmd "execute as Alice run hdv acheter 1"
# Bêta : modérateur, avertissement, suivi de l'activité
cmd "moderateurs ajouter Bob" 1
cmd "execute as Bob run mod avertir Alice Test de moderation" 1
cmd "terracraft suivi" 1
cmd "terracraft course demarrer" 1
# Métier, signalement, largage militaire
cmd "execute as Bob run metier choisir pilote"
cmd "execute as Alice run metier choisir mecanicien"
cmd "execute as Bob run signaler Test automatique : tout va bien"
cmd "execute as Alice at Alice run terracraft largage" 2
# Phase 6 : convoi militaire en panne (camion, escorte) puis zone contaminée (poison sans combinaison)
cmd "effect give Alice minecraft:resistance 120 255 true" 1
cmd "execute as Alice at Alice run terracraft convoi" 3
cmd "execute if entity @e[type=terracraft_geo:truck] run say CONVOI_CAMION" 1
cmd "execute at Alice if entity @e[type=minecraft:zombie,distance=..48] run say CONVOI_GARDES" 1
cmd "kill @e[type=#minecraft:skeletons]" 0
cmd "kill @e[type=minecraft:zombie]" 0
cmd "kill @e[type=minecraft:pillager]" 1
cmd "execute as Alice run terracraft contamination" 1
cmd "execute as Alice run terracraft contamination aller" 8
cmd "effect clear Alice minecraft:poison" 1
cmd "tp Alice 0 201 0" 2
# PvE par défaut : Alice ne peut pas blesser Bob ; dans une zone PvP, si
cmd "tp Bob 3 201 3" 2
cmd "damage Bob 2 minecraft:player_attack by Alice" 1
cmd "execute as Alice at Alice run pvp creer 30 Arene de test" 1
cmd "damage Bob 2 minecraft:player_attack by Alice" 1
cmd "execute as Alice run pvp supprimer Arene de test" 1
cmd "effect give Bob minecraft:instant_health 1 3 true" 1
# Vague nocturne forcée sur Bourg Neuf : trois vagues repoussées, récompense à la trésorerie
cmd "effect give Alice minecraft:resistance 300 255 true" 1
cmd "effect give Bob minecraft:resistance 300 255 true" 1
cmd "execute as Alice run terracraft vague" 13
cmd "kill @e[type=minecraft:zombie]" 0
cmd "kill @e[type=#minecraft:skeletons]" 0
cmd "kill @e[type=minecraft:spider]" 0
cmd "kill @e[type=minecraft:vindicator]" 3
cmd "kill @e[type=minecraft:zombie]" 0
cmd "kill @e[type=#minecraft:skeletons]" 0
cmd "kill @e[type=minecraft:spider]" 0
cmd "kill @e[type=minecraft:vindicator]" 3
cmd "kill @e[type=minecraft:zombie]" 0
cmd "kill @e[type=#minecraft:skeletons]" 0
cmd "kill @e[type=minecraft:spider]" 0
cmd "kill @e[type=minecraft:vindicator]" 3
# Étal de marché : Bob vend des diamants à 25 l'unité, Alice en achète 2 (50 crédits passent d'Alice à Bob)
cmd "tp Bob 3 201 3" 1
cmd "setblock 6 201 0 terracraft_geo:market_stall" 1
cmd "execute as Bob run etal prix 25" 1
cmd "item replace entity Bob weapon.mainhand with minecraft:diamond 5" 1
cmd "execute as Bob run etal ajouter" 1
cmd "tp Alice 5 201 1" 1
cmd "execute as Alice run etal acheter 2" 1
# Boss de bunker (Alice l'abat : +300) puis prime de Bob sur Alice, gagnée par Bob en zone PvP
cmd "execute as Alice at Alice run terracraft boss" 2
cmd "execute if entity @e[tag=terracraft_boss] run say BOSS_PRESENT" 1
cmd "damage @e[tag=terracraft_boss,limit=1] 500 minecraft:player_attack by Alice" 2
cmd "execute as Bob run prime Alice 100" 1
cmd "execute as Alice at Alice run pvp creer 30 Arene des primes" 1
cmd "tp Bob 3 201 3" 1
cmd "effect clear Alice minecraft:resistance" 1
cmd "damage Alice 1000 minecraft:player_attack by Bob" 3
cmd "execute as Bob run pvp supprimer Arene des primes" 1
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
# Vol 1 : depuis la Terre, l'orbite lunaire est refusée ; l'orbite terrestre avec le kit déploie la station
cmd "tp Bob 5.5 201 5.5" 2
cmd "summon terracraft_geo:rocket 10.5 201 10.5 {Parts:15b,Fuel:12,Tanks:2b,Target:11b}" 2
cmd "ride Bob mount @e[type=terracraft_geo:rocket,limit=1,sort=nearest]" 2
cmd "execute as Bob run terracraft fusee decoller" 2
cmd 'execute as Bob at Bob run data merge entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] {Target:2b,Payload:[{id:"terracraft_geo:orbital_station_kit",count:1}]}' 1
cmd "execute as Bob run terracraft fusee decoller" 25
cmd "data get entity Bob Dimension" 1
cmd "execute in terracraft_geo:orbit if block -18 151 6 terracraft_geo:station_workshop" 1
cmd "execute in terracraft_geo:orbit if block -8 151 10 terracraft_geo:airlock_door" 1
cmd "execute in terracraft_geo:orbit if block 8 150 10 terracraft_geo:station_beacon" 1
cmd "execute in terracraft_geo:orbit if block -14 150 10 terracraft_geo:oxygen_distributor" 1
# Vol 2 : du quai de la station vers l'orbite lunaire (balise de Bob) : 12 - 3 - 2 = 7 doses
cmd "execute as Bob at Bob run data merge entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] {Target:11b}" 1
cmd "execute as Bob run terracraft fusee decoller" 25
cmd "data get entity Bob Dimension" 1
cmd "data get entity Bob Pos" 1
cmd "execute as Bob at Bob run data get entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] Fuel" 1
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
# Vol 3 : du quai de l'orbite lunaire vers la Lune, avec base lunaire et rover en charges utiles (1 dose)
cmd 'execute as Bob at Bob run data merge entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] {Target:1b,Payload:[{id:"terracraft_geo:lunar_base_kit",count:1},{id:"terracraft_geo:rover_kit",count:1}]}' 1
cmd "execute as Bob run terracraft fusee decoller" 32
cmd "data get entity Bob Dimension" 1
cmd "execute if entity @e[type=terracraft_geo:rover]" 1
cmd "execute as Bob at Bob run data get entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] Fuel" 1
# Dangers lunaires : pluie de micrométéorites sur Bob, à découvert sur l'aire de sa base (protégé pour le test)
cmd "effect give Bob minecraft:resistance 300 255 true" 1
cmd "ride Bob dismount" 1
cmd "execute in terracraft_geo:moon run tp Bob -2 151 -5" 2
cmd "execute as Bob run terracraft meteores" 25
cmd "execute in terracraft_geo:moon run fill -3 154 -6 -1 154 -4 minecraft:stone" 1
cmd "say ABRI_DEBUT" 12
cmd "say ABRI_FIN" 1
# Protection de la base lunaire de Bob : Eve (étrangère, créatif, non opératrice) ne peut pas casser ; Bob oui
cmd "execute in terracraft_geo:moon run setblock 3 152 -4 minecraft:glass" 1
cmd "player Eve spawn at 3 151 -6 facing 0 0 in terracraft_geo:moon in creative" 4
cmd "player Eve attack once" 2
cmd "execute in terracraft_geo:moon if block 3 152 -4 minecraft:glass run say PROTECTION_ETRANGERE_OK" 1
cmd "player Eve kill" 1
cmd "execute in terracraft_geo:moon run tp Bob 3 151 -6 0 0" 2
cmd "player Bob attack continuous" 3
cmd "player Bob stop" 1
cmd "execute in terracraft_geo:moon if block 3 152 -4 minecraft:air run say PROTECTION_PROPRIETAIRE_OK" 1
# Carte des étoiles (mêmes règles via /fusee cap) : Mars refusé sans navigation martienne, orbite lunaire prête
cmd "execute as Bob run fusee cap mars" 1
cmd "execute as Bob run fusee cap lune" 1
cmd "execute as Bob run fusee cap orbite_lunaire" 1
cmd "execute as Bob at Bob run data get entity @e[type=terracraft_geo:rocket,limit=1,sort=nearest] Target" 1
# Sous-sol lunaire : visite du sanctuaire le plus proche (téléportation au pied du puits, face à la pyramide)
cmd "effect give Bob minecraft:resistance 120 255 true" 1
cmd "execute as Bob run terracraft sanctuaire" 10
cmd "execute as Bob run terracraft sanctuaire" 0
cmd "execute at Bob if block ~-19 ~1 ~ minecraft:chest run say SANCTUAIRE_COFFRE" 1
cmd "execute as Bob run terracraft sanctuaire" 0
cmd "execute at Bob if block ~ ~-1 ~ terracraft_geo:alien_stone run say SANCTUAIRE_DALLAGE" 1
cmd "execute as Bob run terracraft sanctuaire" 0
cmd "execute at Bob if block ~4 ~2 ~ minecraft:ladder run say SANCTUAIRE_ECHELLE" 1
cmd "execute at Bob if entity @e[type=terracraft_geo:lost_astronaut,distance=..40] run say SANCTUAIRE_GARDIENS" 1
# Visuel : équipes de ville (préfixe) et cycle jour/nuit à vitesse normale (~100 ticks en 5 s)
cmd "team list" 1
cmd "team list tc_bourg_neuf" 1
cmd "time query time" 5
cmd "time query time" 1
python3 "$ROOT/tools/ping_server.py" localhost 25599 > ping.json 2>&1 || true
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
# Préfixe de ville devant les pseudos (équipes, 0.19) : retiré pour que les messages restent comparables.
log = re.sub(r"\[Bourg Neuf\] (?=(Alice|Bob)\b)", "", log)
failures = []

def check(condition, message):
    print(("  ok    " if condition else "  ÉCHEC ") + message)
    if not condition:
        failures.append(message)

towns = json.loads((data / "villes.json").read_text())
eco_created = json.loads((data / "economie.json").read_text())["created"]
check(len(towns) == 1 and towns[0]["name"] == "Bourg Neuf", "ville fondée")
check(len(towns[0]["members"]) == 2, "Bob a rejoint la ville")
check(towns[0]["treasury"] == 140 + eco_created.get("vague_nocturne", 0), "trésorerie = 200 déposés - 50 et 10 payés (+ récompense de vague)")
balances = sorted(json.loads((data / "balances.json").read_text()).values())
race = json.loads((data / "course.json").read_text())
race_bob = sum(w["reward"] for w in race["winners"].values() if w["name"] == "Bob")
check(balances == sorted([660, 938 + race_bob]), f"soldes Alice 400 + 10 de l'adjoint + 300 de boss - 50 à l'étal / Bob 888 + 50 de l'étal + {race_bob} de course (prime de 100 posée puis gagnée) (obtenu {balances})")
eco = json.loads((data / "economie.json").read_text())
created, destroyed = sum(eco["created"].values()), sum(eco["destroyed"].values())
check(created == sum(balances) + towns[0]["treasury"] + destroyed, "aucun crédit perdu ni créé : créés = comptes + trésorerie + détruits")
check(eco["destroyed"].get("frais_hdv") == 2 and eco["destroyed"].get("comptoir") == 60, "frais de 2 % et comptoir")
check(re.search(r"Teleported Bob|Bienvenue au centre", log) is not None or "ville tp" in log, "retour au centre-ville")
suit = re.findall(r'"terracraft_geo:space_suit": \[(.*?)\]\}', log)
check(bool(suit) and "space_helmet" in suit[-1] and "jetpack" in suit[-1] and "oxygen_tank" in suit[-1],
      "casque, bouteilles et jetpack portés par clic droit")
check(bool(suit) and '"minecraft:damage"' in suit[-1], "le casque consomme de l'oxygène sur la Lune")
health = [float(h) for h in re.findall(r"Bob has the following entity data: (\d+\.\d+)f", log)]
# Santé pleine (20, ou plus si un palier a ajouté des cœurs) : l'oxygène a protégé Bob sur la Lune.
check(bool(health) and health[0] >= 20, f"Bob respire (aucun dégât, santé {health[:1]})")
progression = json.loads((data / "progression.json").read_text())
jobs = sorted(r.get("job") for r in progression.values() if r.get("job"))  # Eve (test de protection) n'a pas de métier
check(jobs == ["MECANICIEN", "PILOTE"], f"métiers enregistrés (obtenu {jobs})")
stats = [r.get("stats", {}) for r in progression.values()]
check(any(s.get("listings") == 1 for s in stats) and any(s.get("shop") == 1 for s in stats), "compteurs vente et comptoir")
check(any(s.get("town") == 1 for s in stats) and any(s.get("job") == 1 for s in stats), "compteurs ville et métier")
contracts = json.loads((data / "contrats.json").read_text())
check(len(contracts) >= 2 and all("baseline" in c for c in contracts.values()), "journée de contrats ouverte pour chaque joueur")
reports = json.loads((data / "signalements.json").read_text())
check(len(reports) == 1 and reports[0]["player"] == "Bob", "signalement enregistré")
check("[LARGAGE] Caisse en" in log, "caisse de ravitaillement larguée sur la plateforme")
check(log.count("Test passed") >= 2, "module de station : distributeur d'oxygène au centre, porte ouverte")
check(any(s.get("modules") == 1 for s in stats), "compteur de modules de station")
stations = json.loads((data / "stations.json").read_text())
check(any(s["dimension"] == "terracraft_geo:moon_orbit" for s in stations), "balise de station enregistrée en orbite lunaire")
check("Depuis la Terre, cap sur l'orbite terrestre" in log or log.count("Décollage refusé") >= 2,
      "depuis la Terre, la Lune est refusée : la station orbitale d'abord")
check('Bob has the following entity data: "terracraft_geo:orbit"' in log, "premier vol : orbite terrestre")
check(log.count("Test passed") >= 6, "station orbitale déployée : atelier, sas, balise du quai, oxygène")
check(any(s["dimension"] == "terracraft_geo:orbit" and s["name"].startswith("Station orbitale") for s in stations),
      "station orbitale enregistrée au nom du pilote")
check('Bob has the following entity data: "terracraft_geo:moon_orbit"' in log, "second vol, depuis le quai : orbite lunaire")
pos = re.findall(r"Bob has the following entity data: \[([-\d.]+)d, ([-\d.]+)d, ([-\d.]+)d\]", log)
check(bool(pos) and abs(float(pos[-1][0]) - 2.5) < 1 and abs(float(pos[-1][2]) + 2.5) < 1, "fusée posée à côté de la balise de station")
check("Rocket has the following entity data: 7" in log, "carburant : fusée moyenne (12) − Terre→orbite (3) − orbite→orbite lunaire (2) = 7")
plans = sorted(p for r in progression.values() for p in (r.get("plans") or []))
check(plans == ["CARGO", "ION", "TANK"], f"plans débloqués par l'exploration, pas la navigation martienne (obtenu {plans})")
check("Rocket has the following entity data: 7b" in log, "atelier : réservoir étendu, moteur ionique et soute installés")
check(log.count("[ATELIER]") == 3, "trois installations journalisées, navigation martienne refusée")
check("Décollage refusé" in log, "vers Mars sans navigation martienne : décollage refusé")
check('Bob has the following entity data: "terracraft_geo:moon"' in log, "troisième vol : atterrissage sur la Lune depuis l'orbite lunaire")
check(any(s["dimension"] == "terracraft_geo:moon" and s["name"].startswith("Base lunaire") for s in stations),
      "base lunaire déployée à l'atterrissage et enregistrée")
check("Rover déposé" in log or log.count("Test passed") >= 7, "rover lunaire déposé à côté de la fusée")
check("Rocket has the following entity data: 6" in log, "orbite lunaire → Lune : 1 dose (moteur ionique), 6 restantes")
check("[NAV] Bob met le cap sur Mars : " in log and "navigation martienne" in log.split("[NAV] Bob met le cap sur Mars")[1].split("\n")[0],
      "carte des étoiles : Mars signalé « navigation martienne requise »")
check("[NAV] Bob met le cap sur l'orbite lunaire : 1 dose(s), prête" in log, "carte des étoiles : retour en orbite lunaire prêt (1 dose)")
check("[NAV] Bob met le cap sur la Lune" not in log and "Rocket has the following entity data: 11b" in log,
      "carte des étoiles : destination actuelle refusée, cap enregistré sur la fusée")
check("[METEORES] pluie annoncée dans 5 s" in log and "[METEORES] pluie de micrométéorites sur la Lune" in log,
      "micrométéorites : pluie annoncée puis déclenchée sur la Lune")
check("[METEORES] Bob touché à découvert" in log, "micrométéorites : un joueur à découvert est touché")
abri = log.split("ABRI_DEBUT")[-1].split("ABRI_FIN")[0] if "ABRI_DEBUT" in log else "?"
check(abri != "?" and "Bob touché" not in abri, "micrométéorites : un toit au-dessus de la tête protège")
check("Team [Bourg Neuf] has 2 member(s): Alice, Bob" in log, "équipe de ville « Bourg Neuf » avec Alice et Bob (préfixe)")
times = [int(t) for t in re.findall(r"Clock minecraft:overworld is at (\d+) tick", log)][-2:]
check(len(times) == 2 and 60 <= (times[1] - times[0]) % 24000 <= 200,
      f"jour/nuit à vitesse normale : {times[1] - times[0] if len(times) == 2 else '?'} ticks en 5 s")
progress = json.loads((data / "progression.json").read_text())
check("[CONVOI] camion en" in log and "CONVOI_CAMION" in log, "convoi militaire : camion en panne posé et annoncé")
check("CONVOI_GARDES" in log, "convoi militaire : escorte armée présente")
check("Zone contaminée la plus proche" in log, "zones contaminées : zone la plus proche trouvée")
check("Removed effect Poison from Alice" in log or "Removed effect Poison from [Bourg Neuf] Alice" in log,
      "zone contaminée : poison sans casque ni combinaison")
check(any("contamination" in r.get("discoveries", []) for r in progress.values()), "découverte « Compteur Geiger »")

damages = re.findall(r"(Applied 2\.0 damage to Bob|Target is invulnerable to the given damage type)", log)
check(damages[:2] == ["Target is invulnerable to the given damage type", "Applied 2.0 damage to Bob"],
      f"PvE par défaut, PvP dans une zone déclarée (obtenu {damages[:2]})")
check("[VAGUE] Bourg Neuf : vague 3/3" in log, "vague nocturne : trois vagues lancées sur Bourg Neuf")
check("ville défendue" in log and eco_created.get("vague_nocturne") == 250, "vague nocturne repoussée : 250 crédits pour la trésorerie")
check(any("rampart" in r.get("discoveries", []) for r in progress.values()), "découverte « Rempart »")
check("BOSS_PRESENT" in log and "[BOSS] Commandant abattu" in log, "boss de bunker : invoqué puis abattu")
check(eco_created.get("recompenses", 0) >= 300 and any("bunker_boss" in r.get("discoveries", []) for r in progress.values()),
      "boss de bunker : prime de 300 crédits et découverte")
check("[PRIME] Bob met 100 crédits sur Alice" in log and "[PRIME] Bob touche 100 crédits pour Alice" in log,
      "prime posée puis gagnée en zone PvP")
check(json.loads((data / "primes.json").read_text()) == {}, "plus aucune prime en attente après le paiement")
check("[ETAL] Bob fixe le prix à 25" in log and "[ETAL] Alice achète Diamond x2 à Bob pour 50 crédits" in log,
      "étal de marché : Bob fixe le prix, Alice achète 2 diamants (50 crédits à Bob)")
check("[VILLE] Alice nomme Bob adjoint de Bourg Neuf" in log and "[VILLE] Bob paie 10 crédits à Alice depuis Bourg Neuf" in log,
      "rôles de ville : Bob nommé adjoint puis paie depuis la trésorerie")
check(towns[0].get("deputies") == [str(uuid) for uuid in towns[0]["members"] if uuid != towns[0]["mayor"]],
      "l'adjoint est enregistré dans villes.json")
check("nomme modérateur Bob" in log and "avertit Alice : Test de moderation" in log
      and list(json.loads((data / "moderateurs.json").read_text()).values()) == ["Bob"], "modération : Bob nommé modérateur, avertissement journalisé")
check("[SUIVI] 2 joueurs uniques" in log, "suivi de l'activité : 2 joueurs uniques")
activity = json.loads((data / "activite.json").read_text())
check(len(activity) == 3 and all(r["sessions"] >= 1 for r in activity.values()), "activite.json : sessions enregistrées (Alice, Bob, Eve)")
check(sorted(race["winners"]) == sorted(["orbit", "orbital_station", "moon", "lunar_base", "alien_sanctuary"])
      and race_bob == 1300 and race["active"], f"course à l'espace : Bob remporte 5 étapes, 1 300 crédits (obtenu {sorted(race['winners'])})")
check("PROTECTION_ETRANGERE_OK" in log and "[protection] eve bloqué" in log.lower(), "base lunaire protégée : Eve (étrangère) ne casse rien")
check("PROTECTION_PROPRIETAIRE_OK" in log, "base lunaire : le propriétaire Bob casse librement")
check("SANCTUAIRE_COFFRE" in log, "sanctuaire lunaire : coffre au trésor dans la chambre de la pyramide")
check("SANCTUAIRE_DALLAGE" in log and "SANCTUAIRE_ECHELLE" in log, "sanctuaire lunaire : dallage extraterrestre et échelle du puits")
check("SANCTUAIRE_GARDIENS" in log, "sanctuaire lunaire : gardiens présents")
check(any("alien_sanctuary" in r.get("discoveries", []) for r in progress.values()),
      "découverte « sanctuaire extraterrestre » à l'entrée de la salle")
ping = json.loads((work / "ping.json").read_text() or "{}")
check("TerraCraft" in ping.get("motd", "") and "0." in ping.get("motd", ""), f"MOTD de la liste des serveurs ({ping.get('motd', '')!r})")
check(ping.get("icon") is True, "icône du serveur installée et servie")
check((work / "server-icon.png").exists(), "server-icon.png écrit à la racine du serveur")
check(not any("Exception" in line and "spark" not in line for line in log.splitlines()),
      "aucune exception dans le journal (hors spark)")
sys.exit(1 if failures else 0)
EOF
