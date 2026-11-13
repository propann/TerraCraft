# PNJ avec identités, mémoire et quêtes

Première version : `0.40.0`. Le mod appelle directement une API Ollama ou Mistral,
sans MCP ni service intermédiaire à installer. Un modèle peut servir plusieurs
PNJ ; chaque mob conserve une identité et une mémoire différente pour chaque joueur.

## Ce qui est jouable

- Un administrateur crée Léa, mécanicienne du refuge, ou lie une identité à un mob existant.
- Un clic droit ouvre un dialogue ; `/pnj dire <message>` permet de lui répondre.
- Le contexte contient l'identité, le métier et la ville du joueur, les derniers échanges,
  les contrats du jour disponibles et leur progression.
- Le modèle choisit un contrat parmi ceux que le joueur peut réellement remplir et réclamer.
- `/pnj mission` affiche l'objectif ; `/pnj reclamer` appelle la validation des contrats existants.
- Le PNJ se souvient des trois derniers échanges après une reconnexion ou un redémarrage.
- Si l'IA est désactivée, indisponible ou produit une réponse invalide, un dialogue local
  et un contrat disponible prennent le relais.

Cette version apporte le dialogue et la distribution contextuelle des contrats du jour.
La locomotion et le combat restent gérés par Minecraft. Les objectifs et récompenses
proviennent du catalogue serveur ; les missions libres et les réactions autonomes aux
besoins d'une ville sont les prochaines extensions.

## Installation locale avec Ollama

1. Installer un modèle compatible avec les réponses JSON dans Ollama. Le nom `mistral`
   des exemples doit correspondre au modèle installé sur l'hôte ; aucun modèle n'est téléchargé par le mod.
2. Copier [l'exemple Ollama](../config/pnj/ollama.example.json) dans le dossier d'exécution
   du serveur, sous `config/terracraft-npcs.json`.
3. Adapter `model`, puis lancer `/pnj recharger` dans la console ou comme administrateur.
4. En jeu, exécuter `/pnj creer lea`, puis faire un clic droit sur Léa.

Sans configuration, le fichier est créé au démarrage avec `ai_enabled: false` :
le PNJ fonctionne en mode local et aucun appel IA n'est effectué.

`127.0.0.1` désigne l'hôte où Minecraft tourne. Un serveur Falix ne peut donc pas
joindre l'Ollama de ton ordinateur par cette adresse. Pour cette disposition,
prévoir une liaison privée accessible depuis Falix et configurer son URL ;
ce lot n'ouvre aucun port et ne met en place aucune exposition LAN ou publique.

## API Mistral

Copier [l'exemple Mistral](../config/pnj/mistral.example.json) au même emplacement.
Choisir un modèle disponible dans le compte et définir `TERRACRAFT_MISTRAL_API_KEY`
dans l'environnement du processus serveur avant son démarrage. Le fichier ne
contient que le nom de la variable, jamais la clé. Les appels utilisent le compte
Mistral configuré ; aucune requête à une API payante ne fait partie des tests.

## Identités et commandes

Chaque entrée de `personas` définit `name`, `role`, `faction`, `personality` et `goal`.
L'identifiant, par exemple `lea`, contient 1 à 32 lettres minuscules, chiffres,
tirets ou traits bas. Le mob porte l'étiquette persistante `terracraft_npc:lea`.

| Commande | Accès | Effet |
|---|---|---|
| `/pnj` | Joueur | Parler au PNJ le plus proche |
| `/pnj dire <message>` | Joueur | Envoyer au maximum 500 caractères |
| `/pnj mission` | Joueur | Consulter le contrat proposé |
| `/pnj reclamer` | Joueur | Réclamer sa récompense si le contrat est terminé |
| `/pnj creer lea` | Administrateur, niveau 2 | Créer un villageois nommé, immobile et invulnérable |
| `/pnj lier lea <sélecteur>` | Administrateur, niveau 2 | Associer l'identité à un mob existant, dont le comportement vanilla est conservé |
| `/pnj editer <id> nom <texte>` | Administrateur, niveau 2 | Changer le nom affiché |
| `/pnj editer <id> role <texte>` | Administrateur, niveau 2 | Changer le rôle |
| `/pnj editer <id> faction <texte>` | Administrateur, niveau 2 | Changer la faction |
| `/pnj editer <id> personnalite <texte>` | Administrateur, niveau 2 | Ajuster la voix, les habitudes et le tempérament |
| `/pnj editer <id> humeur <texte>` | Administrateur, niveau 2 | Ajuster l'humeur et le ton du moment |
| `/pnj editer <id> objectif <texte>` | Administrateur, niveau 2 | Définir ce qui motive le personnage |
| `/pnj recharger` | Administrateur, niveau 2 | Relire les identités et l'adaptateur IA |

Les champs se modifient aussi en jeu avec `/pnj editer <id> <champ> <texte>` ;
les changements sont écrits dans `config/terracraft-npcs.json`, appliqués immédiatement
et le nom au-dessus des PNJ déjà placés est actualisé. Les valeurs sont bornées
pour garder les profils lisibles et éviter une configuration démesurée.

Il faut se trouver à six blocs maximum, dans la même dimension. Une réponse
retardée est écartée si le joueur se déconnecte, s'éloigne, change de jour ou si
la configuration est rechargée. Lier une autre identité remplace l'ancienne étiquette.

## Contrôle du jeu et performances

La réponse du modèle contient exactement `speech` et `quest_id`. Le mod refuse un
identifiant hors de la liste transmise, les champs supplémentaires et les dialogues
trop longs. Les messages sont du texte littéral ; aucune commande proposée par le
modèle n'est exécutée. Le contrat est revérifié au retour de l'IA, puis au paiement.

Le système existant garde la progression, les montants et l'unicité de la récompense.
Ses limites de résistance à un arrêt brutal restent celles du
[chantier de fiabilisation](fiabilisation.md) ; ce lot ne crée pas un second portefeuille.

Les requêtes HTTP utilisent deux threads, une file de huit demandes au maximum et
un délai global de 15 s par défaut, configurable entre 1 et 60 s. Chaque joueur
dispose d'une seule requête en cours et d'un délai de quatre secondes entre deux
dialogues. Les réponses HTTP sont limitées à 64 Kio. Les lectures et mutations
Minecraft restent sur le fil serveur ; aucune inférence n'est exécutée par tick.

## Mémoire et données transmises

Le fichier `<monde>/terracraft_geo/pnj-memoires.json` conserve les six derniers tours
de parole par couple UUID joueur/mob, le contrat choisi et sa journée. Le stockage
est borné à 10 000 couples ; les premières entrées sont retirées au-delà. Il utilise
les écritures atomiques de `JsonStore` et appartient aux données du monde sauvegardées.

Seuls l'identité du PNJ, le nom, le métier et la ville du joueur, les échanges avec
ce PNJ et les contrats disponibles sont envoyés au modèle. Le chat global, les
coffres et les notes documentaires ne sont pas consultés. Les identités peuvent
être versionnées dans les exemples ; les mémoires des joueurs et les clés restent hors Git.

## Validation et retour arrière

`gradle --no-daemon build` dans `geo-mod/` compile et lance les tests JUnit : contrats
autorisés, champs inattendus, tailles, adaptateurs HTTP Ollama/Mistral et expiration du délai.
Le [scénario PNJ](../tools/npc-scenario-test.sh) utilise un vrai serveur Fabric avec
un modèle HTTP simulé en boucle locale. Il vérifie dialogue, mémoire après
redémarrage, rejet d'un contrat inventé, pannes, délai, absence de crédit indu.
La CI joue ensuite le scénario multijoueur général.

```bash
VERSION="$(sed -n 's/^mod_version=//p' geo-mod/gradle.properties)"
TEST_WORK="$(mktemp -d /tmp/terracraft-pnj.XXXXXX)"
tools/npc-scenario-test.sh "geo-mod/build/libs/terracraft-geo-$VERSION.jar" "$TEST_WORK"
```

Lancer le scénario depuis un checkout complet, avec Java 25 et l'accès aux
téléchargements Fabric/Modrinth. Le dossier doit être vide ou déjà réservé aux tests.
Un essai avec le modèle réellement choisi reste nécessaire avant activation en production.

La publication de la 0.40 suit la procédure du dépôt : contrôles au vert, tag de
version, pack client régénéré par la CI puis réalignement de `mods/` et du pack
avec `tools/sync_client_mods.py`. Cette branche prépare le code ; aucun tag ni
déploiement Falix n'est effectué pendant son développement.

Pour arrêter les appels IA, passer `ai_enabled` à `false` puis `/pnj recharger`.
Pour retirer une identité d'un mob, retirer son étiquette avec `/tag` ; ses
échanges restent dans le fichier de mémoire jusqu'à une suppression administrée.
Le lot ajoute des étiquettes vanilla et un fichier séparé : il ne migre pas le monde.

Références des protocoles : [Ollama, API Chat](https://docs.ollama.com/api/chat),
[Mistral, Chat Completions](https://docs.mistral.ai/api/endpoint/chat),
[Mistral, réponses structurées](https://docs.mistral.ai/inference/conversations/structured-output/custom).
