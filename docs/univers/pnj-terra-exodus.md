# PNJ et dialogues de campagne

Profils adaptés des six personnages du [DOCX](sources-terra-exodus.md).
Le [fichier d'exemple](../../config/pnj/terra-exodus.example.json) utilise les champs
de la [PR #5](https://github.com/propann/TerraCraft/pull/5) : `name`, `role`, `faction`,
`personality`, `goal`. Il garde l'IA désactivée par défaut.

En 0.41.0, les profils sont livrés dans le mod et leurs quêtes sont proposées
selon les prérequis serveur. Leur placement, leur personnalisation et les limites
des dialogues sont détaillés dans le [guide de jeu](../terra-exodus-jeu.md).
Les propositions de contexte plus bas décrivent la conception initiale ; le code
transmet désormais le chapitre, la dimension, le canon autorisé et les quêtes accessibles.

## Identités conservées

| ID de profil | Personnage | Quêtes visées | Ligne d'écriture |
|---|---|---|---|
| `helene_vance` | Commandant Hélène Vance | `q1_1_ashes` à `q1_4_trade` | Directe, militaire et protectrice ; deux phrases. Expliquer la fondation par les commandes et distinguer registre et claims. |
| `boulon_kowalski` | Marcus « Boulon » Kowalski | `q2_1_oil_hunt` à `q2_4_logistics` | Mécanicien passionné, rugueux mais amical ; environ trente mots. Le titane ne bloque pas son enseignement terrestre. |
| `aris_thorne` | Docteure Aris Thorne | `q3_1_eva_prep` à `q3_3_station` | Calme, précise, attentive à l'oxygène et au retour ; deux ou trois phrases courtes. |
| `sujet_09` | Sujet-09, « L'Écho » | `q3_4_moon_step` et `q3_5_ghosts_he3` | Phrases brèves, souvenirs fragmentaires et statique discrète. Informateur distinct des ennemis `lost_astronaut`. |
| `soren_kael` | Capitaine Soren Kael | `q4_1_shipyard` à `q4_3_orion_glyphs` | Déterminé et solidaire ; environ vingt-cinq mots. Valoriser un quai sûr et une colonie qui fonctionne. |
| `archiviste_orion` | L'Archiviste d'Orion | `q5_1_zero_g` à `q5_3_artifact` | Solennel et impartial ; deux phrases. Transmettre uniquement les révélations écrites et autorisées. |

Les limites de mots sont des consignes de style. Le contrôle actuel du prototype
porte notamment sur le format, la longueur et les contrats autorisés ; il ne
vérifie pas automatiquement chaque affirmation narrative.

## Placement des personnages

Les six profils sont livrés en 0.41.0. Pour les personnaliser, copier l’exemple dans le
dossier d'exécution du serveur sous `config/terracraft-npcs.json`. Depuis un compte
administrateur en jeu :

```text
/pnj recharger
/pnj creer helene_vance
/pnj creer boulon_kowalski
```

Si une configuration existe déjà, y fusionner les entrées de `personas` en conservant
les profils utiles et les paramètres du modèle. Le fichier d'exemple comporte six
profils et ne remplace pas automatiquement les réglages du serveur.

Créer chaque personnage depuis sa position voulue. Le mod utilise des mobs
vanilla ; ces profils ne créent pas les bases, les sanctuaires ni une apparence
holographique. Garder Sujet-09 sur une entité amicale : lier une identité à un
`lost_astronaut` ne supprime pas automatiquement son comportement hostile.

Le modèle, son URL et son activation se configurent selon le guide de la PR #5.
Le fichier d'exemple n'ajoute ni clé, ni MCP, ni exposition réseau.

## Contexte transmis en 0.41.0

Le serveur transmet `identity`, `player`, `job`, `town`, `memory`, `message`,
`available_quests` et `campaign`. Ce dernier contient le chapitre, la dimension
et le canon autorisé à ce stade. Chaque quête candidate comprend son identifiant,
son titre, ses objectifs avec compteurs, sa progression et sa récompense.
Les PNJ canoniques proposent seulement les étapes accessibles de leur propre acte ;
Léa conserve les contrats quotidiens. Une réponse IA est revérifiée sur le serveur.

Le modèle répond avec `speech` et `quest_id`. Il n’interpole pas automatiquement
les marqueurs du DOCX : le contexte est un objet JSON structuré. Il peut reformuler
les objectifs transmis ; il ne valide jamais les preuves ni une remise.

Les secrets d’un chapitre futur ne sont pas ajoutés au contexte canonique initial.
Les souvenirs viennent des dialogues précédents et le texte d’un modèle reste
à vérifier pour sa fidélité narrative. Les mécanismes de jeu ne dépendent pas
de cette fidélité : même un dialogue erroné ne crée aucune récompense.

## Répliques locales livrées

Ces répliques fonctionnent lorsque l’IA est désactivée. Elles sont suivies, si une
quête est autorisée, de sa proposition et de `/pnj mission`.

- **`helene_vance` :** « Un refuge tient grâce à ses habitants. Consulte /pnj mission : nous avançons une preuve à la fois. »
- **`boulon_kowalski` :** « Le brut ne se raffine pas avec des promesses ! Clique tes machines avant de les faire tourner : leur production alimentera ton journal. »
- **`aris_thorne` :** « Vérifie ton oxygène et garde de quoi rentrer. Ton journal indique les étapes de préparation du vol. »
- **`sujet_09` :** « [Bzzz…] Les souvenirs restent dans la pierre. Approche des archives que ton journal autorise. »
- **`soren_kael` :** « Un vaisseau doit revenir entier. Notre colonie a besoin de récoltes, pas seulement de coques et de promesses. »
- **`archiviste_orion` :** « Les archives retiennent les actes des voyageurs. Ton journal indique l’épreuve qui t’est ouverte. »
