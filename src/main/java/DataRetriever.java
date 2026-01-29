import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DataRetriever {

    public Ingredient findIngredientById(Integer id) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        Ingredient ingredient = null;

        String sql = "SELECT id, name, price, category FROM ingredient WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ingredient = new Ingredient();
                    ingredient.setId(rs.getInt("id"));
                    ingredient.setName(rs.getString("name"));
                    ingredient.setPrice(rs.getDouble("price"));
                    ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));
                }
            }
        } finally {
            dbConnection.closeConnection(connection);
        }

        if (ingredient == null) {
            throw new RuntimeException("Ingredient introuvable avec l'ID: " + id);
        }

        return ingredient;
    }

    public Dish findDishById(Integer id) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        Dish dish = null;

        String SqlDish = "SELECT id, name, dish_type FROM dish WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(SqlDish)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dish = new Dish();
                    dish.setId(rs.getInt("id"));
                    dish.setName(rs.getString("name"));
                    dish.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type")));
                }
            }
        }

        if (dish == null) {
            return null;
        }

        String SqlIngredients = "SELECT i.id, i.name, i.price, i.category, di.required_quantity, di.unit " +
                "FROM ingredient i " +
                "JOIN dish_ingredient di ON i.id = di.id_ingredient " +
                "WHERE di.id_dish = ?";

        List<DishIngredient> dishIngredients = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(SqlIngredients)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ingredient ingredient = new Ingredient();
                    ingredient.setId(rs.getInt("id"));
                    ingredient.setName(rs.getString("name"));
                    ingredient.setPrice(rs.getDouble("price"));
                    ingredient.setCategory(CategoryEnum.valueOf(rs.getString("category")));

                    DishIngredient dishIngredient = new DishIngredient();
                    dishIngredient.setIngredient(ingredient);

                    Double requiredQuantity = rs.getDouble("required_quantity");
                    if (!rs.wasNull()) {
                        dishIngredient.setQuantity(requiredQuantity);
                    }

                    String unitStr = rs.getString("unit");
                    if (unitStr != null) {
                        dishIngredient.setUnit(Unit.valueOf(unitStr));
                    }

                    dishIngredients.add(dishIngredient);
                }
            }
        }

        dbConnection.closeConnection(connection);
        dish.setDishIngredients(dishIngredients);

        return dish;
    }

    public Order findOrderByReference(String reference) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        Order order = null;

        String sql = "SELECT id, reference, creation_datetime, order_type, status FROM \"order\" WHERE reference = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    order = new Order();
                    order.setId(rs.getInt("id"));
                    order.setReference(rs.getString("reference"));
                    order.setCreationDatetime(rs.getTimestamp("creation_datetime").toInstant());

                    String orderTypeStr = rs.getString("order_type");
                    if (orderTypeStr != null) {
                        order.setOrderType(OrderTypeEnum.valueOf(orderTypeStr));
                    }

                    String statusStr = rs.getString("status");
                    if (statusStr != null) {
                        order.setStatus(OrderStatusEnum.valueOf(statusStr));
                    } else {
                        order.setStatus(OrderStatusEnum.CREATED);
                    }

                    List<DishOrder> dishOrders = findDishOrdersByOrderId(order.getId());
                    order.setDishOrderList(dishOrders);
                }
            }
        } catch (SQLException e) {
            throw new SQLException("Erreur lors de la récupération de la commande", e);
        } finally {
            dbConnection.closeConnection(connection);
        }

        if (order == null) {
            throw new RuntimeException("Commande introuvable avec la référence: " + reference);
        }

        return order;
    }

    public Order saveOrder(Order orderToSave) throws SQLException {
        if (orderToSave.getId() != null) {
            Order existingOrder = findOrderById(orderToSave.getId());
            if (existingOrder != null && existingOrder.getStatus() == OrderStatusEnum.DELIVERED) {
                throw new RuntimeException("Impossible de modifier une commande déjà livrée (statut DELIVERED)");
            }
        }

        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();

        try {
            connection.setAutoCommit(false);

            if (orderToSave.getId() == null) {
                String insertSql = """
                    INSERT INTO "order" (reference, creation_datetime, order_type, status) 
                    VALUES (?, ?, ?::order_type_enum, ?::order_status_enum) 
                    RETURNING id
                    """;

                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, orderToSave.getReference());
                    ps.setTimestamp(2, Timestamp.from(orderToSave.getCreationDatetime()));

                    if (orderToSave.getOrderType() != null) {
                        ps.setString(3, orderToSave.getOrderType().name());
                    } else {
                        ps.setNull(3, Types.VARCHAR);
                    }

                    if (orderToSave.getStatus() != null) {
                        ps.setString(4, orderToSave.getStatus().name());
                    } else {
                        ps.setString(4, OrderStatusEnum.CREATED.name());
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        orderToSave.setId(rs.getInt(1));
                    }
                }
            } else {
                String updateSql = """
                    UPDATE "order" 
                    SET reference = ?, creation_datetime = ?, order_type = ?::order_type_enum, status = ?::order_status_enum 
                    WHERE id = ?
                    """;

                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, orderToSave.getReference());
                    ps.setTimestamp(2, Timestamp.from(orderToSave.getCreationDatetime()));

                    if (orderToSave.getOrderType() != null) {
                        ps.setString(3, orderToSave.getOrderType().name());
                    } else {
                        ps.setNull(3, Types.VARCHAR);
                    }

                    if (orderToSave.getStatus() != null) {
                        ps.setString(4, orderToSave.getStatus().name());
                    } else {
                        ps.setString(4, OrderStatusEnum.CREATED.name());
                    }

                    ps.setInt(5, orderToSave.getId());
                    ps.executeUpdate();
                }
            }

            saveDishOrders(connection, orderToSave);

            connection.commit();

            return findOrderByReference(orderToSave.getReference());

        } catch (SQLException e) {
            connection.rollback();
            throw new SQLException("Erreur lors de la sauvegarde de la commande", e);
        } finally {
            dbConnection.closeConnection(connection);
        }
    }

    private Order findOrderById(Integer id) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        Order order = null;

        String sql = "SELECT id, reference, creation_datetime, order_type, status FROM \"order\" WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    order = new Order();
                    order.setId(rs.getInt("id"));
                    order.setReference(rs.getString("reference"));
                    order.setCreationDatetime(rs.getTimestamp("creation_datetime").toInstant());

                    String orderTypeStr = rs.getString("order_type");
                    if (orderTypeStr != null) {
                        order.setOrderType(OrderTypeEnum.valueOf(orderTypeStr));
                    }

                    String statusStr = rs.getString("status");
                    if (statusStr != null) {
                        order.setStatus(OrderStatusEnum.valueOf(statusStr));
                    }
                }
            }
        } finally {
            dbConnection.closeConnection(connection);
        }

        return order;
    }

    private List<DishOrder> findDishOrdersByOrderId(Integer orderId) throws SQLException {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<DishOrder> dishOrders = new ArrayList<>();

        String sql = """
            SELECT do.id, do.id_dish, do.quantity, d.name as dish_name, d.dish_type
            FROM dish_order do
            JOIN dish d ON do.id_dish = d.id
            WHERE do.id_order = ?
            """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DishOrder dishOrder = new DishOrder();
                    dishOrder.setId(rs.getInt("id"));
                    dishOrder.setQuantity(rs.getInt("quantity"));

                    Dish dish = new Dish();
                    dish.setId(rs.getInt("id_dish"));
                    dish.setName(rs.getString("dish_name"));
                    dish.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type")));

                    dishOrder.setDish(dish);
                    dishOrders.add(dishOrder);
                }
            }
        } finally {
            dbConnection.closeConnection(connection);
        }

        return dishOrders;
    }

    private void saveDishOrders(Connection connection, Order order) throws SQLException {
        String deleteSql = "DELETE FROM dish_order WHERE id_order = ?";
        try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
            ps.setInt(1, order.getId());
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO dish_order (id_order, id_dish, quantity) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (DishOrder dishOrder : order.getDishOrderList()) {
                ps.setInt(1, order.getId());
                ps.setInt(2, dishOrder.getDish().getId());
                ps.setInt(3, dishOrder.getQuantity());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}