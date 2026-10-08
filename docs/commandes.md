# Commandes et touches

Référence de TerraCraft (version 0.25). Tout est en français ; `/aide` en donne l'essentiel en jeu.

## Touches

| Touche | Rôle |
|---|---|
| `O` | Menu TerraCraft : carte, hôtel des ventes, argent, fiche, missions, combinaison, coffre du véhicule, ville, guide |
| `J` | Combinaison spatiale : casque, combinaison, bottes, jetpack, deux réserves d'oxygène |
| `E` | Inventaire, avec le panneau de la combinaison spatiale à droite (survie et créatif) |
| `V` | Coffre du véhicule où l'on est assis (ou du sien à moins de 6 blocs) ; soute de la fusée |
| `K` | Fiche de personnage : paliers, compétences, découvertes, compteurs |
| `M` / `'` | Carte Xaero ; menu des claims (Open Parties and Claims) |
| Saut maintenu en l'air | Jetpack (s'il est porté et a du carburant) |
| Avion | `Z`/`S` gaz, `Q`/`D` tourner, `Espace` monter, `Ctrl` descendre, `Maj` sortir |
| Rover lunaire | Clic droit avec la caisse : déballer ; conduite comme une voiture ; `Maj` + clic gauche : remballer |
| Fusée | `Espace` décoller ; à pied, `Maj` + clic droit main vide : **carte des étoiles** (aussi menu `O`) ; clic droit avec un réservoir : réservoir en plus, avec un kit : charge utile |

## Joueurs

| Commande | Rôle |
|---|---|
| `/aide` (ou `/guide`) | Résumé des commandes et des touches |
| `/tuto` | Objectif « Premiers pas » en cours ; `/tuto passer` pour masquer le parcours |
| `/sethome`, `/home` | Maison (refusé en combat) |
| `/back` | Retour au lieu de la dernière mort (refusé en combat) |
| `/tpa <joueur>`, `/tpaccept`, `/tpdeny` | Téléportation entre joueurs (une demande toutes les 10 s) |
| `/argent` | Solde (1 000 crédits au départ) |
| `/hdv` | Hôtel des ventes (aussi menu `O`) : `vendre <prix>` (objet en main, frais 2 %), `acheter <n°>`, `retirer <n°>`, `page <n>` |
| `/etal`, `/etal prix <n>`, `/etal ajouter`, `/etal acheter [n]` | Étal le plus proche (4 blocs) : offre ; fixer le prix et garnir (propriétaire) ; acheter. Clic droit : stock (propriétaire) ou offre ; Maj + clic droit : acheter 1 |
| `/comptoir [n]` | Comptoir du serveur : carburant, oxygène, munitions, vivres à prix fixe |
| `/missions` | Contrats du jour et missions ; `reclamer <id>` |
| `/metier` | Métiers (mécanicien, éclaireur, récupérateur, combattant, pilote) ; `choisir <métier>` (un changement par 24 h) |
| `/ville` | Sa ville : `creer <nom>` (500 crédits), `inviter`, `rejoindre`, `quitter`, `exclure`, `maire`, `adjoint`, `centre`, `tp`, `deposer`, `payer`, `liste` (adjoints : inviter, exclure, payer) |
| `/station` | Ses stations spatiales ; `nom <nom>` pour renommer la plus proche |
| `/plans` | Plans de fusée débloqués, matériaux, comment débloquer les autres |
| `/fusee carte` | Carte des étoiles de la fusée où l'on est assis (ou à moins de 8 blocs) |
| `/fusee cap <destination>` | Mettre le cap : `terre`, `orbite`, `orbite_lunaire`, `lune`, `orbite_mars`, `mars` |
| `/atelier installer <plan>` | Installer une amélioration sur la fusée garée près d'un atelier de station |
| `/terracraft ou` | Latitude, longitude et altitude réelles de sa position |
| `/terracraft vehicule partager|retirer <joueur>`, `liberer` | Partage et propriété de son véhicule |
| `/pvp` | Règle de combat : PvE partout, liste des zones PvP |
| `/prime <joueur> <montant>`, `/primes` | Mettre une tête à prix (minimum 50, argent bloqué, gagné par qui l'abat en zone PvP) ; primes en cours |
| `/confirmer`, `/annuler` | Valider ou abandonner une action coûteuse en attente (boutons cliquables dans le chat, 30 s) |
| `/signaler <message>` | Signaler un bug ou une perte d'objet aux administrateurs (un par minute) |

## Administrateurs (opérateurs)

| Commande | Rôle |
|---|---|
| `/eco donner <joueur> <montant>` | Créditer un joueur (journalisé `[ECO]`) |
| `/eco stats` | Crédits créés, détruits, en circulation et dans les trésoreries des villes |
| `/terracraft sauvegarde [liste]` | Sauvegarde immédiate du monde ; liste des archives (`backups/`) |
| `/terracraft largage` | Ravitaillement militaire près de soi |
| `/pvp creer <rayon> <nom>`, `/pvp supprimer <nom>` | Déclarer ou retirer une zone PvP centrée sur soi |
| `/terracraft boss` | Invoquer un Commandant du bunker devant soi |
| `/terracraft vague` | Vague nocturne immédiate sur sa ville (s'arrête au bout de 10 min) |
| `/terracraft convoi` | Convoi militaire en panne près de soi |
| `/terracraft contamination [aller]` | Zone contaminée la plus proche (et s'y rendre) |
| `/terracraft fusee decoller` | Lancer la fusée où l'on est assis (mêmes vérifications que la touche Espace) |
| `/terracraft depart` | Rouvrir la carte de départ pour soi |
| `/terracraft meteores` | Pluie de micrométéorites sur la Lune dans 5 s (sinon toutes les 25 à 45 min) |
| `/terracraft sanctuaire` | Sur la Lune : se rendre au pied du puits du sanctuaire le plus proche |
| `/terracraft vehicule voiture|camion|moto` | Véhicule complet et plein devant soi |
| spark : `/spark tps`, `/spark health`, `/spark profiler` | Performances du serveur |

## Journal de la console

Préfixes à surveiller : `[HDV]`, `[ECO]`, `[MISSION]`, `[CONTRAT]`, `[VILLE]`, `[METIER]`, `[VEHICULE]`, `[LARGAGE]`, `[CONVOI]`, `[CONTAMINATION]`, `[VAGUE]`, `[PVP]`, `[BOSS]`, `[PRIME]`, `[ETAL]`,
`[STATION]`, `[ATELIER]`, `[NAV]`, `[METEORES]`, `[PLAN]`, `[SAUVEGARDE]`, `[ANTITRICHE]`, `[SIGNALEMENT]`.

Fichiers de données du monde (`<monde>/terracraft_geo/`, écriture atomique avec copie `.bak`) : `balances.json`,
`hotel-des-ventes.json`, `economie.json`, `missions.json`, `contrats.json`, `progression.json`, `homes.json`,
`start_points.json`, `tutorial.json`, `villes.json`, `stations.json`, `signalements.json`, `contamination.json`, `pvp-zones.json`, `primes.json`.
