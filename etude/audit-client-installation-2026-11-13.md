# Audit du client, de l'installation et du contenu TerraCraft

13 novembre 2026 · mod 0.41.0 (Minecraft 26.3) · commit `d76c415` · troisième volet, après
[l'audit de la carte](audit-generation-carte-2026-11-13.md) et [l'audit du serveur](audit-serveur-complet-2026-11-13.md).

Ce volet couvre ce que les deux premiers n'ont pas regardé : le chemin du joueur depuis le téléchargement jusqu'à la
connexion, le code client (écrans, carte, mixins), le contenu (recettes, butins, traductions), le serveur local et
les outils. Rien n'a été compilé ni joué.

---

## 1. En bref

| Domaine | État | Constat principal |
|---|---|---|
| **Version livrée aux joueurs** | 🔴 | Le serveur tourne en **0.41.0**, mais tous les liens de téléchargement donnent la **0.39.0** (J1). |
| Installeurs | 🟡 | Empreintes SHA-512 vérifiées, anciens mods sauvegardés ; chemins du pack non contrôlés (J2). |
| Code client | 🟢 | Respect des règles OSM et Nominatim ; la carte garde ~100 Mo de textures après fermeture (K1). |
| Mixins | 🟢 | Uniquement des `@Inject`, contrôles serveur corrects sur l'emplacement créatif. |
| Contenu | 🟢 | 84 recettes, 45 objets, 39 blocs vérifiés par les outils ; traductions fr/en complètes. |
| Serveur local, outils | 🟢 | Interface web limitée à `127.0.0.1`, EULA jamais acceptée à la place de l'utilisateur. |

**Action immédiate (5 minutes) : publier la release 0.41.0** (§ 3, J1).

---

## 2. Points forts

- **Installeurs prudents** : ils mettent les anciens mods de côté (`mods-avant-terracraft-<date>`), ne remplacent jamais
  la liste Multijoueur du joueur, et vérifient l'empreinte SHA-512 de chaque mod téléchargé.
- **Licences respectées** : les mods de Xaero, qu'on n'a pas le droit de republier, sont téléchargés depuis Modrinth
  avec une empreinte (`mods/a-telecharger.json`).
- **Bons usages des services OSM** : les tuiles (`MapTileCache`) utilisent un User-Agent identifiable, deux connexions
  au plus, un cache disque de 30 jours, aucune pré-récupération, et l'attribution est affichée. La recherche de lieux
  (`Geocoder`) respecte une requête par seconde et ne part qu'à la validation, sans autocomplétion. C'est exactement
  ce qu'exige Nominatim.
- **Envois protégés** : le serveur vérifie `canSend` avant d'envoyer au client les messages des PNJ, de la carte de
  départ et du tutoriel. Un client trop ancien ne plante donc pas.
- **Mixins sobres** : 4 côté serveur, 9 côté client (291 lignes), uniquement des `@Inject`, sans `@Overwrite` ni
  `@Redirect`. C'est le style le plus compatible avec Sodium et Iris. `defaultRequire: 1` fait échouer le démarrage
  si une mise à jour de Minecraft casse un point d'injection : l'erreur est visible tout de suite.
- **`ServerCreativeSlotMixin`** accepte les emplacements de la combinaison en créatif, avec toutes les vérifications
  nécessaires : mode créatif réel (`hasInfiniteMaterials`), bornes de l'index, type d'objet (`mayPlace`) et taille de
  pile.
- **Contenu cohérent** : `tools/check_recipes.py` donne « 84 recettes, 84 objets vérifiés » et
  `tools/check_assets.py` donne « 45 objets, 39 blocs vérifiés ». `fr_fr.json` et `en_us.json` ont tous deux 202 clés,
  sans aucune clé manquante.
- **Serveur local** : `serveur-local/web/server.py` n'écoute que sur `127.0.0.1` et vérifie les coordonnées. Les
  scripts ne cochent jamais l'EULA à la place de l'utilisateur.

---

## 3. Constats

### J1 — Les joueurs téléchargent une version en retard de deux versions 🔴

| Ce qu'on trouve | Version |
|---|---|
| Serveur Falix (branche `falix`, `d5602eb`, 13/11) | **0.41.0** |
| `geo-mod/gradle.properties` sur `main` | **0.41.0** |
| Dernière release GitHub (`releases/latest`, lien du README) | 0.39.0 (11/10) |
| `TerraCraft-client.mrpack` à la racine (lien de `REJOINDRE.md` et `installer/README.md`) | 0.39.0 |
| `mods/terracraft-geo.jar` (méthode manuelle) | 0.39.0 |

**Cause** : la CI ne crée une release que sur un **tag** `v*` (`build.yml`, étapes « Pack client » et « Release »).
Aucun tag n'a été posé depuis `v0.39.0`. Pendant ce temps, chaque push sur `main` part en production (constat D1 de
l'audit serveur). Le pack du dépôt et `mods/` ne sont mis à jour qu'à la main (`tools/sync_client_mods.py`, « après
la release »).

**Conséquence** : il n'y a ni nouveau bloc ni nouvel objet entre 0.39 et 0.41, donc un joueur en 0.39 se connecte
sans erreur. Mais les nouveautés des 0.40 et 0.41 (PNJ à IA, campagne Terra Exodus, environ 1 700 lignes) lui
restent invisibles : le serveur voit qu'il ne peut pas lui envoyer `NpcChatStatePayload` et n'envoie rien. Aucun
message ne l'avertit, puisque le mod ne compare jamais la version du client à celle du serveur. La séance de jeu
réelle recommandée en priorité 1 serait donc faussée.

**Correction** :
1. Tout de suite, poser le tag `v0.41.0` sur `d76c415` et le pousser. La CI crée la release, et la branche `falix`
   n'est pas touchée : son étape ne tourne que sur `refs/heads/main`. Ensuite, lancer `tools/sync_client_mods.py`
   pour aligner `mods/` et le pack du dépôt.
2. Durablement, au choix :
   - faire du tag le seul déclencheur de la production, ce qui règle aussi D1 : le serveur et le pack sortent ensemble ;
   - ou faire créer la release par la CI dès que `mod_version` change sur `main`.
3. Pointer `REJOINDRE.md` et `installer/README.md` vers `releases/latest/download/…` comme le README, au lieu de
   `raw/main/…`. Il n'y a alors plus qu'une seule source.
4. Contrôler la version à la connexion : le client envoie la sienne, et le serveur affiche « Ton pack est en 0.39,
   le serveur en 0.41 : réimporte le pack » avec le lien. C'est environ 30 lignes avec un payload de plus.

### J2 — Installeurs : chemins du pack non contrôlés 🟡

`install-windows.ps1` et `install-linux.sh` écrivent chaque fichier à l'endroit indiqué par `path` dans
`modrinth.index.json`. Ils ne vérifient pas que ce chemin reste dans `.minecraft`, ni que l'URL est bien sur
`cdn.modrinth.com` ou `github.com`. Le format Modrinth impose pourtant aux launchers de rejeter les chemins en
`..`. La confiance repose sur le compte GitHub. Le risque supplémentaire est faible : un pack piégé pourrait de
toute façon livrer un mod malveillant. Le contrôle reste à ajouter, c'est l'affaire de 3 lignes : rejeter un `path`
qui contient `..` ou commence par `/`, et n'accepter que les domaines autorisés.

### J3 — Petits défauts des installeurs 🟢

- Sous Windows, le fichier est écrit **avant** la vérification d'empreinte. En cas d'échec, le mod corrompu reste
  dans `mods/`. La version Linux vérifie avant d'écrire, et `mods/telecharger-restants.ps1` supprime le fichier. Il
  suffit d'ajouter `Remove-Item $target` avant le `throw`.
- Les installeurs ne prennent que les fichiers « required ». Les joueurs du launcher officiel n'ont donc jamais
  Sodium ni Distant Horizons, et jouent moins fluide que ceux de Prism. On peut le signaler dans `REJOINDRE.md`, ou
  poser une question oui/non dans l'installeur.
- L'installeur Fabric est téléchargé sans empreinte, mais depuis `maven.fabricmc.net` en HTTPS. Ajouter le SHA-256
  connu de la 1.1.2 ne coûte rien.
- `curl … | bash` est courant mais exige de faire confiance au script. Proposer aussi « télécharger, lire, lancer ».

### K1 — La carte du monde garde ~100 Mo en mémoire après fermeture 🟡

`MapTileCache` garde jusqu'à 384 textures de 256 × 256 pixels, soit environ 96 Mo. `WorldMapScreen.removed()` ne les
libère pas, donc elles restent jusqu'à la fermeture du jeu. Sur un PC modeste déjà chargé par Distant Horizons, ça
compte. Correction : libérer toutes les textures à la fermeture de l'écran (le cache disque rend la réouverture
rapide), ou passer à 128 entrées.

### K2 — Cache disque des tuiles jamais nettoyé 🟢

`terracraft-cache/osm` ne fait que grossir. Les tuiles de plus de 30 jours sont retéléchargées mais aucun fichier
n'est supprimé, et le zoom va jusqu'à 19. Correction : au démarrage, supprimer les fichiers de plus de 60 jours ou
plafonner le dossier à 500 Mo.

### K3 — Textes en dur 🟢

On trouve 370 `Component.literal("…")` en français dans le code serveur, contre 202 clés traduites. Un joueur
anglophone verra un mélange des deux langues. C'est sans importance pour un serveur francophone. À reprendre
seulement si le serveur s'ouvre à l'international.

### K4 — Ciel spatial et shaders : à tester 🟢

`SkyRendererMixin` remplace la Lune et ajoute des éléments au ciel. Avec Iris et un shader actif, c'est le shader
qui dessine le ciel, et ces ajouts risquent de ne pas apparaître. À vérifier lors de la séance de jeu, avec et sans
Complementary.

### T1 — Tests Python du banc serveur non portables 🟢

`python -m unittest discover -s tools/tests` lance 20 tests : 7 échouent sous Windows (`WinError 87`, scripts
bash). Ils sont faits pour Linux et la CI. Ce n'est pas un bug, mais il faut le préciser dans le README des outils,
ou marquer ces tests `skipUnless(os.name == "posix")`.

### W1 — Interface web du serveur local 🟢

`server.py` ne vérifie pas l'en-tête `Content-Type`. Une page web malveillante ouverte en même temps pourrait donc
envoyer un `POST` à `localhost:8080/api/selection` et changer le point choisi. L'impact est nul, l'outil ne sert
qu'au développement. `index.html` charge Leaflet depuis unpkg sans empreinte (`integrity`) : en ajouter une.

---

## 4. Ajouts au plan d'action global

Ces étapes s'insèrent dans le plan du § 6 de l'audit serveur :

| Ordre | Chantier | Effort | Origine |
|---|---|---|---|
| **0** | Tag `v0.41.0` puis `sync_client_mods.py` ; liens `REJOINDRE.md` vers `releases/latest` | S (5 min) | J1 |
| 1 bis | Contrôle de version client/serveur à la connexion | S | J1 |
| 3 (élargi) | Un seul déclencheur pour la production **et** la release : le tag | S | J1 + D1 |
| 9 bis | Libérer les textures de la carte, nettoyer le cache disque | S | K1, K2 |
| 10 bis | Installeurs : chemins et domaines contrôlés, suppression en cas d'empreinte fausse | S | J2, J3 |

---

## Annexe — Ce qui a été vérifié

- `installer/install-windows.ps1`, `installer/install-linux.sh`, `mods/telecharger-restants.ps1`,
  `mods/a-telecharger.json`, `REJOINDRE.md`, `installer/README.md`, `mods/LISEZMOI.md`.
- Le contenu de `TerraCraft-client.mrpack` (index, overrides, version du jar inclus) et de `mods/terracraft-geo.jar`.
- Les releases GitHub (API publique), la branche `origin/falix` et les derniers passages de la CI sur `main`.
- `build.yml` (étapes falix, pack, release) et les nouveaux payloads entre 0.39.0 (`2369c3a`) et 0.41.0.
- Le code client : `MapTileCache`, `Geocoder`, `WorldMapScreen` (recherche, fermeture), les récepteurs de
  `GeoModClient`. Les 4 mixins serveur ont été lus en entier ; pour les 9 mixins client, seuls les points d'injection
  ont été relevés.
- Les outils exécutés : `check_recipes.py`, `check_assets.py`, `unittest` sur `tools/tests`. La parité des clés de
  traduction a été vérifiée.
- `serveur-local/web/server.py`, `index.html` et les scripts `start-*.sh`.

Non vérifié : l'affichage réel des écrans, l'équilibrage des recettes et des butins (seule une séance de jeu peut le
dire), le rendu avec shaders.
