import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class App {
    /**
     * Affiche le menu et exécute le choix de l'utilisateur.
     */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("=== EPOSSIBLE ===");
            System.out.println("1. Enregistrer une vente");
            System.out.println("2. Afficher les produits en rupture de stock");
            System.out.println("3. Afficher les produits les plus vendus");
            System.out.println("4. Calculer le chiffre d'affaires sur une période");
            System.out.print("Votre choix : ");
            int choix = scanner.nextInt();
            scanner.nextLine();

            switch (choix) {
                case 1:
                    enregistrerVente(scanner);
                    break;
                case 2:
                    afficherProduitsEnRupture();
                    break;
                case 3:
                    afficherProduitsLesPlusVendus(scanner);
                    break;
                case 4:
                    calculerChiffreAffaires(scanner);
                    break;
                default:
                    System.out.println("Choix invalide.");
                    break;
            }

            // attend 3 sec et re-appel main
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            main(args);
        }
    }

    /**
     * Affiche les produits dont le stock disponible est 0
     */
    public static void afficherProduitsEnRupture() {
        try (MongoClient mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");

            System.out.println("=== Produits en rupture de stock ===");

            // j'init un bool pour savoir si j'ai trouvé au moins un produit en rupture de stock
            boolean produitTrouve = false;

            try (var cursor = database.getCollection("produits").find(new Document("Quantity", 0)).iterator()) {
                while(cursor.hasNext()) {
                    Document produit = cursor.next();
                    // recupere le nom du produit et l'affiche
                    System.out.println(produit.getString("Product ID") + " - " + produit.getString("Product Name"));
                    produitTrouve = true;
                }
            }
            if(!produitTrouve) {
                System.out.println("Aucun produit en rupture de stock.");
            }
        }
    }

    /**
     * Affiche les produits les plus vendus
     */
    public static void afficherProduitsLesPlusVendus(Scanner scanner) {
        System.out.print("Combien de produits voulez-vous afficher ? ");
        int nombreProduits;
        
        // guard simple
        try {
            nombreProduits = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Nombre invalide.");
            return;
        }
        if(nombreProduits <= 0) {
            System.out.println("Nombre invalide.");
            return;
        }

        try (MongoClient mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");

            Map<String, String> nomsProduits = new HashMap<>();
            try (var cursor = database.getCollection("produits").find().iterator()) {
                while(cursor.hasNext()) {
                    Document produit = cursor.next();
                    nomsProduits.put(produit.getString("Product ID"), produit.getString("Product Name"));
                }
            }

            // pour chaque produit on va recup sa somme des quantités vendues
            var classement = database.getCollection("ventes").aggregate(List.of(
                // je split les items dans la commande
                    new Document("$unwind", "$items"),
                    // je groupe par id
                    new Document("$group", new Document("_id", "$items.Product ID")
                    // somme des quantités de chaque commande pour chaque produit
                            .append("quantiteVendue", new Document("$sum", "$items.Quantity"))),
                    new Document("$sort", new Document("quantiteVendue", -1).append("_id", 1)),
                    new Document("$limit", nombreProduits)));

            System.out.println("=== Produits les plus vendus ===");
            int rang = 1;
            try (var cursor = classement.iterator()) {
                while(cursor.hasNext()) {
                    Document resultat = cursor.next();

                    // affiche le produit avec son nom et sa quantité vendue
                    String nom = nomsProduits.get(resultat.getString("_id"));
                    System.out.println(rang + ". " + resultat.getString("_id") + " - " + nom
                            + " : " + resultat.get("quantiteVendue") + " vendues");
                    rang++;
                }
            }
            if(rang == 1) {
                System.out.println("Aucun produit vendu.");
            }
        }
    }

    /**
     * Calcule le chiffre d'affaires entre deux dates
     */
    public static void calculerChiffreAffaires(Scanner scanner) {
        // set le format de date pour le parse
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

        LocalDate debut;
        LocalDate fin;

        try {
            System.out.print("Date de début (merci de respecter le format jj-MM-aaaa) : ");
            debut = LocalDate.parse(scanner.nextLine().trim(), format);
            System.out.print("Date de fin (merci de respecter le format jj-MM-aaaa) : ");
            fin = LocalDate.parse(scanner.nextLine().trim(), format);
        } catch (DateTimeParseException e) {
            System.out.println("Format non respecté.");
            return;
        }

        if(debut.isAfter(fin)) {
            System.out.println("Erreur : date de début après date de fin.");
            return;
        }

        // Calcul le CA
        BigDecimal chiffreAffaires = BigDecimal.ZERO;
        try (MongoClient mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");

            try (var cursor = database.getCollection("ventes").find().iterator()) {
                while(cursor.hasNext()) {
                    Document vente = cursor.next();
                    // parse la date de la vente
                    LocalDate date = LocalDate.parse(vente.getString("Order Date"), format);

                    // regarde si elle est incluse dans la range qu'on veut regarder
                    if(!date.isBefore(debut) && !date.isAfter(fin)) {
                        for (Document item : vente.getList("items", Document.class)) {
                            // ajoute le sales de chaque item à la somme du CA
                            chiffreAffaires = chiffreAffaires.add(new BigDecimal(item.get("Sales").toString()));
                        }
                    }
                }
            }
        }

        // print le CA =
        System.out.println("Chiffre d'affaires : " + chiffreAffaires);
    }

    /**
     * Prépare et insère une vente dans la base de données.
     * Diminue le stock aussi.
     * @param scanner lecteur des saisies de l'utilisateur
     */
    public static void enregistrerVente(Scanner scanner) {
        // On doit demander déjà le client pour lequel on veut ajouter une vente
        System.out.print("Écrivez le Customer ID du client pour lequel vous voulez enregistrer une vente (ex: RH-19495):");
        String id = scanner.nextLine();

        // Cherche si le client existe
        try (MongoClient mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");
            MongoCollection<Document> clients = database.getCollection("clients");

            Document client = clients.find(new Document("Customer ID", id)).first();
            if (client == null) {
                System.out.println("Le client n'existe pas.");
                return;
            }

            // Demande les produits et les quantités à ajouter, jusqu'à ce qu'on écrit une ligne vide pour terminer
            int produitActuel = 1;
            ArrayList<Document> items = new ArrayList<>();
            MongoCollection<Document> produits = database.getCollection("produits");

            while(true) {
                System.out.println("Entrez le Product ID du produit numéro " + produitActuel + " de la commande que vous voulez enregistrer (ou écrivez une ligne vide pour terminer) :");
                String productId = scanner.nextLine().trim();
                if(productId.isEmpty()) break;

                // Trouve le produit
                Document produit = produits.find(new Document("Product ID", productId)).first();
                if (produit == null) {
                    System.out.println("Le produit n'existe pas.");
                    continue;
                }

                // Demande la quantité
                System.out.println("Entrez la quantité du produit " + produit.getString("Product Name") + " :");
                int quantite;
                try {
                    quantite = Integer.parseInt(scanner.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("La quantité doit être un entier positif.");
                    continue;
                }
                if(quantite <= 0) {
                    System.out.println("La quantité doit être un entier positif.");
                    continue;
                }

                // Prend aussi en compte les quantités déjà ajoutées pour ce produit
                long quantiteDejaAjoutee = 0;
                for (Document item : items) {
                    if(productId.equals(item.getString("Product ID"))) {
                        quantiteDejaAjoutee += item.getInteger("Quantity");
                    }
                }
                // verifie le stock
                if(quantite + quantiteDejaAjoutee > produit.getInteger("Quantity")) {
                    System.out.println("Le stock disponible est insuffisant.");
                    continue;
                }

                // Ajoute le produit dans l'array items de la commande
                double sales = produit.getDouble("Price") * quantite;
                Document produitVendu = new Document("Row ID", UUID.randomUUID().toString())
                        .append("Product ID", productId)
                        .append("Quantity", quantite)
                        .append("Sales", sales);
                items.add(produitVendu);
                produitActuel++;
            }

            if(items.isEmpty()) {
                System.out.println("Aucun produit ajouté, vente annulée.");
                return;
            }

            // Demande les informations de livraison
            System.out.print("Ville de livraison : ");
            String city = scanner.nextLine();
            System.out.print("Date d'expédition (au format jj-MM-aaaa) : ");
            String shipDate = scanner.nextLine();
            System.out.print("Mode d'expédition (Standard Class, Second Class, Same Day) : ");
            String shipMode = scanner.nextLine();

            // Prépare le document de la vente avant son ajout dans la collection
            Document vente = new Document("Order ID", UUID.randomUUID().toString())
                    .append("Customer ID", id)
                    .append("Order Date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
                    .append("City", city)
                    .append("Ship Date", shipDate)
                    .append("Ship Mode", shipMode)
                    .append("items", items);

            // Diminue le stock seulement si la quantité est encore disponible car comme deux personnes peuvent potentiellemen utliser l'app en même temps eh bien la première verif
            // qui se fait pendant la saisie peut ne plus être à jour au moment de l'enregistrement de la vente. 
            ArrayList<Document> produitsDeduits = new ArrayList<>();
            try {
                // Pour chaque produit de la vente, on va essayer de diminuer le stock
                for (Document item : items) {
                    int quantite = item.getInteger("Quantity");

                    // J'utilise updateOne avec un filtre pour s'assurer que la quantité est suffisante avant de la diminuer
                    var resultat = produits.updateOne(
                            new Document("Product ID", item.getString("Product ID"))
                                    .append("Quantity", new Document("$gte", quantite)),
                            new Document("$inc", new Document("Quantity", -quantite))); // enlève la quantité qu'on prend

                    if(resultat.getModifiedCount() == 0) {
                        throw new IllegalStateException("Stock insuffisant pour le produit " + item.getString("Product ID"));
                    }
                    produitsDeduits.add(item);
                }

                database.getCollection("ventes").insertOne(vente); // ajoute la vente car tout les produits étaient encore dispo
            } catch (RuntimeException e) {
                // Revert tout ce qui a été fait si une exception est levée
                for (Document item : produitsDeduits) {
                    produits.updateOne(new Document("Product ID", item.getString("Product ID")),
                            // $inc pour ajouter la quantité qu'on a enlevé
                            new Document("$inc", new Document("Quantity", item.getInteger("Quantity"))));
                }
                System.out.println("Vente non enregistrée : " + e.getMessage());
                return;
            }
            System.out.println("Vente enregistrée : " + vente.toJson());
        }
    }
}
