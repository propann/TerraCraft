# PNJ et dialogues de campagne

Profils adaptés des six personnages du [DOCX](sources-terra-exodus.md).
Le [fichier d'exemple](../../config/pnj/terra-exodus.example.json) utilise les champs
de la [PR #5](https://github.com/propann/TerraCraft/pull/5) : `name`, `role`, `faction`,
`personality`, `goal`. Il garde l'IA désactivée par défaut.

## Identités conservées

| ID de profil | Personnage | Quêtes visées | Ligne d'écriture |
|---|---|---|---|
| `helene_vance` | Commandant Hélène Vance | `q1_1_ashes` à `q1_4_trade` | Directe, militaire et protectrice ; deux phrases. Expliquer la fondation par les commandes et distinguer registre et claims. |
| `boulon_kowalski` | Marcus « Boulon » Kowalski | `q2_1_oil_hunt` à `q2_4_logistics` | Mécanicien passionné, rugueux mais amical ; environ trente mots. Le titane ne bloque pas son enseignement terrestre. |
| `aris_thorne` | Docteure Aris Thorne | `q3_1_eva_prep` à `q3_4_moon_step` | Calme, précise, attentive à l'oxygène et au retour ; deux ou trois phrases courtes. |
| `sujet_09` | Sujet-09, « L'Écho » | `q3_5_ghosts_he3` | Phrases brèves, souvenirs fragmentaires et statique discrète. Informateur distinct des ennemis `lost_astronaut`. |
| `soren_kael` | Capitaine Soren Kael | `q4_1_shipyard` à `q4_3_orion_glyphs` | Déterminé et solidaire ; environ vingt-cinq mots. Valoriser un quai sûr et une colonie qui fonctionne. |
| `archiviste_orion` | L'Archiviste d'Orion | `q5_1_zero_g` à `q5_3_artifact` | Solennel et impartial ; deux phrases. Transmettre uniquement les révélations écrites et autorisées. |

Les limites de mots sont des consignes de style. Le contrôle actuel du prototype
porte notamment sur le format, la longueur et les contrats autorisés ; il ne
vérifie pas automatiquement chaque affirmation narrative.

## Prévisualisation avec le prototype PNJ

Après installation de la version comprenant la PR #5, copier l'exemple dans le
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

Créer chaque personnage depuis sa position voulue. Le prototype utilise des mobs
vanilla ; ces profils ne créent pas les bases, les sanctuaires ni une apparence
holographique. Garder Sujet-09 sur une entité amicale : lier une identité à un
`lost_astronaut` ne supprime pas automatiquement son comportement hostile.

Le modèle, son URL et son activation se configurent selon le guide de la PR #5.
Le fichier d'exemple n'ajoute ni clé, ni MCP, ni exposition réseau.

## Contexte présent et contexte à ajouter

La PR #5 transmet l'identité, `player`, `job`, `town`, `memory`, `message` et
`available_quests`. Le modèle renvoie `speech` et `quest_id` ; le serveur valide
la proposition parmi les contrats du jour existants.

Elle ne transmet pas encore `current_quest`, `dimension`, le chapitre ou les archives
connues. Les marqueurs `{player_name}`, `{current_quest}`, `{dimension}` et `{town_name}`
du DOCX ne sont pas des variables automatiquement interpolées par ce code.
Les 19 quêtes de campagne ne sont pas dans les candidats du modèle.

Pour la campagne, le serveur devra fournir le chapitre, les faits validés, la
dimension, la quête active et les révélations autorisées. Il filtrera les objectifs
avant l'appel IA et revérifiera l'état au retour. Un PNJ avancé placé trop tôt ne
doit pas donner accès à un chapitre verrouillé.

Les secrets d'un chapitre futur ne doivent pas être envoyés au modèle lors d'un
dialogue précoce. Une phrase de prompt « ne révèle pas la fin » ne remplace pas
ce contrôle. L'Archiviste a un rôle narratif de gardien ; le modèle ne décide ni
de la vérité du monde, ni de la réussite d'une quête, ni d'une récompense.

## Répliques préparées

Textes éditoriaux à intégrer, sans injection automatique par ce lot :

- **Hélène :** « D'abord un abri, ensuite des vivres. Une ville tient debout si chacun protège son secteur et aide ses voisins. »
- **Marcus :** « Du pétrole sans pompe, c'est une flaque. Branche ton énergie, vérifie tes tuyaux, et on parlera de carburant pour les étoiles. »
- **Aris :** « Vérifie ta tenue, ton oxygène et le carburant du retour. Notre première destination est l'orbite terrestre. »
- **Sujet-09 :** « [Bzzz…] Nous avions laissé des repères dans la poussière. Les pierres se souviennent mieux que moi. »
- **Soren :** « Une coque ne fait pas encore un foyer. Prépare ton quai, teste ton vaisseau et assure le retour de ton équipage. »
- **Archiviste, au stade autorisé :** « Vous avez appris à revenir vers les vôtres. La réparation du berceau commence par ce que vous choisissez de préserver. »
