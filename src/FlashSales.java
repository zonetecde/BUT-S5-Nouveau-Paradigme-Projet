import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bson.Document;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReturnDocument;

public class FlashSales {
    /**
     * Initialise 500 pièces pour chacun des dix produits les plus vendus.
     * @param database db
     */
    public static void initialiser(MongoDatabase database) {
        // trouve les 10 produits les plus vendus
        var classement = database.getCollection("ventes").aggregate(List.of(
                new Document("$unwind", "$items"),
                // je groupe par Product ID et je fais la somme des quantités vendues
                new Document("$group", new Document("_id", "$items.Product ID")
                        .append("quantiteVendue", new Document("$sum", "$items.Quantity"))),
                // sort par 1) nbre de quantité vendue décroissant, 2) Product ID croissant si égalité
                new Document("$sort", new Document("quantiteVendue", -1).append("_id", 1)),
                // que les 10 premiers
                new Document("$limit", 10)));

        ArrayList<Document> pieces = new ArrayList<>();

        try (var cursor = classement.iterator()) {
            while(cursor.hasNext()) {
                String productId = cursor.next().getString("_id");
                Document produit = database.getCollection("produits").find(new Document("Product ID", productId)).first();
                
                // créé 500 pièces pour ce produit
                for (int numero = 0; numero < 500; numero++) {
                    pieces.add(new Document("_id", productId + ":" + numero)
                            .append("Product ID", productId)
                            .append("Product Name", produit.getString("Product Name"))
                            .append("Price", produit.getDouble("Price"))
                            .append("Piece", numero)
                            .append("Etat", "disponible"));
                }
            }
        }

        // drop l'ancienne collection si elle existe et créé la nouvelle
        database.getCollection("ventesFlash").drop();
        database.createCollection("ventesFlash");
        var collection = database.getCollection("ventesFlash");
        collection.insertMany(pieces);

        // ajoute les 2 index pour accélérer les recherches et éviter les doublons, j'ai expliqué dans le readme pourquoi j'ai choisi ces index
        collection.createIndex(new Document("Product ID", 1).append("Piece", 1), new IndexOptions().unique(true));
        collection.createIndex(new Document("Panier ID", 1).append("Customer ID", 1).append("Etat", 1));
    }

    /**
     * Réserve une pièce disponible ou dont la réservation a expiré.
     * @param database db
     * @param customerId identifiant du client
     * @param productId identifiant du produit à réserver
     * @param panierId identifiant du panier auquel ajouter la pièce
     * @return pièce réservée, ou null si aucune pièce n'est disponible
     */
    public static Document reserverPiece(MongoDatabase database, String customerId, String productId, String panierId) {
        // je prends un int aléatoire pour ne pas toujours prendre les mm pièces comme il est dit
        // que il y aura plein de clients qui vont réserver en même temps
        int debut = ThreadLocalRandom.current().nextInt(500);

        Document filtre = new Document("Product ID", productId)
                .append("Piece", new Document("$gte", debut))
                // je prends seulement les pièce qui sont soit dispo, soit réservées mais dont la réservation a expiré (voir readme pourquoi)
                .append("$or", List.of(
                        new Document("Etat", "disponible"),
                        new Document("Etat", "reserve").append("$expr",
                                new Document("$lte", List.of("$ExpireLe", "$$NOW")))));

        // je mets à jour la pièce pour la réserver, je mets un expire de 10 minutes
        List<Document> miseAJour = List.of(new Document("$set", new Document("Etat", "reserve")
                .append("Customer ID", new Document("$literal", customerId))
                .append("Panier ID", new Document("$literal", panierId))
                // $$NOW = date actuelle
                .append("ExpireLe", new Document("$add", List.of("$$NOW", 10 * 60 * 1000)))));

        // je prends la première pièce disponible ou expirée, je la mets à jour et je la retourne grâce à returnDocument
        var options = new FindOneAndUpdateOptions().sort(new Document("Piece", 1)).returnDocument(ReturnDocument.AFTER);
        var collection = database.getCollection("ventesFlash");
        Document piece = collection.findOneAndUpdate(filtre, miseAJour, options);

        // si aucune pièce n'a été trouvée entre debut et 499, je cherche entre 0 et debut - 1 (bon c'est pas la meilleure solution mais ça fonctionne)
        if(piece == null && debut > 0) {
            filtre.put("Piece", new Document("$lt", debut));
            piece = collection.findOneAndUpdate(filtre, miseAJour, options);
        }
        return piece;
    }

    /**
     * Marque comme vendues les pièces du panier dont la réservation est encore valide.
     * @param database db
     * @param customerId identifiant du propriétaire du panier
     * @param panierId identifiant du panier à valider
     * @return nombre de pièces validées
     */
    public static long validerPanier(MongoDatabase database, String customerId, String panierId) {
        Document filtre = new Document("Panier ID", panierId)
                .append("Customer ID", customerId)
                .append("Etat", "reserve")
                .append("$expr", new Document("$gt", List.of("$ExpireLe", "$$NOW")));
        Document miseAJour = new Document("$set", new Document("Etat", "vendu"))
                .append("$unset", new Document("ExpireLe", ""))
                .append("$currentDate", new Document("ValideLe", true));

        // getModifiedCount va pas inclure les produits expirés
        return database.getCollection("ventesFlash").updateMany(filtre, miseAJour).getModifiedCount();
    }

    /**
     * Affiche le catalogue et réserve les produits saisis dans un nouveau panier.
     * @param scanner lecteur des identifiants du client et des produits
     */
    public static void reserver(Scanner scanner) {
        System.out.print("Customer ID : ");
        String customerId = scanner.nextLine().trim();
        try (var mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");
            if(database.getCollection("clients").find(new Document("Customer ID", customerId)).first() == null) {
                System.out.println("Le client n'existe pas.");
                return;
            }

            System.out.println("=== Catalogue des ventes flash ===");
            var catalogue = database.getCollection("ventesFlash").aggregate(List.of(
                    new Document("$group", new Document("_id", "$Product ID")
                            .append("nom", new Document("$first", "$Product Name"))
                            .append("prix", new Document("$first", "$Price"))),
                    new Document("$sort", new Document("_id", 1))));
            try (var cursor = catalogue.iterator()) {
                if(!cursor.hasNext()) {
                    System.out.println("Aucune vente flash initialisée.");
                    return;
                }
                while(cursor.hasNext()) {
                    Document produit = cursor.next();
                    System.out.println(produit.getString("_id") + " - " + produit.getString("nom") + " : " + produit.get("prix"));
                }
            }

            String panierId = UUID.randomUUID().toString();
            System.out.println("Panier ID à conserver pour la validation : " + panierId);
            while(true) {
                System.out.print("Product ID à réserver (une pièce, ligne vide pour terminer) : ");
                String productId = scanner.nextLine().trim();
                if(productId.isEmpty()) return;

                Document piece = reserverPiece(database, customerId, productId, panierId);
                if(piece == null) {
                    System.out.println("Produit absent des ventes flash ou stock indisponible.");
                } else {
                    System.out.println("Pièce réservée jusqu'au " + piece.getDate("ExpireLe"));
                }
            }
        }
    }

    /**
     * Demande le panier à valider et affiche le nombre de pièces confirmées.
     * @param scanner lecteur de l'identifiant du client et du panier
     */
    public static void valider(Scanner scanner) {
        System.out.print("Customer ID : ");
        String customerId = scanner.nextLine().trim();
        System.out.print("Panier ID : ");
        String panierId = scanner.nextLine().trim();
        try (var mongoClient = MongoClients.create()) {
            MongoDatabase database = mongoClient.getDatabase("projet");
            long nombre = validerPanier(database, customerId, panierId);
            if(nombre == 0) {
                System.out.println("Aucune réservation valide pour ce client et ce panier (expirée, absente ou déjà validée).");
            } else {
                System.out.println(nombre + " pièce(s) validée(s). Les réservations expirées ne sont pas confirmées.");
            }
        }
    }
}
