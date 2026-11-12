# Quêtes et progression — TERRA EXODUS

Spécification d'intégration issue des cinq tableaux de quêtes du
[DOCX fourni](sources-terra-exodus.md). Les **19 identifiants d'origine sont conservés**.
Ils ne sont pas encore enregistrés dans `Missions.java`. Les récompenses et les
déblocages de cette campagne ne sont pas activés par cette documentation.

Les intitulés des actes et la fiction sont décrits dans [la trame](terra-exodus.md).
Les écarts avec le code sont recensés dans [l'analyse technique](analyse-terra-exodus.md).

## Acte I — Survie et renaissance civique

Donneur principal : **Hélène Vance**.

| ID conservé | Titre | Objectif retenu pour l'intégration | Support et travail restant |
|---|---|---|---|
| `q1_1_ashes` | Premiers Pas dans les Cendres | Choisir son secteur, fouiller des ruines et obtenir une arme de défense. | Carte et butin présents ; utiliser `loot` / `scavenger` pour la récupération. La condition d'arme et le regroupement en quête restent à ajouter. |
| `q1_2_night` | Sous les Étoiles Hostiles | Défendre son refuge lors d'un raid et repousser des monstres. | Découverte `rampart` et compteur `kills` présents. `contaminated_zombie` et `purifier_vent` sont absents : aucune quête ne doit exiger ces objets/entités avant leur création. |
| `q1_3_town` | L'Acte Fondateur | Rejoindre ou fonder une ville, puis protéger un premier chunk. Le registre est son mobilier civique. | Mission `citizen`, compteur `town` et claims présents. La pose du registre ne crée pas la ville. Les protections de claims restent à tester avec de vrais clients. |
| `q1_4_trade` | Le Pouls du Commerce | Installer un étal et terminer un contrat ou une mission économique accessible. | Étals, métiers et contrats présents. Définir un événement de fin de contrat et une vérification de l'étal utilisable, sans confondre choix d'un métier et contrat terminé. |

Le raid dépend d'une ville : `q1_2_night` peut être proposée tôt, mais sa validation
se fait après l'accès à une ville de `q1_3_town`. **L'ordre visuel des ID n'est pas
une dépendance stricte** : `q1_1` ouvre `q1_3`, puis `q1_2` et `q1_4`. Le chapitre
suivant exige les quatre objectifs, avec un parcours solo prévu.

## Acte II — Pétrole et industrie

Donneur principal : **Marcus « Boulon » Kowalski**.

| ID conservé | Titre | Objectif retenu pour l'intégration | Support et travail restant |
|---|---|---|---|
| `q2_1_oil_hunt` | Chasseur d'Or Noir | Fabriquer le détecteur et localiser un gisement naturel. | `oil_detector` et pétrole présents ; ajouter une preuve serveur de prospection réussie. Posséder un bloc d'huile dans l'inventaire ne prouve pas la découverte d'un gisement. |
| `q2_2_iron_blood` | Le Sang de la Terre | Mettre en marche une pompe et un réseau électrique terrestre. | Machines et connexions présentes ; ajouter l'observation d'un fonctionnement effectif. Les seize lingots de titane du DOCX sont déplacés vers une activité lunaire, afin de ne pas bloquer le premier départ. |
| `q2_3_refining` | Raffinage Stratégique | Relier la raffinerie et produire dix bidons d'essence, puis une dose de carburant de fusée. | Employer `fuel_can` et `rocket_fuel`, pas `rocket_fuel_bucket`. Ajouter une attribution de production au joueur ou à sa ville ; déplacer dix fois un même bidon ne doit pas compter dix productions. |
| `q2_4_logistics` | Maîtrise des Continents | Assembler un véhicule terrestre ou un avion, puis récupérer un largage ou terminer une activité de ravitaillement supportée. | Mission `mechanic` / compteur `vehicles`, véhicules et largages présents. La voie avion demande un signal de validation ; l'escorte de convoi mobile attend son implémentation. |

Les activités de transport doivent avoir une alternative réalisable depuis le point
de départ choisi. Les contrats quotidiens servent de soutien ; aucun tirage journalier
précis ne doit être indispensable pour débloquer la campagne.

## Acte III — Orbite et Lune

Donneurs : **Aris Thorne**, puis **Sujet-09 / L'Écho**.

| ID conservé | Titre | Objectif retenu pour l'intégration | Support et travail restant |
|---|---|---|---|
| `q3_1_eva_prep` | Équipement Extravéhiculaire | Équiper casque, combinaison et bottes, préparer des réserves d'oxygène utilisables. | Objets et système de combinaison présents ; lire les emplacements de `SpaceSuit`, sans se limiter à l'inventaire. Le compresseur du DOCX n'est pas un prérequis existant. |
| `q3_2_orbit` | Cap sur l'Orbite | Assembler et alimenter une fusée, puis atteindre l'orbite terrestre. | `launches`, découverte `orbit` et mission `launch` présents. Conserver des ressources de retour. |
| `q3_3_station` | Avant-Poste Orbital | Déployer le kit de station et vérifier atelier, balise et accès à l'oxygène. | Kits, stations et mission `orbital_builder` présents. Le kit peut être déployé pendant le premier voyage : créditer ce fait même si `q3_3` devient visible après `q3_2`. |
| `q3_4_moon_step` | Un Pas sur la Lune | Atterrir sur la Lune avec une base, de l'oxygène et un rover. | Découverte `moon`, stations de surface et rover présents. Relire la base enregistrée, pas seulement la possession d'un kit. |
| `q3_5_ghosts_he3` | Les Fantômes de 2089 et l'Hélium-3 | Explorer un sanctuaire lunaire, récolter l'hélium-3 et des cristaux, retrouver une archive de l'expédition. | `helium`, `titanium` et découverte `alien_sanctuary` présents. Ajouter les archives, les compteurs de victimes par type et les objectifs groupés. Un `lost_astronaut` ordinaire n'est pas un mini-boss spécifique. |

Les quantités du DOCX — seize ressources d'hélium-3, huit cristaux et douze rôdeurs —
sont des **cibles d'équilibrage à tester**, pas des seuils ajoutés au code. Distinguer
le bloc `helium3_crystals` de l'éclat `helium3_shard` utilisé dans les recettes.

Avant Mars, le parcours doit garantir une source raisonnable de l'artefact nécessaire
à `Plans.MARS_NAV`. Les tables de butin sont aléatoires : prévoir une remise unique
ou une autre solution déterministe, validée par le serveur, plutôt qu'un blocage
indéfini. Ne pas réserver tous les artefacts au final.

## Acte IV — Mars et vaisseaux modulaires

Donneur principal : **Capitaine Soren Kael**.

| ID conservé | Titre | Objectif retenu pour l'intégration | Support et travail restant |
|---|---|---|---|
| `q4_1_shipyard` | Chantier Naval Spatial | Construire un vaisseau valide, décoller puis se poser ou s'amarrer en conservant sa cargaison. | `BuiltShip`, `ShipStructure`, `ShipCarrier` et blocs présents. Ajouter un événement de vol construit réussi. Une simple possession de coque n'est pas une preuve. |
| `q4_2_mars_colony` | La Colonie Rouge | Installer la navigation martienne, rejoindre Mars, établir une base et obtenir une récolte de serre alimentée. | `Plans`, découverte `mars`, bases et serre présents. Vérifier la dimension et une récolte effective ; une serre posée n'établit pas une autosuffisance mesurée. |
| `q4_3_orion_glyphs` | Les Glyphes d'Orion | Découvrir des archives martiennes et reconstituer la piste vers le relais final. | Ruines martiennes, analyseur, événement de lecture et coordonnées narratives à créer. Des blocs `alien_stone` / `alien_glyph` existants ne prouvent pas une structure martienne. |

La navigation martienne exige actuellement une visite lunaire, cinq minerais de
titane minés et l'amélioration correspondante, qui consomme deux éclats d'hélium-3,
un bloc de redstone et un artefact. Les « coordonnées » du scénario n'ajoutent pas
automatiquement cette amélioration. Les trajets des vaisseaux doivent rester
compatibles avec les règles de `SpaceTravel`.

## Acte V — Le Noyau d'Orion

Donneur principal : **L'Archiviste d'Orion**.

| ID conservé | Titre | Objectif retenu pour l'intégration | Support et travail restant |
|---|---|---|---|
| `q5_1_zero_g` | Dérive en Zéro-G | Rejoindre les astéroïdes et effectuer une sortie équipée avec un retour sûr. | Dimension, découverte `asteroids`, jetpack et bottes présents ; définir un critère serveur d'EVA complète avec équipement, trajet et retour. |
| `q5_2_sanctuary` | L'Assaut du Sanctuaire | Localiser le sanctuaire orbital, réussir ses épreuves et vaincre son gardien. | Lieu orbital, défenses, champ de force et `sanctuary_guardian` à développer. Le sanctuaire lunaire actuel reste une étape antérieure. |
| `q5_3_artifact` | L'Artefact de Régénération | Obtenir le Noyau d'Orion distinct, le ramener et restaurer une région autorisée. | Noyau, activation, état régional et registre de campagne à développer. Un `alien_artifact` générique ne déclenche pas la victoire. |

Le texte « purification globale » du DOCX devient une **restauration régionale
persistante et bornée**. La portée, l'autorisation du territoire, les contributions,
la consommation du noyau et les effets doivent être définis et testés avant livraison.
Une réussite n'efface pas les villes, les claims ni le terrain existant.

## Récompenses et activités secondaires

Les crédits sont un solde serveur, pas un objet `terracraft:credits`. Les montants
50 / 100 / 150 / 200 / 300 / 500 / 1 000 / 1 500 / 5 000 et les XP du DOCX sont
des propositions à équilibrer. Aucun de ces montants n'est ajouté par ce lot.
Conserver les récompenses déjà réclamées des missions permanentes et des contrats.

Les titres, réputations de faction, augmentations de claims, Mode Prestige et objets
spéciaux du document demandent leurs propres systèmes. Ne pas en promettre la remise
tant que leurs identifiants et leurs effets ne sont pas disponibles.

| Activité du DOCX | Base actuelle | Extension proposée |
|---|---|---|
| Primes de chasse | Primes sur joueurs et règles PvP. | Tableau physique, cibles PvE, infamie, tirage toutes les six heures et jetons de faction. |
| Convois et largages | Convois militaires en panne et caisses de ravitaillement. | Convois mobiles, escorte, interception et marqueurs de carte adaptés. |
| Fret interplanétaire | Stock commun de ville accessible par le terminal. | Cargaisons identifiées, trajet, destinataire et réception ; retirer un stock partagé sur une autre planète ne prouve pas un transport physique. |
| Course saisonnière de factions | Jalons de première découverte par joueur. | Saisons, contributions collectives, classement de factions, cosmétiques et bonus à équilibrer séparément. |

## État de campagne et validation

La campagne sera enregistrée par UUID de joueur, avec une version de schéma,
les objectifs accomplis et un registre de récompenses idempotent. La réussite
collective finale utilisera des identifiants stables de communauté et de région.
Le `player_data.json` du DOCX est une proposition, pas un fichier déjà lu par le mod.

Les vétérans doivent pouvoir faire reconnaître des faits antérieurs vérifiables
(visite, ville, station, amélioration), sans payer une deuxième fois leurs anciennes
missions. La progression principale doit rester monotone après un départ de ville,
une mort ou un changement de véhicule. Les compteurs de production nouveaux demandent
une règle d'attribution ; un simple inventaire de ressources est insuffisant.

Pour chaque chapitre, tester joueur neuf et vétéran, mode solo, coopération, mort,
reconnexion, redémarrage, double clic, minuit des contrats et requête IA retardée.
Le moteur serveur valide les faits ; une déclaration du modèle ou du joueur n'est
jamais une preuve de réussite.

## Textes d'accueil préparés

Texte proposé pour `Welcome`, après conservation des règles et du choix géographique :

> Bienvenue dans TERRA EXODUS. Depuis l'Effondrement de 2089, les villes attendent
> ceux qui sauront les reconstruire. Choisis ton point de chute : trouve un abri,
> rassemble des ressources et rejoins un refuge. Un signal venu des étoiles subsiste.

Le tutoriel existant garde ses étapes utiles : maison, premier coffre, claim,
marché, fiche et première mission. Son intégration narrative ne doit pas demander
le `purifier_vent` absent ni prétendre que le registre fonde la ville.

Proposition pour le guide : « Le refuge est ton point de départ. L'industrie te
donne les moyens de voyager ; les quais rendent possible le retour. Les archives
d'Orion se révèlent à mesure que tu explores et aides ta communauté. »

Les clés `fr_fr.json` du DOCX ne deviennent actives que si le code utilise ces clés.
Les descriptions actuelles de `Missions.java` sont des chaînes Java : leur migration
et la vue en chapitres demandent une implémentation, pas seulement un ajout au JSON.
