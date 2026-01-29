import java.util.Objects;

public class DishOrder {
    private Integer id;
    private Dish dish;
    private Integer quantity;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DishOrder dishOrder)) return false;
        return Objects.equals(id, dishOrder.id) && Objects.equals(dish, dishOrder.dish) && Objects.equals(quantity, dishOrder.quantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dish, quantity);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Dish getDish() {
        return dish;
    }

    public void setDish(Dish dish) {
        this.dish = dish;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
