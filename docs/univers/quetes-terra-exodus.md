# Quêtes et progression — TERRA EXODUS

Les **19 identifiants du DOCX** sont conservés dans `ExodusQuests.java`.
Cette table décrit l’implémentation de la version 0.41.0 proposée dans la PR #7.
Les crédits et points sont remis une fois par joueur ; les étapes validées par
l’administration ne versent pas de récompense.

Les objectifs observés avant leur déblocage restent utilisables. Ils ne dispensent
pas des prérequis : la ville précède sa défense ; tous les objectifs d’un acte
précèdent la progression suivante. Aucune recette ni dimension existante n’est
verrouillée globalement par la campagne.

## Acte 1

| Quête | Preuves serveur | Prérequis | Récompense |
|---|---|---|---|
| `q1_1_ashes` — Premiers Pas dans les Cendres | Fouiller un coffre (1); Porter une arme (1) | Départ | 150 crédits + 25 points |
| `q1_3_town` — L’Acte Fondateur | Habiter une ville et protéger un chunk (1) | q1_1_ashes | 250 crédits + 25 points |
| `q1_2_night` — Sous les Étoiles Hostiles | Tenir un raid jusqu’à l’aube (1); Repousser huit monstres (8) | q1_3_town | 300 crédits + 25 points |
| `q1_4_trade` — Le Pouls du Commerce | Utiliser un étal (1); Terminer un contrat du jour (1) | q1_3_town | 200 crédits + 25 points |
## Acte 2

| Quête | Preuves serveur | Prérequis | Récompense |
|---|---|---|---|
| `q2_1_oil_hunt` — Chasseur d’Or Noir | Détecter un gisement sous ses pieds (1) | q1_2_night,q1_4_trade | 250 crédits + 50 points |
| `q2_2_iron_blood` — Le Sang de la Terre | Faire fonctionner une pompe attribuée (1) | q2_1_oil_hunt | 350 crédits + 50 points |
| `q2_3_refining` — Raffinage Stratégique | Produire 10 000 mB d’essence (10000); Produire 1 000 mB de kérosène (1000) | q2_2_iron_blood | 500 crédits + 50 points |
| `q2_4_logistics` — Maîtrise des Continents | Assembler un véhicule (1); Ouvrir un ravitaillement (1) | q2_3_refining | 450 crédits + 50 points |
## Acte 3

| Quête | Preuves serveur | Prérequis | Récompense |
|---|---|---|---|
| `q3_1_eva_prep` — Équipement Extravéhiculaire | Équiper casque, combinaison, bottes et oxygène (1) | q2_4_logistics | 300 crédits + 75 points |
| `q3_2_orbit` — Cap sur l’Orbite | Lancer une fusée (1); Atteindre l’orbite terrestre (1) | q3_1_eva_prep | 600 crédits + 75 points |
| `q3_3_station` — Avant-Poste Orbital | Enregistrer une station orbitale avec atelier et oxygène (1) | q3_2_orbit | 600 crédits + 75 points |
| `q3_4_moon_step` — Un Pas sur la Lune | Visiter la Lune (1); Déployer une base lunaire (1); Déployer un rover lunaire (1) | q3_3_station | 600 crédits + 75 points |
| `q3_5_ghosts_he3` — Les Fantômes de 2089 | Explorer un sanctuaire lunaire (1); Extraire seize éclats d’hélium-3 (16); Extraire huit cristaux lunaires (8); Extraire cinq minerais de titane (5); Lire l’archive lunaire (1) | q3_4_moon_step | 800 crédits + 75 points |
## Acte 4

| Quête | Preuves serveur | Prérequis | Récompense |
|---|---|---|---|
| `q4_1_shipyard` — Chantier Naval Spatial | Achever un vol en vaisseau construit (1) | q3_5_ghosts_he3 | 900 crédits + 100 points |
| `q4_2_mars_colony` — La Colonie Rouge | Visiter Mars (1); Déployer une base martienne (1); Obtenir une récolte de serre attribuée sur Mars (1) | q4_1_shipyard | 1000 crédits + 100 points |
| `q4_3_orion_glyphs` — Les Glyphes d’Orion | Lire les quatre glyphes de l’archive martienne (1) | q4_2_mars_colony | 1000 crédits + 100 points |
## Acte 5

| Quête | Preuves serveur | Prérequis | Récompense |
|---|---|---|---|
| `q5_1_zero_g` — Navigation en Zéro-G | Sortir équipé dans les astéroïdes puis revenir à une balise (1) | q4_3_orion_glyphs | 1000 crédits + 125 points |
| `q5_2_sanctuary` — L’Épreuve du Sanctuaire | Vaincre son gardien dans le temple d’Orion (1) | q5_1_zero_g | 1500 crédits + 125 points |
| `q5_3_artifact` — L’Éveil Cosmique | Contribuer un Noyau authentique à la restauration d’une ville (1) | q5_2_sanctuary | 2000 crédits + 125 points |

Total de campagne : **12 750 crédits**, sans compter les missions libres,
contrats, primes ou objectifs municipaux déjà présents.

## Attribution et arbitrages

- Arme : équipement porté ou inventaire ; le kit initial fournit une épée.
- Ville : appartenance réelle et au moins un claim personnel. Le registre reste du mobilier.
- Commerce : utiliser un étal et réclamer un contrat quotidien réellement terminé.
- Industrie : le premier clic sur une machine lui attribue une télémétrie persistante.
  La pompe doit extraire et la raffinerie doit convertir du brut. Déplacer des
  bidons ou donner des objets ne produit aucune preuve de raffinage.
- Équipement EVA : lecture des emplacements de combinaison, avec réserve d’oxygène.
- Station : balise, atelier et distributeur aux emplacements du kit orbital ;
  une station ancienne intacte peut être reconnue près de sa balise.
- Base lunaire et martienne : station effectivement déployée ou déjà enregistrée.
- Rover : déballage réussi sur la Lune, à la main ou par la charge utile d’une fusée.
- Archives lunaires : lecture près du trésor d’un sanctuaire déjà découvert.
- Vaisseau : atterrissage réussi après le vol construit, pour les membres d’équipage présents.
- Mars : récolte réelle d’une serre attribuée, puis lecture dans un site généré.
- EVA finale : sortie sans véhicule, casque/combinaison/bottes et jetpack alimenté,
  puis retour à pied près d’une balise dans la ceinture.
- Gardien : mort attribuée à un joueur, propriétaire de l’épreuve présent à proximité.
- Final : dépôt authentifié et activation municipale. L’artefact courant ne suffit pas.

Les quantités de cristaux comptent les blocs extraits, comme les statistiques
existantes. Les douze rôdeurs et le mini-boss du document initial ne sont pas un
prérequis : le combat lunaire reste libre et le gardien final est une variante
nommée du commandant existant, avec sa barre de vie et sans sa prime de bunker.
Les objets absents du mod ne deviennent pas des conditions impossibles.
Le kit d’avion constitue une alternative au véhicule terrestre.

Le carburant obtenu via l’hélium-3 reste le `rocket_fuel` courant. Une recette
stable permet de reconstruire l’artefact de navigation : deux cristaux lunaires,
un éclat d’hélium-3 et un lingot de titane. Le Noyau final reste authentifié
séparément, sur un objet nommé, sans nouveau modèle ni nouvelle recette.

Voir [le guide de jeu et d’exploitation](../terra-exodus-jeu.md) pour les commandes,
les données, les sauvegardes et les limites des tests.
