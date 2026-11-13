# Sources de TERRA EXODUS

## Documents de conception fournis

1. Proposition transmise dans la conversation : analyse des systèmes, scénario
   « TERRA EXODUS — Les Vestiges d'Orion », cinq actes et boucle de quêtes.
2. **TerraCraft — Quêtes et Progression du Scénario (TERRA EXODUS).docx**, fourni
   depuis le dossier Téléchargements du porteur du projet. Son texte et ses six
   tableaux ont été lus ; le fichier original est conservé sans modification et
   n'est pas recopié dans Git.

Empreinte SHA-256 du DOCX analysé :

```text
89615cd8570b88297c9b93aa09b3482f5e71aaf85bb7946d2688cebbaa9cee06
```

Les tableaux 1 à 5 décrivent les 19 quêtes. Le tableau 6 propose des hooks.
Le document décrit aussi six personnages, des exemples de prompts, des récompenses,
un schéma de sauvegarde et des commandes futures. Ces propositions ont été
confrontées aux sources du dépôt ; leur formulation comme « officiel » dans le
DOCX ne signifie pas qu'elles sont déjà chargées par le mod.

## Référence technique

Sources de `propann/TerraCraft`, branche `main`, commit
`6736c356c39f2be6e72b832c5ec756789174128c`, version `0.39.0`.
Les références de fichiers figurent dans l'[analyse technique](analyse-terra-exodus.md).
La PR #5 fournit les PNJ et l’adaptateur IA. La PR #7 les étend avec les cinq chapitres, les quêtes autorisées et leurs preuves.

## Arbitrages de cette synthèse

- Conserver les cinq actes, les 19 ID et les six personnages du DOCX.
- Corriger le namespace technique en `terracraft_geo` pour les éléments existants.
- Garder les artefacts lunaires avant Mars et réserver le Noyau d'Orion au final.
- Déplacer le titane hors des prérequis du premier vol et résoudre la dépendance
  entre fondation de ville et raid nocturne.
- Conserver les sanctuaires lunaires ; spécifier les ruines martiennes et le relais
  orbital comme des extensions.
- Définir le final comme une restauration régionale autorisée, avec une persistance
  et des effets limités de suppression de la Contamination, sans réécriture du monde.
- Réserver l'origine exacte de la Contamination aux révélations écrites du canon.
  Le mot « virus » apparaît dans un profil du DOCX ; il ne décrit pas une mécanique
  biologique établie par le code ni un fait que le modèle doit inventer.

Cette synthèse initiale est reprise dans la PR #7, qui implémente le moteur, l’accueil et le livre de départ. Les seuils et écarts effectivement livrés sont détaillés dans le catalogue et le guide ci-dessous.

## Intégration

La PR #7 reprend ces sources et conserve les 19 ID. Le [catalogue des quêtes](quetes-terra-exodus.md) décrit les seuils effectivement codés et les arbitrages ; le [guide de jeu](../terra-exodus-jeu.md) décrit les commandes, l’exploitation et les limites de validation.
