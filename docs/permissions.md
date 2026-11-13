# Rôles et permissions TerraCraft

État de référence : version 0.41.0.

Ce document distingue les accès qui existent dans le serveur des permissions détaillées prévues pour la suite. Une commande visible dans l'aide n'est pas forcément accessible à chaque rôle : Minecraft masque les commandes interdites dans l'autocomplétion.

## Accès actuellement en place

| Rôle | Attribution | Accès |
|---|---|---|
| Joueur | Automatique à la connexion | Commandes de jeu, chat, missions et actions prévues par les règles. |
| Membre d'une ville | Invitation acceptée | Actions que la logique de ville autorise à ses membres. |
| Maire ou adjoint | Rôle enregistré dans la ville | Gestion municipale prévue pour ce rôle : habitants, trésorerie, élections ou restauration, selon la commande. |
| Modérateur | Opérateur : `/moderateurs ajouter <joueur>` | Commandes de modération `/mod`. Ce rôle n'accorde pas le statut d'opérateur. |
| Opérateur / administrateur | Accès accordé dans la console Falix ou par les opérateurs existants | Commandes d'administration et attribution/retrait des modérateurs. C'est l'accès Minecraft le plus large. |

Le rôle de modérateur est conservé par UUID dans `<monde>/terracraft_geo/moderateurs.json`. Les silences temporaires sont conservés en mémoire et disparaissent au redémarrage. Les commandes de ville et de campagne vérifient leurs rôles et conditions propres ; elles ne dépendent pas de la liste des modérateurs.

À ce stade, TerraCraft ne dispose pas d'un système général où l'administrateur peut accorder une permission précise pour chaque commande. Une permission de modérateur n'ouvre que les opérations codées dans `/mod`. Les commandes à accès élevé s'appuient sur les permissions d'opérateur/gamemaster de Minecraft.

## Outils d'équipe disponibles

- `/moderateurs` : consulter la liste (opérateurs).
- `/moderateurs ajouter <joueur>` et `/moderateurs retirer <joueur>` : gérer l'accès de modération (opérateurs).
- `/mod expulser <joueur> <raison>`
- `/mod silence <joueur> <minutes>` ; `0` lève le silence.
- `/mod avertir <joueur> <message>`
- `/mod aller <joueur>`
- `/mod signalements`

Les actions de modération sont inscrites dans le journal serveur avec le préfixe `[MOD]`. Le silence n'est pas une sanction persistante et doit être renouvelé après un redémarrage si nécessaire.

## Modèle prévu pour la suite

Le but est d'éviter de donner l'opérateur à une personne qui a seulement besoin de gérer les joueurs ou les événements. La conception à venir devrait prévoir :

1. **Permissions par capacité**, avec des noms stables : `chat.mute`, `chat.kick`, `reports.read`, `npc.edit`, `events.manage`, `economy.grant`, `world.teleport`.
2. **Rôles composables** (modération, construction, animation, support) auxquels les capacités sont associées. Le maire et l'adjoint restent des rôles municipaux distincts des rôles d'équipe.
3. **Attribution durable par UUID**, avec commande pour consulter, accorder et révoquer, ainsi qu'une durée optionnelle pour les accès temporaires.
4. **Principe du moindre accès** : refus par défaut, aucune capacité héritée implicitement de l'opérateur vers les joueurs; les opérations économiques et destructrices demandent une permission dédiée.
5. **Journal d'audit** pour chaque changement d'accès et action sensible : auteur, cible, capacité, heure, résultat.
6. **Reprise sûre** : les opérateurs de la console peuvent toujours restaurer les accès; un fichier invalide ne doit pas accorder de droits.
7. **Tests reproductibles** : joueur ordinaire, modérateur, maire, adjoint, opérateur et console; vérifier les commandes visibles et les refus côté serveur.

Ces éléments sont une cible de conception, pas des commandes actuellement disponibles. Le détail devra être adapté aux commandes et aux usages de l'équipe avant l'implémentation.

## Références

- [Commandes et touches](commandes.md)
- [Bien démarrer dans le monde](entree-en-jeu.md)
- [Guide Falix](../deploy/FALIX.md) — procédure d'exploitation serveur
