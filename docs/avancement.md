# Avancement TerraCraft

Dernière passe : 7 octobre 2026 — version **0.27.0**.

## Fonctionnel et raccordé

- [x] Monde Terre réelle avec relief, routes, eau et bâtiments Overture/OpenFreeMap.
- [x] Génération des bâtiments limitée par type et hauteur.
- [x] Menu TerraCraft `O`, fiche joueur `K`, missions, `/aide` et carnet de survie avec les touches.
- [x] Parcours « Premiers pas » (6 étapes récompensées, encart à l'écran, `/tuto`).
- [x] Accueil des habitués (titre, solde, contrats, ville, métier, nouveautés), liste des joueurs (Tab), MOTD et icône du serveur.
- [x] Économie persistante, hôtel des ventes graphique : objets conservés à l'identique, sans duplication.
- [x] Hôtel des ventes : catégories, prix moyens, icônes, vente directe ; comptoir du serveur ; frais de 2 % ; `/eco stats`.
- [x] Voiture, camion et moto : assemblage, conduite, carburant, coffre, propriétaire et partage ; progression comptée une seule fois par véhicule.
- [x] Fusée, orbite terrestre, orbite lunaire, Lune, Mars et orbite de Mars ; carburant par trajet (8 doses), Mars après la Lune.
- [x] Station orbitale d'abord : kit déployé au premier vol (salle de travail, tunnels, stockage, quai, sas) ; la Lune et Mars partent d'un quai.
- [x] Fusées à 1-4 réservoirs (8-20 doses, charges utiles), propulseurs et hublots.
- [x] Bases lunaire et martienne en kit, rover lunaire solaire ; plus de dégâts de chute en fusée.
- [x] Sous-sol lunaire : cavernes géantes, sanctuaires extraterrestres, donjons, cristaux, artefacts.
- [x] Carte des étoiles (écran de navigation), `/fusee carte` et `/fusee cap` ; dossier `mods/` du client sur le dépôt.
- [x] Dangers lunaires : micrométéorites (abri sous un toit), rôdeurs renforcés la nuit lunaire.
- [x] Rejoindre en un import (pack dans le dépôt, serveur déjà dans la liste) ; `REJOINDRE.md`.
- [x] Visuel : jour/nuit normaux, barres de vie, chat, menu `O` en tuiles, villes en préfixe ; toutes les fenêtres au même style.
- [x] Confirmations avant de fonder ou dissoudre une ville et de vendre à prix cassé (`/confirmer`, `/annuler`).
- [x] Événements sur Terre : convois militaires en panne, zones contaminées.
- [x] Vagues nocturnes sur les villes ; PvE partout, PvP dans les zones déclarées.
- [x] Boss de bunker (Commandant), primes sur les joueurs.
- [x] Étals de marché (magasins de joueurs).
- [x] Villes : adjoints ; claims des habitants comptés comme territoire de la ville (à vérifier en jeu).
- [x] Bêta : règles (`/regles`), modérateurs (`/mod`), suivi de l'activité (`/terracraft suivi`).
- [x] Événement d'ouverture : course à l'espace (`/course`).
- [x] Stations : kit de module pressurisé 7×5×7, balise de station (arrivée des fusées), `/station`.
- [x] Plans de fusée (`/plans`) et atelier de station : réservoir étendu, moteur ionique, soute (touche V), navigation martienne.
- [x] Combinaison spatiale (touche `J` et panneau de l'inventaire `E`) : casque, combinaison, bottes magnétiques, jetpack, deux réserves d'oxygène, rendu en armure, HUD O₂.
- [x] Jetpack : poussée en maintenant saut, carburant au bidon, compatible anti-triche.
- [x] Avion : kit, carburant, pilotage au regard, décrochage, crash, instruments de bord.
- [x] Villes : fondation (500 crédits), invitations, maire, trésorerie, `/ville tp`, titres d'entrée et de sortie.
- [x] Coffre du véhicule : bouton du menu O et touche V.
- [x] Contrats du jour (3 par jour), nouvelles missions, écran des missions refait.
- [x] Métiers (`/metier`) avec bonus branchés sur véhicules, armes, jetpack, avion et attributs.
- [x] Territoires : propriétaire affiché en changeant de chunk (Open Parties and Claims).
- [x] Ravitaillements militaires toutes les 45 à 75 min (`/terracraft largage` pour les admins).
- [x] `/signaler` : rapports de bugs enregistrés, alerte aux opérateurs.
- [x] Survie : téléportations refusées en combat, `/tpa` limitée.
- [x] Anti-triche vol (allow-flight est forcé pour l'espace).
- [x] Données joueurs écrites de façon atomique avec copie `.bak`.
- [x] Sauvegardes automatiques du monde, rotation, restauration testée.
- [x] Versions des mods tiers figées (`tools/mods.lock.json`), identiques serveur et pack.
- [x] Test de démarrage d'un vrai serveur avant chaque déploiement Falix (CI).
- [x] Test de scénario avec faux joueurs (`tools/scenario_test.sh`, 70 vérifications) : villes, économie, combinaison, oxygène, métiers, largages, station, vol en fusée jusqu'à la balise, plans et atelier, MOTD et icône.

## À tester en jeu

- [ ] Inventaire E : panneau de la combinaison, shift-clic, clic sans jeter l'objet, mode créatif.
- [ ] Hôtel des ventes : catégories, vente depuis l'écran, comptoir, frais.
- [ ] Jetpack : poussée, carburant, recharge, flammes, pas de faux positif anti-triche.
- [ ] Avion : décollage, virages, décrochage, atterrissage, crash, passager.

- [ ] Combinaison spatiale : rendu sur le joueur (avec et sans armure), autres joueurs, migration depuis la 0.6.
- [ ] Moto : montage avec deux roues, conduite, démontage et reconnexion.
- [ ] Mars : premier voyage, arrivée, gravité, oxygène, retour et génération de chunks.
- [ ] Orbite de Mars : quai, oxygène, bottes magnétiques, station et retour.
- [ ] Anti-triche vol : aucun faux positif en jeu normal (sauts, chutes, véhicules, échelles).
- [ ] Sauvegardes automatiques sur Falix : durée, taille et espace disque.

## Prochaine séquence

Voir l'ordre de développement de la [feuille de route](feuille-de-route.md) et l'[état du projet](etat-du-projet.md) :
séance de test à plusieurs et mesures, uniformisation de l'interface, événements et règles PvP.

## Règle de publication

Une version est publiée seulement si le build passe, si le serveur démarre (test automatique), si le client et le
serveur utilisent le même JAR, si le `.mrpack` est régénéré et si la branche `falix` contient les mods serveur aux noms fixes.
