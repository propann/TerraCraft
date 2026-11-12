#!/usr/bin/env python3
"""Vérifie les preuves persistantes et les assertions du serveur de campagne Fabric."""
import json
import re
import sys
from pathlib import Path

work = Path(sys.argv[1])
data = work / 'world/terracraft_geo'
alice = (work / 'alice-id').read_text().strip()
load = lambda path: json.loads(path.read_text())
first = load(work / 'first-wallet.json')
wallet = load(data / 'portefeuilles.json')
rejected = load(work / 'rejected-core.json')
deposited = load(work / 'deposited-core.json')
completed = load(work / 'completed-campaign.json')
final = load(data / 'terra-exodus.json')
progress = load(data / 'progression.json')[alice]
log = (work / 'campaign.log').read_text(errors='replace')
restart = (work / 'campaign-restart.log').read_text(errors='replace')
checks = []

def check(condition, message):
    checks.append(bool(condition))
    print(('ok : ' if condition else 'ÉCHEC : ') + message)

check('book_count has 1 [exodus_test]' in log, 'un seul livre après deux demandes')
check('book_count has 1 [exodus_test]' in restart, 'mise à jour du livre après redémarrage sans duplication')
books = [line for line in log.splitlines() if 'Alice has the following entity data' in line and 'written_book' in line]
check(bool(books) and all('TERRA EXODUS' in line and 'exodus_book' in line for line in books), 'livre écrit et identifié au joueur')
check(bool(books) and all('Sujet-09' not in line and 'Archiviste' not in line for line in books), 'livre initial sans révélation des actes ultérieurs')
memories = load(data / 'pnj-memoires.json')
check(any(m['quest'] == 'exodus:q1_1_ashes' for m in memories.values()), 'Hélène propose automatiquement la première quête autorisée sans IA')
settings = load(work / 'config/terracraft-npcs.json')
check(not settings['ai_enabled'], 'aucun appel IA nécessaire à la campagne')
rejected_j = rejected['players'][alice]
check(rejected_j['contribution'] == '' and not rejected['regions'], 'artefact ordinaire refusé comme Noyau')
check('q5_3_artifact' not in rejected_j['done'], 'quête finale refusée avant contribution et restauration')
check(rejected_j['facts'].get('gasoline', 0) > 0 and rejected_j['facts'].get('kerosene', 0) > 0, 'production réelle de raffinerie attribuée au joueur')
check(all(j['facts'].get('gasoline', 0) == 0 for key, j in rejected['players'].items() if key != alice), 'second utilisateur sans détournement de production')
check(rejected_j['facts'].get('guardian') == 1, 'gardien du temple généré vaincu avec une source joueur')
check(len(deposited['regions']) == 1, 'une seule région après deux tentatives de dépôt')
region = next(iter(deposited['regions'].values()), {})
check(region.get('contributors') == [alice] and not region.get('active'), 'dépôt unique et activation refusée au simple habitant')
check(deposited['players'][alice]['core'] == completed['players'][alice]['core'], 'identité du Noyau conservée au redémarrage')
check('poison_before has 1 [exodus_test]' in log, 'contamination naturelle active avant restauration')
check('poison_after has 0 [exodus_test]' in restart, 'effets de contamination suspendus dans la région restaurée')
check(next(iter(completed['regions'].values()), {}).get('active'), 'activation persistante par le maire')
check(len(completed['players'][alice]['done']) == 19, '19 quêtes achevées avec étapes intermédiaires administratives explicites')
check(first['balances'].get(alice) == 2150, f'premiers paiements uniques moins fondation : {first["balances"].get(alice)} / 2150')
check(wallet['balances'].get(alice) == 4150, f'paiement final unique et reset sans second paiement : {wallet["balances"].get(alice)} / 4150')
check(len(wallet['receipts']) == 3, 'trois reçus économiques pour les trois quêtes effectivement payées')
check(len(progress.get('campaignReceipts', [])) == 3, 'trois reçus de points malgré répétitions et remise à zéro')
check(final['players'][alice]['contribution'] == completed['players'][alice]['contribution'], 'remise à zéro sans réutilisation du Noyau déposé')
check(len(final['regions']) == 1 and next(iter(final['regions'].values()))['active'], 'région restaurée conservée après répétition et reset')
# Le classificateur commun n'autorise que les pannes externes déjà documentées.
from check_scenario_log import classify_exceptions
for name in ('campaign.log', 'campaign-restart.log'):
    errors, _ = classify_exceptions((work / name).read_text(errors='replace'))
    check(not errors, f'{name} sans erreur serveur inattendue : {errors}')
print(f'{sum(checks)}/{len(checks)} assertions de campagne réussies')
if not all(checks):
    raise SystemExit(1)
