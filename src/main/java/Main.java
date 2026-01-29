import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Main {
    public static void main(String[] args) {
        DataRetriever dataRetriever = new DataRetriever();

        try {
            System.out.println("=== Test 1: Création d'une commande ===");
            Dish salade = dataRetriever.findDishById(1);

            if (salade == null) {
                System.out.println("ERREUR: Plat introuvable avec ID 1");
                return;
            }

            System.out.println("Plat trouvé: " + salade.getName());
            System.out.println("Ingrédients: " + (salade.getDishIngredients() != null ? salade.getDishIngredients().size() : 0));

            DishOrder ligne1 = new DishOrder();
            ligne1.setDish(salade);
            ligne1.setQuantity(2);

            // Générer une référence au format ORDXXXXX (5 chiffres)
            String reference = generateOrderReference();

            Order nouvelleCommande = new Order();
            nouvelleCommande.setReference(reference);  // Utiliser la référence générée
            nouvelleCommande.setCreationDatetime(Instant.now());
            nouvelleCommande.setOrderType(OrderTypeEnum.EAT_IN);
            nouvelleCommande.setStatus(OrderStatusEnum.CREATED);
            nouvelleCommande.setDishOrderList(new ArrayList<>());
            nouvelleCommande.getDishOrderList().add(ligne1);

            Order commandeSauvegardee = dataRetriever.saveOrder(nouvelleCommande);
            System.out.println("Commande créée avec référence: " + commandeSauvegardee.getReference());

            System.out.println("\n=== Test 2: Changement de statut à DELIVERED ===");
            commandeSauvegardee.setStatus(OrderStatusEnum.DELIVERED);
            dataRetriever.saveOrder(commandeSauvegardee);
            System.out.println("Statut changé à DELIVERED avec succès");

            System.out.println("\n=== Test 3: Tentative de modification d'une commande DELIVERED ===");
            commandeSauvegardee.setOrderType(OrderTypeEnum.TAKE_AWAY);
            try {
                dataRetriever.saveOrder(commandeSauvegardee);
                System.out.println("ERREUR: L'exception n'a pas été levée!");
            } catch (RuntimeException e) {
                System.out.println("SUCCÈS: Exception correctement levée: " + e.getMessage());
            }

            System.out.println("\n=== Test 4: Recherche par référence ===");
            Order commandeRetrouvee = dataRetriever.findOrderByReference(commandeSauvegardee.getReference());
            System.out.println("Commande retrouvée: " + commandeRetrouvee);

        } catch (SQLException e) {
            System.err.println("Erreur SQL: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Méthode pour générer une référence au format ORDXXXXX
    private static String generateOrderReference() {
        // Générer un nombre entre 0 et 99999 (5 chiffres maximum)
        int randomNum = ThreadLocalRandom.current().nextInt(0, 100000);

        // Formater avec des zéros devant pour avoir toujours 5 chiffres
        return String.format("ORD%05d", randomNum);
    }
}