import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

public class Setup {
    /**
     * Lit les données de vente depuis le fichier JSON.
     * @param args arguments de la ligne de commande
     * @throws IOException si le fichier ne peut pas être lu
     */
    public static void main(String[] args) throws IOException {
        // 1. Je lis le fichier eSales.json et je le parse en un tableau d'objets JSON
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("eSales.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        String json = jsonBuilder.toString();
        JSONArray jsonArray = new JSONArray(json);

        // 2. Je crée les collections produits, clients et ventes dans la base de données MongoDB
        String uri = "mongodb://localhost:27017";
        try (MongoClient mongoClient = MongoClients.create(uri)) {
            MongoDatabase database = mongoClient.getDatabase("projet");

            // Je commence par clear les 3 collections si elles existent déjà
            database.getCollection("produits").drop();
            database.getCollection("clients").drop();
            database.getCollection("ventes").drop();

            // Je commence par produits
            database.createCollection("produits");

            // Je vais itérer sur chaque docs de eSales.json, trouver les produits uniques en les mettant dans un set, puis je vais les insérer dans la collection produits
            Set<String> produitsUniques = new HashSet<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);

                if(!item.has("Product ID")) continue; // le dernier doc de eSales.json est mal formé

                String productId = item.getString("Product ID");

                if(!produitsUniques.contains(productId)) {
                    produitsUniques.add(productId);

                    // Ajoute le produit dans la collection
                    // Pour retrouver le prix je vais diviser le prix total par la quantité
                    double price = item.getDouble("Sales") / item.getInt("Quantity");
                    Document produit = new Document("Product ID", productId)
                            .append("Category", item.getString("Category"))
                            .append("Sub-Category", item.getString("Sub-Category"))
                            .append("Product Name", item.getString("Product Name"))
                            .append("Quantity", 1000)
                            .append("Price", price);
                    database.getCollection("produits").insertOne(produit);
                }
            }

            // Je continue avec client
            database.createCollection("clients");

            // Je vais itérer sur chaque docs de eSales.json, trouver les clients uniques en les mettant dans un set, puis je vais les insérer dans la collection clients
            Set<String> clientsUniques = new HashSet<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);

                if(!item.has("Customer ID")) continue; // le dernier doc de eSales.json est mal formé

                String customerId = item.getString("Customer ID");

                if(!clientsUniques.contains(customerId)) {
                    clientsUniques.add(customerId);

                    // Ajoute le client dans la collection
                    Document client = new Document("Customer ID", customerId)
                            .append("Customer Name", item.getString("Customer Name"))
                            .append("Segment", item.getString("Segment"));
                    database.getCollection("clients").insertOne(client);
                }
            }

            // Je continue avec ventes
            database.createCollection("ventes");

            // Je vais itérer sur chaque docs de eSales.json, regrouper les produits par commande dans une map, puis je vais les insérer dans la collection ventes
            Map<List<String>, Document> ventesUniques = new HashMap<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);

                if(!item.has("Order ID")) continue; // le dernier doc de eSales.json est mal formé

                String orderId = item.getString("Order ID");
                String customerId = item.getString("Customer ID");
                String orderDate = item.getString("Order Date");
                // Un même Order ID peut être utilisé pour des clients et des dates différents
                List<String> commande = List.of(orderId, customerId, orderDate);

                // Créer la commande si elle n'existe pas encore
                if(!ventesUniques.containsKey(commande)) {
                    Document vente = new Document("Order ID", orderId)
                            .append("Customer ID", customerId)
                            .append("Order Date", orderDate)
                            .append("City", item.getString("City"))
                            .append("Ship Date", item.getString("Ship Date"))
                            .append("Ship Mode", item.getString("Ship Mode"))
                            .append("items", new ArrayList<Document>());
                    ventesUniques.put(commande, vente);
                }

                // Ajoute le produit dans l'array items de la commande
                Document produitVendu = new Document("Row ID", item.getString("Row ID"))
                        .append("Product ID", item.getString("Product ID"))
                        .append("Quantity", item.getInt("Quantity"))
                        .append("Sales", item.getDouble("Sales"));
                ventesUniques.get(commande).getList("items", Document.class).add(produitVendu);
            }

            // Ajoute les ventes dans la collection
            for (Document vente : ventesUniques.values()) {
                database.getCollection("ventes").insertOne(vente);
            }

            // Je vais ajouter ici les index pour accélérer les recherches et éviter les doublons

            // déjà un index sur Product ID et Customer ID pour éviter les doublons
            database.getCollection("produits").createIndex(new Document("Product ID", 1), new IndexOptions().unique(true));
            database.getCollection("clients").createIndex(new Document("Customer ID", 1), new IndexOptions().unique(true));

            // Accélère la recherche des produits en rupture de stock
            database.getCollection("produits").createIndex(new Document("Quantity", 1));
        }
    }
}
