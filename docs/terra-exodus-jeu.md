# Jouer à TERRA EXODUS

Version proposée : **0.41.0**, PR [#7](https://github.com/propann/TerraCraft/pull/7),
à intégrer après les PR de [fiabilisation #4](https://github.com/propann/TerraCraft/pull/4)
et de [PNJ #5](https://github.com/propann/TerraCraft/pull/5).
Aucun déploiement automatique n’est effectué depuis cette branche.

## Première arrivée et livre

Après le choix géographique, le kit initial comprend pain, torches, épée,
boussole et **TERRA EXODUS — Les Vestiges d’Orion**. `Arrival.welcome` remet le
livre à la première arrivée seulement. Les conseils techniques de l’ancien carnet
sont conservés, avec une pagination limitée à treize lignes courtes par page.

Le livre contient le prologue de 2089, les objectifs accessibles et l’aide de
survie. Les pages narratives des actes ultérieurs apparaissent avec la progression.
Les personnages ne connaissent pas l’origine exacte de la Contamination.

`/exodus livre` récupère le journal perdu ou actualise l’exemplaire identifié
à son propriétaire dans l’inventaire. Cette commande ne redonne pas le kit initial.
Un inventaire plein fait tomber le livre aux pieds du joueur. Un ancien carnet
non identifié est conservé. Une copie déposée dans un coffre n’est pas recherchée.
Les vétérans peuvent commencer la campagne sans changer de monde ni de départ.

## Objectifs et personnages

`/exodus` donne les objectifs accessibles et leurs compteurs. Le menu Missions
présente trois onglets paginés : **Campagne**, **Contrats**, **Libres**. Le bouton
Lire détaille les preuves (cliquer à nouveau pour la page de détail suivante) ; `/exodus` fournit également leur texte complet.

| Commande | Usage |
|---|---|
| `/exodus reclamer <id>` | Vérifier les preuves et remettre la récompense |
| `/missions reclamer exodus:<id>` | Même remise depuis les missions et leur interface |
| `/exodus livre` | Actualiser ou récupérer son livre |
| `/pnj` ou `/pnj dire <texte>` | Parler à un contact situé à six blocs au maximum |
| `/pnj mission`, `/pnj reclamer` | Voir ou réclamer l’objectif proposé par ce contact |
| `/exodus archives` | Lire une archive lunaire ou martienne accessible |
| `/exodus signal` | Localiser le site martien ou le temple accessible |
| `/exodus epreuve` | Déclencher le gardien de son épreuve dans le temple |
| `/exodus noyau` | Récupérer le Noyau après la quête du gardien |
| `/exodus contribuer` | Déposer son Noyau au registre de sa ville |
| `/exodus restaurer` | Activer la restauration, pour le maire ou un adjoint |
| `/exodus registre` | Consulter les régions et leurs contributeurs |
| `/aide exodus` | Aide courte en jeu |

Le serveur possède les six identités de campagne, en plus de Léa. Elles sont
chargées depuis `terra-exodus-personas.json` dans le mod et peuvent être
personnalisées dans `config/terracraft-npcs.json`. Un fichier existant garde ses
personnages personnalisés ; les identités canoniques sont ajoutées en mémoire.

L’administration place les contacts aux refuges, ateliers et bases voulus avec
`/pnj creer <identite>` ou lie un mob existant avec `/pnj lier <identite> <mob>` :

| Acte | Identité | Personnage |
|---|---|---|
| I | `helene_vance` | Commandant Hélène Vance |
| II | `boulon_kowalski` | Marcus « Boulon » Kowalski |
| III | `aris_thorne` | Docteure Aris Thorne |
| III | `sujet_09` | Sujet-09 / L’Écho, contact amical distinct des ennemis |
| IV | `soren_kael` | Capitaine Soren Kael |
| V | `archiviste_orion` | L’Archiviste d’Orion |

Les contacts créés sont persistants, immobiles et protégés des dégâts.
Leur placement reste une opération d’administration : ils ne sont pas générés
aléatoirement dans chaque ville. La campagne fonctionne aussi depuis le journal
sans attendre qu’un contact soit placé.

**MCP n’est pas nécessaire.** Par défaut l’IA est désactivée ; les dialogues locaux
proposent les quêtes autorisées. Ollama ou l’API Mistral peuvent enrichir les
répliques, selon [la configuration PNJ](pnj-intelligents.md). Seuls les identifiants
transmis par le serveur sont acceptés ; une réponse IA ne crée ni objectif,
récompense, objet ou restauration. Les faits narratifs sont limités dans le contexte
et dans la consigne système ; le texte libre d’un modèle reste à évaluer pour sa
fidélité au canon.

## Industrie et exploration

Le premier clic sur une pompe, raffinerie ou serre lui attribue un propriétaire de
télémétrie, conservé dans le bloc (`TelemetryOwner`). L’attribution reste inchangée
quand un autre joueur utilise la machine. Casser et reconstruire le bloc permet
une nouvelle attribution. Les protections de construction habituelles s’appliquent.
Attribuer la machine avant sa production : les cycles antérieurs ne sont pas reconstitués.

Les ruines martiennes et temples d’Orion apparaissent dans les **nouveaux chunks**
de leurs dimensions, tous les 512 blocs. Les coordonnées sont indiquées par le
signal. Aucun bâtiment existant n’est remplacé. Un ancien chunk déjà exploré
ne reçoit pas rétroactivement de structure : rechercher le site suivant dans
un secteur encore vierge. Le sanctuaire lunaire existant garde son rôle précoce.

Le temple est une première arène compacte : piliers de glyphes, murs et sol
brûlant autour du gardien. Chaque joueur déclenche son gardien, avec barre de vie.
Un autre joueur peut aider si le propriétaire reste à moins de 48 blocs. Une mort
sans source joueur ne valide pas l’épreuve ; le gardien peut alors être relancé.
Un gardien vivant reste référencé par son UUID et est recentré s’il s’éloigne.

La lecture martienne vérifie la position, le socle et les quatre glyphes du site.
Elle ne débloque pas elle-même le plan de navigation martienne : ses règles
existantes, les ressources et l’amélioration de fusée restent nécessaires.

## Noyau et restauration coopérative

Le Noyau est un `alien_artifact` nommé **Noyau d’Orion**, avec identifiant unique et
propriétaire dans ses données. Un artefact ordinaire, un objet simplement renommé
ou un Noyau appartenant à quelqu’un d’autre est refusé. Les récupérations répétées
conservent le même identifiant ; toutes leurs copies ne peuvent servir qu’à un seul dépôt.

Tenir son Noyau en main près d’un registre (quatre blocs horizontalement, trois
verticalement), dans les 64 blocs du centre de sa ville terrestre, puis contribuer.
Le dépôt est enregistré avant de retirer l’objet. Le maire ou un adjoint active
ensuite la restauration, depuis le registre. Le dépôt ne nécessite pas d’élection.
Une ville déjà restaurée accepte la contribution d’un nouveau joueur éligible,
qui peut ainsi terminer sa campagne sans créer une seconde région.

La région couvre un disque de **64 blocs de rayon autour du centre municipal au
moment du premier dépôt**. Déplacer ensuite le centre ne déplace pas cette région.
Les effets périodiques de Contamination y cessent ; les effets déjà actifs expirent
normalement. Aucun terrain, claim, inventaire ni bâtiment n’est remplacé. Le reste
du monde conserve ses zones contaminées. Les régions et contributeurs constituent
un registre distinct des premières historiques de `SpaceRace`. Le nom des contributeurs y figure ; la distinction « Éveilleur d’Orion » apparaît dans leur fiche de personnage. Le livre actualisé contient le dénouement après les 19 remises.

## Données et reprise

Sous `<monde>/terracraft_geo/` :

- `terra-exodus.json` : version de schéma, preuves, quêtes réclamées, remise en
  attente, Noyau, contribution, gardien et régions. Les preuves courantes sont
  enregistrées toutes les dix secondes et à l’arrêt ; une remise ou un dépôt
  provoque une écriture immédiate.
- `portefeuilles.json` : document économique faisant autorité, réunissant soldes,
  annonces, reçu de paiement et journal économique. Au premier chargement, les
  anciens fichiers sont importés s’il n’existe pas encore. Les fichiers
  `balances.json`, `hotel-des-ventes.json` et `economie.json` restent des exports
  de compatibilité ; ne pas les modifier pour changer l’état économique.
- `progression.json` : points de campagne et reçus de points dans le même document.
- `pnj-memoires.json` : souvenirs et objectif proposé, comme dans la PR PNJ.

Une remise s’effectue en quatre étapes : enregistrer la quête en attente, créditer
le portefeuille avec un reçu unique, enregistrer les points avec leur reçu, puis
marquer la quête terminée. La reprise d’une remise en attente se fait à la connexion,
sans second versement. Les écritures utilisent les temporaires et sauvegardes de
`JsonStore`. Une lecture sur sauvegarde conserve une copie de diagnostic du primaire
corrompu et empêche sa recopie sur la sauvegarde saine.

Le mod refuse de repartir d’une campagne ou d’un portefeuille illisible sans
sauvegarde exploitable. Une restauration manuelle doit remettre **l’ensemble
cohérent du monde**, comprenant ces journaux et les données des joueurs. Restaurer
séparément un ancien reçu ou un seul fichier peut annuler cette cohérence.
Cette protection contre les répétitions ne remplace pas les sauvegardes du monde
ni une base transactionnelle entre fichiers.

## Administration et vérification

Permissions : création/reconfiguration PNJ au niveau opérateur 2 ; commandes
`/exodus admin` au niveau 4. Les remises ordinaires restent accessibles aux joueurs.

- `/exodus admin valider <joueur> <id>` valide une étape de préparation sans
  récompense. L’opération est journalisée et peut ignorer les dépendances.
- `/exodus admin reinitialiser <joueur>` efface objectifs et progression de
  campagne, sans supprimer les reçus économiques, les Noyaux déposés ou les régions.
  Une remise déjà en cours doit d’abord se terminer.

Avant exploitation : sauvegarder le monde et installer le même JAR serveur/client.
Les structures exigent des chunks nouveaux. Le pack client distribué n’est pas
réécrit par cette PR ; le produire lors de la release prévue.

Vérifications : JUnit pour les dépendances, seuils, reprise des identités et
pagination ; scénario Fabric à deux joueurs pour le livre, le PNJ local, la
production attribuée, une archive martienne générée, une serre sur Mars, le gardien, le faux Noyau, les permissions municipales,
la contamination, les doublons, le redémarrage et la reprise injectée d’une remise interrompue. Ce scénario prépare explicitement
une fouille et valide administrativement les étapes intermédiaires : il ne constitue
pas une partie complète des 19 quêtes. Les scénarios PNJ et multijoueur existants
restent requis. L’équilibrage, le rendu du livre et de l’interface ainsi que la
partie complète doivent encore être évalués par des joueurs.

Réseau : aucun nouveau port de service. Tests liés à `127.0.0.1` ; aucune clé IA
nécessaire. L’IA ne contacte son endpoint configuré que si elle est activée.
