# Projet BDD

Rayane STASZEWSKI - RA-IL 2

## Partie I

J'ai commencé par créer les collections `produits` et `clients` à partir du fichier `eSales.json`.

Un document de eSales.json est structuré de la manière suivante :

```json
{
    "Row ID": "32298",
    "Order ID": "CA-2012-124891",
    "Order Date": "31-07-2012",
    "Ship Date": "31-07-2012",
    "Ship Mode": "Same Day",
    "Customer ID": "RH-19495",
    "Customer Name": "Rick Hansen",
    "Segment": "Consumer",
    "City": "New York City",
    "State": "New York",
    "Country": "United States",
    "Postal Code": "10024",
    "Market": "US",
    "Region": "East",
    "Product ID": "TEC-AC-10003033",
    "Category": "Technology",
    "Sub-Category": "Accessories",
    "Product Name": "Plantronics CS510 - Over-the-Head monaural Wireless Headset System",
    "Sales": "2309.65",
    "Quantity": "7",
    "Discount": "0",
    "Profit": "762.1845",
    "Shipping Cost": "933.57",
    "Order Priority": "Critical"
}
```

Pour en soustraire les deux collections `produits` et `clients`, j'ai pris chaque attribut spécifique au client et chaque attribut spécifique au produit et les ai mis dans deux collections.

Voici à quoi les deux nouvelles collections doivent ressembler en se basant sur le document de `eSales.json` que j'ai copié en haut :

Pour la collection `produits` :

```json
{
    "Product ID": "TEC-AC-10003033", // id de la ligne
    "Category": "Technology",
    "Sub-Category": "Accessories",
    "Product Name": "Plantronics CS510 - Over-the-Head monaural Wireless Headset System",
    "Quantity": "1000", // tout les produits ont une quantité de 1000 d'après l'énoncé
    "Price": ...
}
```

Et pour la collection `clients` :

```json
{
    "Customer ID": "RH-19495", // id de la ligne
    "Customer Name": "Rick Hansen",
    "Segment": "Consumer"
}
```

Note: J'ai vérifié que pour chaque client unique des docuemnts dans `eSales.json`, s'ils avaient toujours la même adresse. J'ai remarqué que 1580 clients changent d'adresse entre deux commandes, donc c'est pour ça que je mets l'adresse dans la collection `ventes` et non dans la collection `clients`.

Enfin, j'ai créé la collection `ventes` qui comme le précise le sujet possède la même structure que le fichier `eSales.json` mais sans certains attributs. J'ai aussi fait le choix de mettre un array items pour éviter de devoir dupliquer un document pour chaque produit acheté dans une commande. J'ai aussi vérifié que ship date et ship mode possède la même valeur pour chaque document d'une même commande, donc je n'ai pas besoin de les mettre dans l'array items. J'ai fait le choix aussi de ne pas mettre d'attribut `Sales` au document de la collection `ventes` mais dans chaque "sous-document" du tableau `items`, car sinon il faudrait toujours mettre à jour la valeur de `Sales`. Du coup si on a besoin du total de la commande je ferai juste la somme de chaque `Sales` des items dans le tableau items.

Ainsi, un document de la collection `ventes` ressemble à ceci :

```json
{
    "Order ID": "CA-2012-124891",
    "Customer ID": "RH-19495",
    "Order Date": "31-07-2012",
    "City": "New York City",
    "State": "New York",
    "Country": "United States",
    "Postal Code": "10024",
    "Market": "US",
    "Region": "East",
    "Ship Date": "31-07-2012",
    "Ship Mode": "Same Day",
    "items": [
        // array de tout les produits achetés dans la commande
        {
            "Row ID": "32298",
            "Product ID": "TEC-AC-10003033",
            "Quantity": 7,
            "Sales": 2309.65 // prix unitaire * quantité
        }
    ]
}
```

Maintenant que j'ai définis la structures des collections, j'ai écrit la classe Java `Setup.java` avec un main qui va lire le fichier `eSales.json` et créer les collections `produits`, `clients` et `ventes` dans la base de données MongoDB.

Ce script utilise la librairie `org.json` pour parser le fichier JSON et la librairie `mongodb-driver-sync` pour interagir avec MongoDB.

Note: le dernier document de `eSales.json` était un document avec que `{"Row ID": ""}` du coup j'ai fais une condition spéciale dans le script pour le skip.

### Index

À la fin de l'import, `Setup.java` j'ai créé les index suivants :

- Un index unique sur `Product ID` dans la collection `produits` pour éviter les doublons.
- Un index unique sur `Customer ID` dans la collection `clients` pour éviter les doublons.
- Un index sur `Quantity` dans la collection `produits` pour rechercher les produits en rupture de stock plus rapidement.

### 1. Établir une vente d'une liste de produits pour un client donné

J'ai commencé par demander le `Customer ID` et vérifier que le client existe dans la collection `clients`. Ensuite je demande les produits et les quantités à ajouter à la commande, jusqu'à ce qu'on écrive une ligne vide pour terminer. Pour chaque produit je vérifie qu'il existe et que la quantité demandée est disponible. Si on ajoute plusieurs fois le même produit, je prends aussi en compte les quantités déjà ajoutées dans la commande. J'ai mis les produits dans un array `items`, avec un `Sales` calculé en faisant le prix du produit \* la quantité, puis je demande les informations de livraison.

J'ai aussi fait une deuxième vérification du stock au moment de le diminuer, car si deux personnes utilisent l'app en même temps le stock peut avoir changé depuis la saisie. Du coup je diminue la quantité seulement si elle est encore suffisante, puis j'ajoute la vente dans la collection `ventes` quand tous les produits ont été déduits. Si un produit n'est plus disponible ou que l'ajout de la vente échoue, je remets les quantités déjà enlevées (je fais comme un rollback).

### 2. Afficher tous les produits en rupture de stock

J'ai fait une recherche dans la collection `produits` pour récupérer tous les documents avec une `Quantity` égale à 0. Ensuite j'affiche le `Product ID` et le nom de chaque produit trouvé. J'ai aussi mis un bool pour savoir si j'ai trouvé au moins un produit, du coup si aucun produit n'est en rupture de stock j'affiche un message pour le dire.

### 3. Afficher la liste des produits les plus vendus

J'ai commencé par demander combien de produits on veut afficher. Ensuite je récupère les noms des produits dans une map pour pouvoir les afficher avec leur identifiant. Pour faire le classement, je sépare les items de chaque vente avec `$unwind`, puis je les groupe par `Product ID` et je fais la somme des quantités vendues. Du coup un produit vendu en plusieurs exemplaires compte bien pour toute sa quantité et pas juste pour une vente. Enfin je trie du plus vendu au moins vendu et je limite le résultat au nombre demandé.

### 4. Calculer le chiffre d'affaires sur une période donnée

J'ai commencé par demander une date de début et une date de fin au format `jj-MM-aaaa`. Je vérifie que les dates sont valides et que la date de début n'est pas après la date de fin. Ensuite je parcours les ventes et je parse leur `Order Date` pour regarder si elle est dans la période demandée, en incluant les deux dates. Pour chaque vente dans cette période, j'ajoute les `Sales` de chaque item à la somme du chiffre d'affaires. J'utilise un `BigDecimal` pour additionner les montants. Ensuite j'affiche le chiffre d'affaires total.

## Partie II

### 1. Créer et remplir la collection des ventes flash

J'ai interprété "les 10 premiers produits vendus" comme les 10 produits les plus vendus, en faisant la somme des quantités comme pour la question 3 de la partie I. Si deux produits ont la même quantité vendue, je les trie en secondaire par `Product ID`.

J'ai créé la collection `ventesFlash` avec 500 pièces pour chacun de ces produits. J'ai fait le choix de mettre une pièce par document, du coup il y a 5000 documents au départ. Comme MongoDB verrouille le document modifié, je n'ai pas mis tout le stock d'un produit et tous les paniers dans un seul document. Les utilisateurs peuvent donc réserver des pièces différentes sans modifier tous le même document. Je ne crée pas non plus de document pour chaque utilisateur inscrit pour éviter de surcharger la base de données, j'ai juste considéré que le panier existe seulement quand on réserve une pièce.

Voici un exemple d'une pièce disponible :

```json
{
    "_id": "OFF-AR-10003651:0",
    "Product ID": "OFF-AR-10003651",
    "Product Name": "Nom du produit dans le catalogue",
    "Price": 10.0,
    "Piece": 0,
    "Etat": "disponible"
}
```

Le script reprend les vraies valeurs depuis `produits`. J'ai mis à jour `Setup.java` pour initialiser aussi cette collection après l'import.

J'ai ajouté un index unique sur `Product ID` et `Piece` ensemble pour retrouver les pièces d'un produit et éviter les doublons. J'ai aussi ajouté un index sur `Panier ID`, `Customer ID` et `Etat` ensemble pour retrouver les réservations d'un panier plus rapidement.

### 2. Réserver et valider un panier

J'ai ajouté le choix 5 dans le menu pour réserver des produits en vente flash. Je demande le client, je vérifie qu'il existe et j'affiche le catalogue. Ensuite je crée un `Panier ID` que l'utilisateur doit conserver pour la validation (après si c'était sur un site ça aurait été sauvegardé dans le localstorage mais comme on est en ligne de commande on doit le mémoriser).

À chaque `Product ID` saisi, je réserve une pièce, donc on peut saisir plusieurs fois le même produit si on en veut plusieurs. Une ligne vide termine la saisie.

Pour réserver je fais un `findOneAndUpdate` qui trouve une pièce disponible et la réserve dans la même opération. Je commence la recherche à un numéro de pièce aléatoire pour éviter que toutes les requêtes cherchent la première pièce. Je sais pas trop si c'est la meilleure façon de faire mais ça permet déjà d'éviter que ça bloque sur la même pièce.

La pièce passe à l'état `reserve`, avec le `Customer ID`, le `Panier ID` et une date BSON `ExpireLe`. J'utilise l'horloge de MongoDB pour calculer les 10 minutes, sinon si on a des utilisateurs dans des timezones différentes ça aurait utilisé leur heure local donc j'ai tout unifié sur l'heure du "serveur".

Si la réservation expire, la pièce peut être réservée à nouveau même si son état indique encore `reserve` car la recherche regarde aussi si `ExpireLe` est dépassée. Juste elle est pas remis automatiquement en reserve = false. Du coup elle redevient disponible sans avoir besoin d'un programme qui remet les quantités en stock.

J'ai ajouté le choix 6 pour valider un panier en donnant le `Customer ID` et le `Panier ID`. Je passe les pièces encore réservées par ce client et non expirées à l'état `vendu`, puis j'enlève `ExpireLe` et j'ajoute une date BSON `ValideLe`. Quand c'est à l'état vendu la pièce ne peut plus être réservée.

Chaque pièce est réservée pour 10 minutes à partir de son ajout comme on les ajoute une par une. Si certaines pièces ont expiré quand on valide le panier, seules celles encore valides sont confirmées et le programme affiche leur nombre. Je mets pas de message à l'utilisateur pour le prévenir que certaines pièces ont expiré avant de valider son panier pour lui demander confirmation mais c'est une piste d'amélioration.
