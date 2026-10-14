Partie I :
Une plateforme de commerce électronique EPOSSIBLE utilise une base de données MongoDB et y stocke toutes les informations relatives à ses ventes, à ses produits et à ses clients (voir le fichier eSales.json). EPOSSIBLE veut procéder à des modifications sur deux aspects : données et applicatif.

Données : EPOSSIBLE veut restructurer sa base de données et passer d’une collection à trois collections : ventes, produits et clients.

1.  Créer et remplir les collections produits et clients à partir des documents de eSales.json. De surcroit, un document de produits doit contenir le champ « Price » pour représenter le prix unitaire et le champ « Quantity » pour représenter la quantité disponible du produit. Initialement, il y a 1000 pièces disponibles pour chaque produit.
2.  Créer une nouvelle collection ventes qui aura la même structure que eSales.json, à l’exception des champs (qu’il faut supprimer) : « Discount », « Profit », « Shipping » et « Order Priority ». De plus, elle fera juste référence aux produits et clients.

Application : EPOSSIBLE veut écrire une application Java permettant de manipuler la nouvelle base de données via l’API pour accéder à MongoDB. L’application doit implémenter les fonctionnalités suivantes :

1.  Établir une vente d’une liste de produits pour un client donné.
2.  Afficher tous les produits en rupture de stock.
3.  Afficher la liste des produits les plus vendus.
4.  Calculer le chiffre d’affaires sur une période donnée.

Partie II :
EPOSSIBLE veut créer une nouvelle collection pour organiser des ventes flash avec un catalogue de produits et un système de panier. Cette vente s'appliquera aux 10 premiers produits vendus, avec 500 pièces disponibles pour chaque produit. Un produit ajouté au panier est réservé pendant 10 minutes. Si le panier expire, le produit est remis en stock. Si l’utilisateur valide, le stock est définitivement déduit. EPOSSIBLE veut cibler jusqu’à 10 000 000 d’utilisateurs inscrits. Sur la base des expériences précédentes, plus de 100 000 requêtes d’ajout au panier par seconde pourraient frapper la base de données au même instant. Il convient de noter que MongoDB verrouille un document modifié mais laisse les autres documents de la collection libres.

1.  Créer une collection pour des ventes flash qui prend en considération les contraintes évoquées précédemment. Ne pas oublier de la remplir avec les 10 premiers produits vendus.
2.  Ajouter des fonctionnalités de réservation et de validation à l'application Java.

Travail à rendre :
Le projet est à réaliser en monôme. Le projet sera évalué sur la base des critères suivants :
• La qualité de conception des structures de documents NoSQL.
• Fonctionnalités codées et opérationnelles.
• Clarté et qualité du code (ajout des commentaires).
• Usage des indexes pour améliorer les performances des requêtes.

Il faut rendre les éléments suivants :

1.  Un fichier readme contenant la structure de tous les documents conçus pour les parties I et II, ainsi que toute information utile pour expliquer le fonctionnement du projet.
2.  Scripts pour créer la BD, ses collections ainsi que les données à partir des fichiers json. Il faudra éventuellement donner les scripts pour créer les indexes.
3.  Les fichiers json qui contiennent toutes les données.
4.  Le(s) programmes(s) java bien commenté(s) avec des exemples d’utilisation pour les fonctionnalités décrites au-dessus. L’utilisation d’un menu textuel est recommandée.

Mettre tous les fichiers dans un répertoire portant votre nom, et compresser ce répertoire au format ZIP dont le nom de fichier doit suivre le format suivant : NomEtudiant.zip.

Note importante : toute tentative de plagiat sera sévèrement sanctionnée.
