import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class Order {
    private Integer id;
    private String reference;
    private Instant creationDatetime;
    private List<DishOrder> dishOrderList;
    private OrderTypeEnum orderType;
    private OrderStatusEnum status;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Instant getCreationDatetime() {
        return creationDatetime;
    }

    public void setCreationDatetime(Instant creationDatetime) {
        this.creationDatetime = creationDatetime;
    }

    public List<DishOrder> getDishOrderList() {
        return dishOrderList;
    }

    public void setDishOrderList(List<DishOrder> dishOrderList) {
        this.dishOrderList = dishOrderList;
    }

    public OrderTypeEnum getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderTypeEnum orderType) {
        this.orderType = orderType;
    }

    public OrderStatusEnum getStatus() {
        return status;
    }

    public void setStatus(OrderStatusEnum status) {
        this.status = status;
    }

    @Override
    public String toString() {
    return "Order{" +
            "id=" + id +
            ", reference='" + reference + '\'' +
            ", creationDatetime=" + creationDatetime +
            ", orderType=" + orderType +
            ", status=" + status +
            ", dishOrderList=" + dishOrderList +
            '}';
    }

    Double getTotalAmountWithoutVat() {
        throw new RuntimeException("Not implemented");
    }

    Double getTotalAmountWithVat() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Order order)) return false;
        return Objects.equals(id, order.id) &&
               Objects.equals(reference, order.reference) &&
               Objects.equals(creationDatetime, order.creationDatetime) &&
               orderType == order.orderType &&
               status == order.status &&
               Objects.equals(dishOrderList, order.dishOrderList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, reference, creationDatetime, orderType, status, dishOrderList);
    }
}
