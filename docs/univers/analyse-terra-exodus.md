# Analyse technique du scénario

Analyse du 12 novembre 2026 sur `main`, commit `6736c35`, mod `0.39.0`.
La [PR #4](https://github.com/propann/TerraCraft/pull/4) ajoute la fiabilisation de la CI ;
la [PR #5](https://github.com/propann/TerraCraft/pull/5) prépare les PNJ en `0.40.0`.
Cette analyse de référence décrit le socle initial. La PR #7 intègre maintenant la campagne en 0.41.0 ; son état courant, ses arbitrages et ses tests sont décrits dans le [guide de jeu](../terra-exodus-jeu.md). Les mentions de travail restant ci-dessous se rapportent au socle 0.39.0. Le code a été examiné depuis le dépôt,
puis le texte et les six tableaux du [DOCX fourni](sources-terra-exodus.md) ont été
rapprochés de ces sources. Le document Word définit une direction de conception ;
ses exemples techniques ne sont pas tous des mécanismes déjà présents.

## Systèmes présents

« Présent » indique du code ou une ressource identifiée. Les noms de classes et
de fichiers ne constituent pas à eux seuls une preuve de jouabilité ni d'équilibrage.

| Domaine | Sources | Conclusion pour la campagne |
|---|---|---|
| Terre réelle | [EarthTerrain](../../geo-mod/src/main/java/com/terracraft/geo/world/EarthTerrain.java), [GeoChunkGenerator](../../geo-mod/src/main/java/com/terracraft/geo/world/GeoChunkGenerator.java), [OsmCells](../../geo-mod/src/main/java/com/terracraft/geo/world/OsmCells.java), [VectorTiles](../../geo-mod/src/main/java/com/terracraft/geo/world/VectorTiles.java), [ElevationTiles](../../geo-mod/src/main/java/com/terracraft/geo/world/ElevationTiles.java), [KoppenMap](../../geo-mod/src/main/java/com/terracraft/geo/world/KoppenMap.java), [BuildingStyles](../../geo-mod/src/main/java/com/terracraft/geo/world/BuildingStyles.java), [BuildingInterior](../../geo-mod/src/main/java/com/terracraft/geo/world/BuildingInterior.java), [UrbanTrees](../../geo-mod/src/main/java/com/terracraft/geo/world/UrbanTrees.java) | Socle des lieux réels et des explorations terrestres. |
| Après l'Effondrement | [Apocalypse](../../geo-mod/src/main/java/com/terracraft/geo/world/Apocalypse.java), [Wasteland](../../geo-mod/src/main/java/com/terracraft/geo/world/Wasteland.java), [Contamination](../../geo-mod/src/main/java/com/terracraft/geo/Contamination.java), [NightRaids](../../geo-mod/src/main/java/com/terracraft/geo/NightRaids.java), [SupplyDrops](../../geo-mod/src/main/java/com/terracraft/geo/SupplyDrops.java), [Convoys](../../geo-mod/src/main/java/com/terracraft/geo/Convoys.java) | Dangers, récupération et défense ; les escortes scénarisées de convois restent à créer. |
| Villes et échanges | [Towns](../../geo-mod/src/main/java/com/terracraft/geo/Towns.java), [TownPolitics](../../geo-mod/src/main/java/com/terracraft/geo/TownPolitics.java), [Claims](../../geo-mod/src/main/java/com/terracraft/geo/Claims.java), [Economy](../../geo-mod/src/main/java/com/terracraft/geo/Economy.java), [AuctionHouse](../../geo-mod/src/main/java/com/terracraft/geo/AuctionHouse.java), [Contracts](../../geo-mod/src/main/java/com/terracraft/geo/Contracts.java), [Bounties](../../geo-mod/src/main/java/com/terracraft/geo/Bounties.java), [Jobs](../../geo-mod/src/main/java/com/terracraft/geo/Jobs.java) | Ville, trésorerie, métiers, marché, élections et défiance. Les lois municipales votées sont une extension. |
| Industrie | [IndustryBlocks](../../geo-mod/src/main/java/com/terracraft/geo/content/industry/IndustryBlocks.java), [MachineKind](../../geo-mod/src/main/java/com/terracraft/geo/content/industry/MachineKind.java), [MachineBlockEntity](../../geo-mod/src/main/java/com/terracraft/geo/content/industry/MachineBlockEntity.java), [Industry](../../geo-mod/src/main/java/com/terracraft/geo/Industry.java), [Logistics](../../geo-mod/src/main/java/com/terracraft/geo/Logistics.java) | Pompes, raffinerie, générateur, batteries, serre, réseaux et stock de ville. Les machines partagent des classes : `OilPump`, `Refinery` ou `BatteryBank` ne sont pas des classes séparées à chercher. |
| Transport et vaisseaux | [Vehicle](../../geo-mod/src/main/java/com/terracraft/geo/content/Vehicle.java), [Plane](../../geo-mod/src/main/java/com/terracraft/geo/content/Plane.java), [Rocket](../../geo-mod/src/main/java/com/terracraft/geo/content/Rocket.java), [ShipBlocks](../../geo-mod/src/main/java/com/terracraft/geo/content/ShipBlocks.java), [BuiltShip](../../geo-mod/src/main/java/com/terracraft/geo/content/ship/BuiltShip.java), [ShipCarrier](../../geo-mod/src/main/java/com/terracraft/geo/content/ship/ShipCarrier.java) | Transport terrestre, avion, fusée et déplacement de constructions en blocs ; les noms de blocs ne correspondent pas tous à des classes dédiées. |
| Espace | [Space](../../geo-mod/src/main/java/com/terracraft/geo/Space.java), [SpaceTravel](../../geo-mod/src/main/java/com/terracraft/geo/SpaceTravel.java), [Plans](../../geo-mod/src/main/java/com/terracraft/geo/Plans.java), [Stations](../../geo-mod/src/main/java/com/terracraft/geo/Stations.java), [StationBuilder](../../geo-mod/src/main/java/com/terracraft/geo/StationBuilder.java), [SpaceSuit](../../geo-mod/src/main/java/com/terracraft/geo/SpaceSuit.java) | Destinations Terre/orbites/Lune/Mars/astéroïdes et équipements. L'expression « système solaire complet » dépasserait les destinations actuelles. |
| Présence alien | [ModMobs](../../geo-mod/src/main/java/com/terracraft/geo/content/ModMobs.java), [MoonUnderground](../../geo-mod/src/main/java/com/terracraft/geo/world/MoonUnderground.java), [MoonChunkGenerator](../../geo-mod/src/main/java/com/terracraft/geo/world/MoonChunkGenerator.java), [Bosses](../../geo-mod/src/main/java/com/terracraft/geo/Bosses.java) | Ruines et sanctuaires lunaires, rôdeurs et astronautes perdus. Le boss Commandant des bunkers est distinct du futur gardien final. |

## Corrections nécessaires au scénario initial

| Formulation initiale | Constat dans le code | Adaptation retenue |
|---|---|---|
| Météorites régulières sur la Terre | [Meteors](../../geo-mod/src/main/java/com/terracraft/geo/Meteors.java) cible la Lune. | Réserver ce danger à l'acte lunaire ; un événement terrestre demanderait du développement. |
| Fabriquer le registre pour fonder et revendiquer une ville | [TownRegistryBlock](../../geo-mod/src/main/java/com/terracraft/geo/content/TownRegistryBlock.java) consulte la ville et le scrutin. La fondation passe par `/ville creer`, sa confirmation et son coût de 500 crédits ; les claims sont un système distinct. | Décrire ces démarches séparément et garder le registre comme mobilier civique. |
| Titane terrestre avant le premier vol | Le titane est généré sur la [Lune](../../geo-mod/src/main/java/com/terracraft/geo/world/MoonChunkGenerator.java) et sur [Mars](../../geo-mod/src/main/java/com/terracraft/geo/world/MarsChunkGenerator.java) ; le générateur terrestre examiné ne l'ajoute pas. | Placer son extraction dans l'acte III, après le premier accès lunaire. |
| Hélium-3 donnant un carburant intrinsèquement supérieur | La [recette](../../geo-mod/src/main/resources/data/terracraft_geo/recipe/rocket_fuel_from_helium3.json) consomme quatre `helium3_shard` pour un `rocket_fuel` standard. | En faire une source spatiale de carburant ; une nouvelle qualité de carburant reste à concevoir. |
| Décollage direct Terre → Lune | [SpaceTravel.routeProblem](../../geo-mod/src/main/java/com/terracraft/geo/SpaceTravel.java) impose d'abord l'orbite terrestre et un départ près d'une station pour les voyages lointains en survie. | Conserver le relais orbital dans le parcours obligatoire visé. |
| Artefact seulement au terme de la ceinture | [Plans.MARS_NAV](../../geo-mod/src/main/java/com/terracraft/geo/Plans.java) demande la visite de la Lune, cinq minerais de titane minés, puis deux éclats d'hélium-3, un bloc de redstone et un `alien_artifact` pour l'amélioration. | Garder les artefacts lunaires accessibles avant Mars. Le Noyau d'Orion final sera un objectif distinct. |
| Ruines martiennes déjà disponibles | [MarsChunkGenerator](../../geo-mod/src/main/java/com/terracraft/geo/world/MarsChunkGenerator.java) génère son terrain et des minerais ; `applyBiomeDecoration` est vide. | Inscrire les ruines et archives martiennes au travail restant. |
| Sanctuaire final déjà présent dans les astéroïdes | Le sanctuaire et ses gardiens actuels sont construits par [MoonUnderground](../../geo-mod/src/main/java/com/terracraft/geo/world/MoonUnderground.java). | Ajouter plus tard un relais orbital final sans déplacer le sanctuaire lunaire. |
| Un `DockingClamp` identifié | [ShipBlocks](../../geo-mod/src/main/java/com/terracraft/geo/content/ShipBlocks.java) enregistre notamment `ship_airlock`, `pilot_seat`, `nav_console` ; aucun bloc `docking_clamp` n'est identifié dans cet ensemble. | Décrire l'amarrage existant par le sas ; ne pas demander une pièce absente. |
| Fusées jetables remplacées par les vaisseaux | Les véhicules et atterrissages existants ne justifient pas cette règle de consommation. | Conserver les fusées réutilisables et présenter les vaisseaux comme une progression de construction. |
| Activation purifiante et technologies ultimes | Le butin [alien_sanctuary](../../geo-mod/src/main/resources/data/terracraft_geo/loot_table/chests/alien_sanctuary.json) distribue des artefacts ; leur présence n'implémente pas un noyau de restauration. | Spécifier séparément activation, région, autorisation, persistance et récompenses. |
| Panthéon des factions déjà géré par la course | [SpaceRace](../../geo-mod/src/main/java/com/terracraft/geo/SpaceRace.java) mémorise les premiers joueurs à certains jalons. | Prévoir un registre de campagne dédié aux communautés sans modifier les records historiques. |

Les fichiers `alien_ruins` et `alien_sanctuary` sont notamment des tables de butin.
Leur nom seul ne prouve ni l'emplacement d'une structure, ni une action sur un artefact.

## Identifiants et récompenses du DOCX

Le namespace enregistré par le mod est **`terracraft_geo`**, défini dans
[GeoMod](../../geo-mod/src/main/java/com/terracraft/geo/GeoMod.java).
Remplacer seulement le préfixe `terracraft:` ne suffit pas à créer les objets manquants.

| Nom du DOCX | Lecture correcte |
|---|---|
| `credits` | Solde géré par l'économie, pas un item à donner avec `/give`. |
| `fluid_tank` | Le réservoir existant est `terracraft_geo:fuel_tank`. |
| `rocket_fuel_bucket` | Le carburant existant est `terracraft_geo:rocket_fuel`. |
| `helium3_crystals` | Bloc existant ; les recettes d'hélium-3 consomment `helium3_shard`. |
| `moon_crawler`, `lost_astronaut` | Types d'entités existants, distincts des items. L'astronaute perdu ordinaire n'est pas un boss spécifique. |
| `alien_ruins`, `alien_sanctuary` | Notamment des noms de tables de butin ; aucun bloc de ce nom à utiliser comme preuve de structure. |
| `contaminated_zombie`, `sanctuary_guardian` | Ennemis de conception à développer avant de les exiger dans une quête. |

Les ressources d'items existantes et les déclarations examinées ne fournissent pas
les récompenses ou équipements suivants du DOCX :

`advanced_engine_block`, `ancient_key_fragment`, `bandage`, `bio_dome_emitter`,
`canned_food`, `empty_barrel`, `eva_patch_kit`, `guardian_core`, `heavy_casing`,
`insulated_wire`, `jetpack_overcharge_module`, `lunar_flag`, `mars_nav_coordinates`,
`purifier_vent`, `quantum_core_fragment`, `reinforced_door`, `sanctuary_coordinates`,
`solar_array_v2`, `town_banner`, `trade_license`.

Ils demandent une création explicite ou un remplacement validé par un objet existant.
L'absence de `purifier_vent` rend notamment le tutoriel proposé dans le DOCX impossible
à suivre tel quel. Les quantités de crédits, les titres, l'XP, les bonus de claims
et la réputation sont aussi à équilibrer et à implémenter séparément.

## Hooks, sauvegardes et commandes proposés dans le DOCX

| Proposition | Base à utiliser pour l'intégration |
|---|---|
| `ServerPlayFormattingEvents` pour la collecte | Cette proposition ne correspond pas à un contrôle d'inventaire utilisé par le mod examiné. Distinguer possession, collecte et production ; choisir une observation serveur adaptée à l'objectif. |
| `UseBlockCallback` pour prouver une pose | L'interaction peut être refusée ou ne poser aucun bloc. Vérifier l'état réellement obtenu après l'opération, à l'image des contrôles différés de butin de `GeoMod`. |
| `TileEntity`, `isOf`, `getRegistryKey` | Adapter aux noms utilisés dans ce projet : `BlockEntity`, vérifications `is(...)` et identifiants de dimension du code courant. |
| Élimination d'une entité | Le dépôt utilise `ServerLivingEntityEvents.AFTER_DEATH` dans `GeoMod`, puis attribue la victime au joueur et au bon objectif. |
| Arrivée dans une dimension | Réutiliser les découvertes produites par `Space` pour les arrivées réellement réalisées, avec contrôle de la dimension. |
| `NpcEntity` / `MistralAPI` | La PR #5 emploie `Npcs`, `NpcBrain`, `NpcPolicy` et des mobs vanilla ; les classes du DOCX sont des propositions. |

Les quêtes actuelles sont réparties notamment entre `progression.json`,
`missions.json` et `contrats.json`. L'économie a ses propres fichiers et la PR #5
ajoute `pnj-memoires.json`. Le `player_data.json` agrégé du DOCX n'est pas un schéma
déjà pris en charge. Ajouter un état de campagne versionné sans remplacer ces données.
Les dates de fiction de 2089 restent distinctes des horodatages techniques et du
calendrier réel des contrats.

Les commandes `/terracraft mission grant`, `/terracraft mission reset`,
`/terracraft npc spawn` et `/terracraft spacerace status` du DOCX ne sont pas les
commandes disponibles dans la version auditée. Les racines existantes comprennent
`/missions`, `/primes`, `/prime` et `/course` ; la PR #5 propose `/pnj creer` et
`/pnj recharger`. Les commandes administratives de campagne devront être conçues
avec permissions, journalisation et prévention des paiements répétés.

## Ordre de développement proposé

| Lot | Travail concret | Critère de validation |
|---|---|---|
| A — Écriture et accueil | Adapter `Welcome`, `ServerGuide`, `Tutorial` et les descriptions en conservant l'aide utile. | Un nouveau joueur comprend son premier objectif ; aucun texte ne présente une extension comme disponible. |
| B — Campagne persistante | Ajouter chapitres, prérequis et événements manquants dans la progression, puis l'affichage dans les missions. | Parcours réalisable en solo ; pas de saut de chapitre, double paiement ou perte d'avancement après reconnexion/redémarrage. |
| C — PNJ | Raccorder les personnages aux objectifs débloqués et limiter leur connaissance au chapitre du joueur. | Même progression avec IA activée ou repli local ; une réponse du modèle ne débloque rien seule. La PR #5 ne propose actuellement que des contrats du jour. |
| D — Archives et Mars | Ajouter journaux de 2089, découvertes et ruines martiennes, avec leurs ressources et leurs lieux. | Sources des indices trouvables, géographie vérifiée et génération mesurée sur monde neuf. |
| E — Final Orion | Sanctuaire orbital, gardien, noyau distinct, activation régionale et registre des contributions. | Pas d'accès avant les prérequis ; activation autorisée, bornée et idempotente ; sauvegarde/restauration et autres territoires préservés. |

L'interface en cinq chapitres n'existe pas encore dans
[Missions](../../geo-mod/src/main/java/com/terracraft/geo/Missions.java), qui expose
une liste de missions permanentes et les contrats quotidiens. L'écriture des
chapitres doit accompagner un véritable état de campagne.

## Portée de cette livraison

Cette livraison ajoute la documentation, ses liens et un exemple de six profils PNJ.
Elle ne change
ni les quêtes en jeu, ni les recettes, ni la génération, ni les sauvegardes des joueurs.
Les contrôles portent sur les liens, les noms techniques référencés et la cohérence
du parcours avec les sources. Aucun serveur n'est démarré pour cette modification.
