# Architecture du système solaire TerraCraft

Objectif : proposer une Terre crédible, des destinations spatiales reconnaissables et une progression jouable. Les distances et les durées sont compressées pour le gameplay ; les environnements, les ressources, les contraintes et les silhouettes restent cohérents.

## Règles de conception

- Une destination n'est ajoutée que si elle possède un intérêt de jeu clair : ressource, mission, danger, construction ou découverte.
- Chaque planète possède deux espaces liés : sa surface et son orbite.
- La Terre reste le centre économique et social. Aller dans l'espace ne doit pas rendre les villes inutiles.
- Aucun voyage ne doit immobiliser un joueur pendant des heures : le temps réel est réduit, avec un coût et une préparation visibles.
- Les ressources rares sont réparties entre plusieurs mondes pour créer du commerce, sans rendre un seul monde obligatoire pour chaque action.
- Les dimensions sont générées de façon déterministe et bornées : pas de génération infinie inutile, pas de bâtiments géants ou de structures vides.

## Structure technique cible

Chaque monde planétaire est défini par une fiche commune :

| Élément | Rôle |
|---|---|
| Identifiant | `earth`, `moon`, `mars`, etc. |
| Orbite | Quai, station, carburant et retour |
| Surface | Générateur, biome, gravité, température |
| Atmosphère | Respirable, combinaison, oxygène consommé |
| Ressources | Minerais et matériaux propres à la destination |
| Dangers | Mobs, météo, radiation, chutes ou manque d'air |
| Déverrouillage | Mission, plan, carburant et équipement requis |
| Point d'arrivée | Spawn sécurisé et retour clairement indiqué |

Le code doit utiliser cette fiche pour les noms, les titres d'arrivée, la gravité, l'oxygène et les destinations. On évite un `if` différent pour chaque planète dans les systèmes de voyage.

## Ordre réaliste mais jouable

### 1. Terre — hub

- Villes réelles, ruines, routes, véhicules, claims et économie.
- Carburant, pièces et composants de fusée récupérés ou fabriqués ici.
- Missions de préparation : combinaison, oxygène, moteur et lancement.

### 2. Orbite terrestre — station de base

- Quai généré automatiquement à l'arrivée.
- Construction avec coque, plancher, hublots et lampes.
- Distributeur d'oxygène, stockage partagé et atelier spatial.
- Départ vers la Lune et retour vers la Terre.

### 3. Lune — première surface extraterrestre

- Gravité faible, absence d'atmosphère, cratères et régolithe.
- Titane et hélium-3 comme ressources principales.
- Épaves, rôdeurs lunaires et petits sites d'extraction.
- Retour possible depuis une plateforme ou une fusée préparée.

### 4. Mars — prochaine planète jouable

- Orbite de Mars avec station minimale.
- Surface rouge, canyons simples, glace et tempêtes visuelles.
- Atmosphère non respirable, oxygène obligatoire.
- Fer, glace, silicates et ressource rare pour les moteurs avancés.

### 5. Astéroïdes — zone de transport

- Petites zones instanciées ou champs limités, pas un univers infini.
- Minage de métaux rares et récupération d'épaves.
- Déplacements avec jetpack et ancrages, danger de dérive.
- Sert de lien économique entre Terre, Lune et Mars.

### 6. Planètes extérieures — endgame optionnel

- Jupiter et Saturne : d'abord des orbites et lunes, pas une surface habitable.
- Vénus : surface dangereuse, température et pression adaptées au gameplay.
- Mercure : cycle thermique et métaux rares, zone courte mais exigeante.
- Uranus, Neptune et Pluton : destinations tardives d'exploration, pas prioritaires pour la bêta.

## Véhicules et progression

1. Moto : déplacement léger, deux roues, faible coffre.
2. Voiture : déplacement polyvalent et exploration.
3. Camion : transport et grand coffre.
4. Jetpack : déplacements courts en orbite, consommation d'énergie.
5. Avion : transport terrestre rapide, pistes et carburant requis.
6. Fusée : Terre ↔ orbite ↔ destinations planétaires, composants et carburant.

Les véhicules terrestres ne deviennent pas des véhicules spatiaux par magie : chaque catégorie a son carburant, son inventaire et ses limites. Le jetpack ne remplace pas la fusée ; l'avion ne fonctionne pas dans le vide.

## Garde-fous de gameplay

- Spawn sécurisé avec retour possible avant toute expédition.
- Alerte claire avant départ : destination, carburant, oxygène, risque et point de retour.
- Coffres et inventaires sauvegardés avant transfert de dimension.
- Aucun objet important détruit silencieusement sur un échec de voyage.
- Station et bases protégées par claims.
- Téléportation de secours réservée aux admins et journalisée.
- Une mission courte valide chaque nouvelle planète avant l'ajout de son contenu avancé.

## Feuille d'exécution

1. Remplacer les destinations numériques par un registre planétaire commun.
2. Générer orbite et surface depuis la même définition.
3. Migrer Terre, orbite et Lune sans changer les sauvegardes existantes.
4. Ajouter Mars avec une boucle complète : départ, arrivée, oxygène, ressource, mission et retour.
5. Ajouter jetpack et avion avec tests de collision, carburant et reconnexion.
6. Ajouter les astéroïdes et les ressources rares.
7. Ajouter les planètes extérieures uniquement après validation des performances.

## Validation obligatoire

- Départ et retour testés avec un inventaire plein.
- Déconnexion pendant le voyage sans perte d'objet.
- Joueur sans combinaison refusé proprement.
- Station retrouvable après redémarrage.
- Génération stable sur dix points de départ.
- Temps de génération et mémoire mesurés avant chaque nouvelle dimension.

## État (0.11.0)

- Destinations : Terre, orbite terrestre, orbite lunaire, Lune, orbite de Mars, Mars (registre `SolarSystem`).
- Carburant de fusée par trajet (`Space.travelCost`, plus court chemin) : Terre ↔ orbite 3, orbite ↔ orbite lunaire 2,
  orbite lunaire ↔ Lune 1, orbites ↔ orbite de Mars 4 à 5, orbite de Mars ↔ Mars 2. Réservoir de 8 doses :
  Terre → Mars impose une escale en orbite.
- Mars et son orbite se débloquent après avoir marché sur la Lune.
- Stations : kit de module pressurisé (7 × 5 × 7, oxygène, portes raccordables) et balise de station ; une fusée se
  pose à la balise de son pilote ou d'un habitant de sa ville, sinon sur un quai construit à l'arrivée.
- Vérifié automatiquement (`tools/scenario_test.sh`) : module, balise, vol Terre → orbite lunaire, arrivée à la
  balise, 5 doses consommées.

