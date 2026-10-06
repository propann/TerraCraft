# Localisation et génération du monde

## Concept

Avant de jouer, le joueur ouvre une carte mondiale, la déplace et la zoome, puis clique sur le lieu où il veut apparaître. Après confirmation, le serveur transforme ce choix en une ancre géographique et génère uniquement la région nécessaire autour d'elle. La localisation automatique du PC reste facultative : le choix manuel suffit.

La carte Minecraft pourra combiner relief, routes, bâtiments, cours d'eau, points d'intérêt, végétation indicative et météo locale. Nous ne chercherons pas à générer toute la planète d'un coup : le monde sera construit par secteurs autour de l'ancre, avec cache et génération à la demande.

## Règles de confidentialité

- La localisation est facultative et désactivée par défaut.
- Le joueur voit la zone proposée et la confirme avant tout envoi.
- Nous envoyons une cellule ou un rayon approximatif, jamais les coordonnées GPS exactes.
- Le serveur ne conserve pas l'historique des déplacements.
- Le joueur peut choisir une zone fictive ou déplacer l'ancre manuellement.
- Les données brutes restent sur la machine et ne sont pas affichées aux autres joueurs.

## Architecture proposée

```text
Carte mondiale (écran de choix)
  → zoom + clic + confirmation
  → coordonnées arrondies / cellule géographique
Service d'import
  → relief + OSM + météo
  → cache local avec attribution et licences
Serveur Fabric
  → validation de la zone et quotas
  → génération / cache des chunks Minecraft
```

## Carte de sélection

La première interface sera une carte mondiale vectorielle basée sur **MapLibre GL JS** : elle permet le zoom, le déplacement, les styles propres et la sélection précise d'une rue ou d'un bâtiment. Les tuiles pourront venir d'OpenFreeMap pour le prototype, puis d'un cache ou d'une instance maîtrisée si le projet devient public. MapLibre rend les tuiles vectorielles dans le navigateur et permet de contrôler l'apparence de la carte. Le joueur choisit uniquement un point de départ ; les véhicules seront étudiés plus tard dans le gameplay.

La recherche par nom de rue sera séparée de l'affichage : elle utilisera un service de géocodage respectueux des limites d'utilisation, avec temporisation et cache. Le clic sur la carte restera toujours disponible, même sans recherche.

Un serveur Minecraft ne peut pas lire le GPS d'un PC par magie : il faudra un mod client ou une application compagnon. Fabric fournit les points d'entrée client/serveur et les mécanismes réseau nécessaires pour ce type d'échange. La génération devra aussi respecter les licences des données cartographiques utilisées.

## Sources gratuites envisagées

- **Carte et objets géographiques :** OpenStreetMap, avec attribution obligatoire et respect de la politique des tuiles ; pour un service public, prévoir un fournisseur de tuiles ou un cache maîtrisé plutôt que de surcharger `tile.openstreetmap.org`.
- **Affichage de la carte :** MapLibre GL JS + tuiles vectorielles OpenFreeMap pour le prototype ; MapLibre est une bibliothèque libre de rendu, pas une base de données géographique.
- **Relief :** SRTM ou un autre modèle numérique d'élévation global distribué par l'USGS.
- **Météo :** Open-Meteo pour température, pluie, neige, vent, nuages et prévisions. L'accès est sans clé pour l'usage gratuit non commercial ; les limites et l'attribution devront être vérifiées avant un déploiement public.

Les données météo servent à l'ambiance et au gameplay ; elles ne doivent pas être utilisées pour déduire ou afficher la position réelle d'un joueur.

## Stratégie des tuiles

La carte mondiale de sélection reste légère et peut utiliser les tuiles vectorielles en ligne. Après le clic du joueur, le serveur prépare une zone limitée autour du point choisi :

1. calculer la boîte géographique autour de l'ancre ;
2. récupérer les tuiles et données nécessaires à plusieurs niveaux de zoom ;
3. les conserver dans un cache local avec leur attribution ;
4. générer les chunks Minecraft à partir du cache ;
5. supprimer ou renouveler le cache selon une durée configurable.

On ne téléchargera pas la planète entière pour le prototype. Si le projet devient public, nous pourrons auto-héberger les tuiles ou utiliser un fournisseur adapté, plutôt que dépendre d'un service public sans garantie de disponibilité.

## Première version testable

Commencer avec une carte mondiale simplifiée, trois zones fictives, le zoom et le clic de sélection. Ensuite ajouter un secteur réel, le relief, puis OSM et la météo. La localisation automatique réelle viendra seulement après validation du parcours manuel.
