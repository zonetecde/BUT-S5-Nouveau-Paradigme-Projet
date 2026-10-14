import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
            System.out.print("Votre choix : ");
            int choix = scanner.nextInt();
            scanner.nextLine();

            switch (choix) {
                case 1:
                    enregistrerVente(scanner);
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
     * Prépare et insère une vente dans la base de données.
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

            // Ajoute la vente dans la collection
            database.getCollection("ventes").insertOne(vente);
            System.out.println("Vente enregistrée : " + vente.toJson());
        }
    }
}
