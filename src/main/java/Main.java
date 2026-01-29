import java.util.List;

public class Main {
    public static void main(String[] args) {
        DataRetriever dataRetriever = new DataRetriever();

        Dish dish = dataRetriever.findDishById(4);
        System.out.println("Dish 4: " + dish);
        System.out.println("Dish 2: " + dataRetriever.findDishById(2));

        Dish dishToUpdate = dataRetriever.findDishById(1);
        dishToUpdate.setIngredients(List.of(
            dataRetriever.findIngredientById(1),
            dataRetriever.findIngredientById(2)
        ));
        Dish updatedDish = dataRetriever.saveDish(dishToUpdate);
        System.out.println("Updated dish: " + updatedDish);

        List<Ingredient> createdIngredients = dataRetriever.createIngredients(
            List.of(new Ingredient(null, "Fromage", CategoryEnum.DAIRY, 1200.0, null))
        );
        System.out.println("Created ingredients: " + createdIngredients);

        Order newOrder = new Order();
        newOrder.setReference("CMD-001");
        newOrder.setOrderType(OrderTypeEnum.EAT_IN);
        newOrder.setStatus(OrderStatusEnum.CREATED);
        newOrder.setCreationDatetime(java.time.Instant.now());

        Order savedOrder = dataRetriever.saveOrder(newOrder);
        System.out.println("Commande sauvegardée: " + savedOrder);

        Order foundOrder = dataRetriever.findOrderByReference("CMD-001");
        System.out.println("Commande trouvée: " + foundOrder);
    }
}