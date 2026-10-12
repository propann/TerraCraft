# TerraCraft — piste économie DUCO

**Statut :** étude / proposition, aucun code de production modifié  
**Branche :** `economy/duinocoin`, créée depuis `main`  
**Décision proposée :** commencer par un prototype local opt-in. Garder les crédits TerraCraft actuels inchangés jusqu'à ce que l'intégration DUCO, la sécurité et le modèle économique aient passé leurs portes de validation.

## Intention produit

Créer une boucle de jeu reconnaissable et amusante : le joueur construit ou pose un petit rig Arduino dans TerraCraft, le configure simplement et peut choisir de lancer un mineur qui crédite son compte DUCO externe. Le rig en jeu donne une identité et une progression à l'activité; il ne constitue pas lui-même un processeur et ne doit pas lancer de calcul invisible.

Le DUCO est présenté comme un compte externe à TerraCraft. La documentation officielle décrit un compte dont le nom d'utilisateur sert d'adresse/identifiant de portefeuille, et un mineur officiel pour ESP/Arduino. Ce modèle de compte n'est pas équivalent à la création locale d'un portefeuille à clés privées. TerraCraft ne doit donc jamais demander ni conserver le mot de passe DUCO ou la clé de mineur.

## État constaté dans TerraCraft

L'économie existante est une économie de crédits internes, avec soldes associés aux UUID Minecraft et sauvegardés dans `<world>/terracraft_geo/balances.json`. Le système comporte déjà commandes, statistiques et opérations de transfert. Ce registre est la référence des prix du jeu aujourd'hui.

**Conséquence :** le prototype DUCO reste une piste distincte et n'altère pas les crédits ni les prix. Faire du DUCO la monnaie officielle du serveur nécessite une décision ultérieure sur les paiements, l'identité, la trésorerie, les remboursements, la disponibilité des comptes et la comptabilité.

## Architecture cible du prototype

- **Compte DUCO :** le joueur crée et contrôle son compte sur le service officiel DUCO. TerraCraft peut guider vers ce parcours et mémoriser uniquement le nom de compte après association explicite.
- **Association de compte :** définir une preuve de contrôle avant toute récompense ou affichage privé. Ne pas considérer le simple fait de saisir un nom d'utilisateur comme une preuve suffisante. Étudier une association par signature/défi ou un parcours officiel compatible; si aucune preuve sûre n'existe, limiter la première démo à une configuration locale sans association serveur.
- **Mineur :** utiliser le logiciel officiel ou un firmware officiel pour Arduino/ESP dans un test séparé. Toute installation et tout démarrage sont déclenchés par le joueur. Pas d'installation opaque, d'autodémarrage, de mot de passe collecté par le plugin ou de mineur embarqué caché.
- **Objet Arduino en jeu :** objet de gameplay et panneau de contrôle/état (arrêté, actif, en pause, erreur, compte associé). Le serveur vérifie que l'objet existe et accorde éventuellement une progression de jeu; l'exécution réelle reste dans un processus choisi et contrôlé par le joueur.
- **Sources de calcul :** distinguer un ESP/Arduino physique du PC du joueur. Le premier consomme l'énergie et les ressources de son propriétaire; un mineur PC consomme le CPU et l'électricité locale, avec rendement variable et potentiellement faible. Les deux doivent être décrits et arrêtables facilement.
- **Serveur :** aucune charge de minage sur Falix pendant l'étude. L'idée d'un petit mineur « serveur » est une porte séparée : validation écrite des limites d'hébergement, puis mesure CPU/RAM/réseau et rendement. Le serveur de jeu ne doit pas monopoliser les ressources de génération, sauvegarde ou joueurs.

## Plan de travail et critères de sortie

### E0 — Découverte et contraintes

- Confirmer les comptes DUCO, l'API, les méthodes d'association, le mineur ESP/Arduino officiel, la licence et les règles d'usage applicables.
- Inventorier la version Java/Minecraft, le chargeur, les commandes et la persistance de l'économie actuelle.
- Mesurer l'empreinte de base du serveur local (RAM, CPU, TPS, temps de sauvegarde), puis conserver ces mesures comme témoin.

**Sortie :** note de décisions avec sources et limites, aucun changement de comportement.

### E1 — Expérience DUCO hors jeu

- Créer un compte de test dédié, sans compte personnel ni fonds de valeur.
- Configurer un ESP/Arduino compatible avec le logiciel officiel et vérifier que le solde DUCO évolue.
- En parallèle, mesurer un mineur PC seulement si l'expérience exige vraiment un client logiciel; commencer arrêté, demander un opt-in clair et définir une limite CPU.
- Comparer démarrage, arrêt, reconnexion Wi-Fi, coupure, latence, consommation électrique et durée nécessaire à une récompense observable.

**Sortie :** preuve reproductible, estimation prudente de rendement (non garantie), et choix ESP physique, PC local, ou abandon du PC.

### E2 — Prototype local minimal

- Ajouter uniquement sur cette branche une interface de démonstration locale : objet Arduino, état lisible, commandes explicites démarrer/pause/arrêter et consignes de configuration.
- Garder le mineur comme programme séparé, lancé explicitement par le joueur ou sur son appareil physique. Le plugin ne reçoit aucun secret DUCO.
- Lier un UUID Minecraft à un nom DUCO seulement après avoir défini une preuve de contrôle sûre. Sinon ne pas exposer de solde et ne pas attribuer de récompenses de jeu.
- Garder les récompenses DUCO directes vers le compte DUCO du joueur; aucun échange automatique en crédits.

**Sortie :** démonstration où l'arrêt est immédiat, l'état est compréhensible, et l'économie interne reste identique.

### E3 — Économie et identité

Décider séparément entre ces modèles :

1. **DUCO externe de joueur :** le joueur mine pour son compte; les crédits TerraCraft restent la monnaie de jeu. C'est le premier modèle recommandé.
2. **DUCO comme monnaie du serveur :** le serveur reçoit les DUCO et paie des achats/transferts. Cela introduit une trésorerie et une logique de paiement, des risques de compte, de double dépense, d'indisponibilité et des règles de remboursement. Ne pas commencer par ce modèle.
3. **Récompenses de jeu adossées au minage :** TerraCraft attribue des crédits selon des preuves externes. À écarter tant que la preuve de travail, l'anti-fraude et les coûts ne sont pas spécifiés; un client ne peut pas être considéré comme fiable simplement parce qu'il affirme miner.

**Sortie :** décision écrite sur la monnaie, le taux éventuel, les frais, les plafonds et la gestion des litiges, avant tout changement de prix ou solde.

### E4 — Test serveur local et budget 8 Go

- Tester sur serveur local avec une limite réaliste : serveur + clients + système doivent rester sous 8 Go; mesurer la mémoire au repos et en charge.
- Enregistrer TPS, CPU, mémoire, réseau et temps de sauvegarde à l'état de référence, puis avec le prototype actif.
- Critères de départ proposés : aucun changement de la séquence d'apparition/spawn; pas de tâche CPU lourde sur le thread serveur; TPS stable; mémoire sans croissance continue; arrêt propre au redémarrage; aucune écriture du mineur dans les données de génération.
- Tester plusieurs joueurs, déconnexions, redémarrage, chunks chargés/déchargés, réassociation de compte et fichier de soldes existant.
- Si le mineur tourne sur un PC client, mesurer le serveur sans lui attribuer sa charge; si un worker serveur est envisagé, faire d'abord le test séparé et isolé sur machine locale, jamais sur l'hébergement de jeu.

**Sortie :** rapport avant/après avec traces reproductibles. Échec d'un critère = retour au design, pas de déploiement.

### E5 — Décision d'hébergement et mise en service éventuelle

L'hébergement actuel dispose d'un budget global de 8 Go. La règle d'usage acceptable de Falix prévoit des restrictions lorsque la consommation CPU, mémoire, disque ou réseau devient excessive ou dégrade les autres services. Aucun worker DUCO ne doit être lancé sur Falix sans confirmation écrite des limites et autorisation applicables.

Un éventuel mineur serveur doit être isolable, plafonné, désactivable par configuration et supprimé sans toucher au monde ni à l'apparition. Le déploiement ne se fait qu'après réussite locale, revue de sécurité, décision économique et validation explicite de l'hébergement. La branche de génération de bâtiments reste indépendante.

## Mesures comparables à produire

| Domaine | Mesure | Témoin / comparaison |
|---|---|---|
| Serveur | RAM min/moyenne/pic, CPU, TPS, durée de tick | avant / après; mineur serveur arrêté / actif |
| Client | CPU moyen/pic, température disponible, RAM, watts si mesurables | jeu seul / jeu + mineur; plusieurs limites CPU |
| DUCO | temps de connexion, rejets, solde et durée observée | ESP / éventuel PC; mêmes conditions autant que possible |
| Robustesse | reconnexion, arrêt, coupure, redémarrage, répétition | cycles reproductibles |
| Économie | crédits internes et soldes persistés | empreinte/diff avant / après; aucune altération en E0–E2 |
| Jeu | spawn, chunks, interactions d'objet, charge multi-joueur | protocole existant et même zone |

Publier la date, versions, matériel, configuration et durée de chaque mesure. Ne pas comparer des rendements issus de difficultés/réseaux différents comme s'ils étaient garantis.

## Sécurité et limites non négociables du prototype

- Arrêt par défaut; activation volontaire et visible par joueur.
- Aucun minage au démarrage du jeu, en arrière-plan après sa fermeture, ou sans indicateur.
- Aucun mot de passe DUCO, clé de mineur ou secret enregistré dans les fichiers de configuration TerraCraft, journaux, serveur, cache géographique ou sauvegarde Minecraft.
- Pas de crédit de jeu basé sur une télémétrie fournie par le client sans protocole de vérification indépendant.
- Pas de transfert, conversion, marché, paiement ou garde de fonds dans la première démo.
- Pas de minage serveur chez l'hébergeur tant que la politique et le budget exacts ne sont pas validés.
- Prévoir désinstallation/révocation de liaison et éviter que l'absence de DUCO empêche de jouer.

## Sources de travail

- [DUCO — dépôt officiel](https://github.com/revoxhere/duino-coin) : projet, logiciels de minage et informations de licence.
- [DUCO — Wiki / Getting Started](https://github.com/revoxhere/duino-coin/wiki/Getting-Started) : création de compte et configuration.
- [DUCO — API REST officielle](https://github.com/duino-coin/duco-rest-api) : endpoints compte, authentification et clé mineur; examiner précisément avant tout appel.
- [Falix — Acceptable Use Policy](https://falixnodes.net/acceptable-use-policy) : règles de consommation équitable des ressources; revalider avant déploiement.
- TerraCraft `Economy.java` et `AuctionHouse.java` sur `main) : économie interne et persistance actuelle.

## Décisions ouvertes pour la suite

1. L'objet Arduino représente-t-il un vrai ESP physique en priorité, un mineur PC volontaire, ou les deux?
2. Le premier jalon doit-il seulement montrer le statut d'un mineur local, ou tenter une liaison compte DUCO ↔ UUID dès le début?
3. L'ambition de monnaie serveur signifie-t-elle, à terme, achats directs en DUCO ou simplement un thème/récompense externe? Garder cette décision après la preuve locale de faisabilité.
