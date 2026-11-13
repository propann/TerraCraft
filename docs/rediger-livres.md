# Écrire et prévisualiser un livre TerraCraft

Les livres de campagne sont assemblés par le mod à partir de la progression du joueur : objectifs, textes déjà révélés et contacts disponibles peuvent varier. Le petit outil ci-dessous sert à préparer ou relire un brouillon avant intégration. Il ne remplace pas l'assemblage dynamique du journal.

## Préparer un brouillon

Crée un fichier texte UTF-8. Une ligne contenant uniquement `---` sépare deux sections. Les paragraphes sont renvoyés à la ligne selon la même approximation que le mod : 18 caractères par ligne, 13 lignes par page, 100 pages maximum.

```bash
python3 tools/preview_book.py mon-livre.txt
```

Pour récupérer un tableau JSON des pages :

```bash
python3 tools/preview_book.py mon-livre.txt --json
```

La largeur réelle varie selon les glyphes Minecraft : vérifie toujours les livres dans le jeu, surtout les accents, guillemets, symboles et mots longs. Ne mets pas de clé, donnée de joueur ou contenu privé dans les brouillons versionnés.

## Intégration en jeu

Le journal TERRA EXODUS est créé dans `ExodusCampaign.giveBook` et paginé par `BookPages`. Les contenus variables doivent rester liés aux quêtes et preuves côté serveur ; un texte de livre ne doit jamais valider une quête ni accorder une récompense. Après intégration, vérifier le livre avec plusieurs niveaux de progression et l'actualisation `/exodus livre`.

Les livres statiques et les guides de joueur doivent utiliser le même vocabulaire que `/aide` et [la référence des commandes](commandes.md).