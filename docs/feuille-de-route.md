# Feuille de route TerraCraft

Objectif : construire un serveur survie multijoueur sur une Terre réelle, avec exploration, villes, claims, économie, véhicules, armes, apocalypse et progression vers l'espace.

Architecture spatiale de référence : [architecture-systeme-solaire.md](architecture-systeme-solaire.md).

Principe : chaque phase doit être jouable et vérifiable avant d'ajouter la suivante. Les systèmes serveur passent avant l'interface ; les données réelles et la performance passent avant les effets visuels.

## État actuel

### Déjà en place

- Serveur Fabric 26.3 avec déploiement Falix sur la branche `falix`.
- Monde Terre réelle : relief, climat, routes, eau, occupation du sol et bâtiments OSM/Overture.
- Carte en jeu avec sélection du point de départ, recherche et souris corrigée.
- Générateur de bâtiments avec plafonds de hauteur, styles, fenêtres, toits et intérieurs.
- Open Parties and Claims, Xaero Minimap et Xaero World Map.
- Survie, `/aide`, `/sethome`, `/home`, `/back`, `/tpa`.
- Économie de base : `/argent`, `/hdv`, vente, achat et retrait d'annonces.
- Armes à feu, munitions, dégâts et sniper avec zoom.
- Véhicules assemblables, réparables, conduisibles, attaquables et démontables.
- Véhicules générés sous forme de vraies entités, plus de voitures décoratives pour les nouvelles zones.
- Fusée, station orbitale, Lune, oxygène et progression de personnage.
- Cache Overture local et déploiement du cache de bâtiments avec le pack Falix.

### Problèmes à surveiller

- Les anciens chunks gardent leur ancienne génération : une nouvelle map ou de nouvelles zones sont nécessaires pour valider le générateur.
- L'hôtel des ventes possède maintenant une interface graphique, pagination et retrait d'annonces.
- Les claims sont fournis par un mod externe ; TerraCraft peut améliorer l'aide et le parcours, mais pas redessiner directement son écran interne.
- Les véhicules générés doivent être testés avec plusieurs joueurs, collisions, démontage et inventaire plein.

## Phase 0 — Stabilisation technique

Priorité immédiate, bloquante pour le reste.

- [ ] Tester une map neuve avec 10 à 20 points réels : centre-ville, maisons, zones industrielles, routes, campagne et montagne.
- [ ] Vérifier les temps de génération et les erreurs de téléchargement/cache.
- [ ] Tester la génération avec et sans cache Overture.
- [ ] Vérifier qu'un chunk ne contient pas de bâtiment géant, vide ou dupliqué.
- [ ] Tester véhicules, armes, souris, claims et économie sur serveur local puis Falix.
- [x] Ajouter une procédure de sauvegarde et de restauration testée (sauvegardes automatiques intégrées, voir deploy/FALIX.md).
- [ ] Mesurer TPS, mémoire, temps de démarrage et taille du monde (spark installé : `/spark tps`, `/spark health`, `/spark profiler`).

Critère de sortie : 30 minutes de jeu à plusieurs sans crash, blocage de génération ou perte d'objets.

## Phase 1 — Accueil et interface joueur

- [x] Créer un menu TerraCraft principal : Carte, Claims, Missions, Argent, Hôtel des ventes, Fiche joueur et Guide.
- [ ] Garder `/aide` comme solution de secours avec les mêmes informations.
- [x] Ajouter un tutoriel : parcours « Premiers pas » en 6 étapes (/tuto), affiché à droite de l'écran.
- [x] Afficher clairement les touches `M`, `'`, `K`, `O`, `J` (carnet de survie, /aide, tutoriel).
- [ ] Uniformiser couleurs, icônes et textes français.
- [ ] Ajouter confirmations et messages d'erreur compréhensibles.

Critère : un nouveau joueur peut choisir sa zone, la protéger et accéder à l'économie sans consulter le code.

## Phase 2 — Claims, villes et groupes

- [ ] Nom, description et couleur du claim.
- [ ] Rôles : propriétaire, membre, invité, constructeur.
- [ ] Invitation et retrait d'un joueur depuis un parcours simple.
- [ ] Affichage du propriétaire et des limites à l'entrée d'une zone.
- [ ] Protection des coffres, véhicules et ateliers.
- [ ] Puis créer les villes : plusieurs claims, centre-ville, membres et trésorerie.
- [ ] Ajouter une taxe légère seulement si elle finance un service identifiable.

Critère : deux joueurs peuvent créer une base commune et donner des accès différents sans ambiguïté.

## Phase 3 — Économie et commerce

- [x] Créer un vrai écran d'hôtel des ventes avec pages, prix, vendeur, achat et retrait d'annonce.
- [x] Catégories : ressources, blocs, pièces, armes, espace, nourriture, équipement.
- [ ] Magasins joueurs placés dans les bâtiments ou claims.
- [x] Prix moyen, volume vendu et dernière vente.
- [ ] Sources d'argent : missions, exploration, primes et vente de ressources.
- [x] Sorties d'argent : comptoir du serveur (carburant, oxygène, munitions, vivres) et frais de marché de 2 %.
- [x] Transactions atomiques : aucune perte d'objet ou de crédit en cas d'erreur (objet complet conservé, écriture atomique + .bak).
- [x] Journal administrateur : `/eco stats` (créé, détruit, en circulation) et logs [HDV], [ECO], [MISSION].

Critère : chaque joueur peut gagner, dépenser et échanger des crédits sans inflation incontrôlée.

## Phase 4 — Missions et progression

- [ ] Journal de missions actives, terminées et récompenses.
- [ ] Familles : exploration, survie, combat et économie.
- [ ] Missions liées à la position réelle : ville, bâtiment, route ou point d'intérêt.
- [ ] Récompenses : crédits, réputation, pièces, plans et accès à des zones.
- [ ] Métiers : mécanicien, éclaireur, récupérateur, combattant et pilote.
- [ ] Collections : villes visitées, bâtiments, véhicules réparés, ressources et découvertes.
- [ ] Missions coopératives avec progression de groupe.

Critère : un joueur a toujours une prochaine activité claire sans grind obligatoire.

## Phase 5 — Véhicules et transport

- [x] Propriétaire, verrouillage et partage d'accès.
- [ ] Coffre de véhicule protégé par le claim.
- [ ] État détaillé : moteur, roues, radiateur, batterie, turbo et carburant.
- [ ] Garage ou borne de réparation dans les villes.
- [x] Récupération sûre des pièces dans l'inventaire lors du démontage.
- [ ] Rôles clairs : voiture rapide, camion de transport, véhicule lourd.
- [ ] Tester collisions, pentes, frontières de chunks, passagers et reconnexion.
- [ ] Réserver les véhicules rares aux événements.

Critère : conduire, réparer, partager, ranger et démonter fonctionne sans perte d'objet.

## Phase 6 — Combat, danger et apocalypse

- [ ] Finaliser les dégâts des véhicules et les règles de protection dans les claims.
- [ ] Améliorer munitions, rechargement, recul, zoom et effets visuels.
- [ ] Améliorer les textures et icônes des armes, véhicules et pièces.
- [ ] Ajouter zones PvE, zones PvP et règles explicites.
- [ ] Ajouter bâtiments dangereux, bunkers, caves, ravitaillements et boss rares.
- [ ] Ajouter événements : convoi, contamination, attaque et ravitaillement militaire.
- [ ] Ajouter les primes après équilibrage du PvP.
- [ ] Tester claims contre tirs, explosions, véhicules et mobs.

Critère : le danger pousse à explorer sans détruire arbitrairement la progression.

## Phase 7 — Monde vivant et génération avancée

- [ ] Stabiliser la classification maison, immeuble, commerce, industriel et tour.
- [ ] Utiliser hauteur et niveaux Overture uniquement quand les données sont fiables.
- [ ] Refuser les géométries aberrantes et les bâtiments trop grands.
- [ ] Ajouter des variantes d'intérieurs selon le type de bâtiment.
- [ ] Ajouter mobilier, éclairage, entrées et loot contextualisé.
- [ ] Ajouter points d'intérêt : hôpitaux, gares, stations-service, écoles et bunkers.
- [ ] Varier la densité entre centre-ville, banlieue, campagne et zones industrielles.
- [ ] Ajouter un rapport de génération pour localiser les bâtiments suspects.

Critère : une ville est reconnaissable, les maisons restent à taille humaine et chaque zone a un intérêt différent.

## Phase 8 — Espace et endgame

- [ ] Plans et composants de fusée débloqués par missions.
- [ ] Ressources rares terrestres nécessaires au départ.
- [ ] Station orbitale avec stockage, atelier et missions.
- [ ] Zones lunaires, minerais, épaves et dangers spécifiques.
- [ ] Retour Terre et transport de ressources limité.
- [ ] Objectifs coopératifs de construction de station.
- [ ] Garder la Terre utile après l'accès à la Lune.

Critère : atteindre la Lune est une étape prestigieuse, mais la Terre reste active.

## Phase 9 — Bêta publique et exploitation

- [ ] Bêta privée avec whitelist.
- [ ] Publication des commandes, touches, règles, versions et connexion.
- [x] Sauvegardes automatiques et restauration testée.
- [ ] Logs des transactions, claims, véhicules et actions administratives.
- [ ] Procédure de signalement des bugs et pertes d'objets.
- [ ] Pack client synchronisé avec Falix et versions publiées.
- [ ] Événement d'ouverture avec objectifs simples.
- [ ] Suivi : joueurs actifs, retour après 24 h, missions, annonces, TPS et erreurs.

## Ordre de développement recommandé

1. Stabilisation et nouvelle map de test.
2. Menu principal et onboarding.
3. Claims améliorés et villes.
4. Hôtel des ventes graphique et magasins joueurs.
5. Missions, métiers et collections.
6. Véhicules propriétaires et garages.
7. Événements, danger et combat.
8. Génération enrichie et points d'intérêt.
9. Endgame spatial.
10. Bêta publique.

## Validation avant chaque déploiement

- Le serveur compile.
- Le client reçoit le même jar.
- Le pack Falix contient la même version.
- Une sauvegarde est disponible.
- Une commande de test est documentée.
- Aucun système ne supprime silencieusement un item, un crédit, un claim ou un véhicule.
