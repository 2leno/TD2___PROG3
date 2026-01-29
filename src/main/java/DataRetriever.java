import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataRetriever {
    public Order findOrderByReference(String reference) {
        String sql = """
            SELECT o.id, o.reference, o.creation_datetime, o.order_type, o.status
            FROM "order" o
            WHERE o.reference = ?
            """;

        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, reference);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Order order = new Order();
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

                order.setDishOrders(findDishOrdersByOrderId(order.getId()));

                return order;
            } else {
                throw new RuntimeException("Order not found with reference: " + reference);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Order saveOrder(Order orderToSave) {
        if (orderToSave.getId() != null) {
            Order existingOrder = findOrderById(orderToSave.getId());

            if (existingOrder != null && existingOrder.getStatus() == OrderStatusEnum.DELIVERED) {
                throw new IllegalStateException("Une commande livrée ne peut plus être modifiée");
            }
        }

        String upsertOrderSql = """
            INSERT INTO "order" (id, reference, creation_datetime, order_type, status)
            VALUES (?, ?, ?, ?::order_type_enum, ?::order_status_enum)
            ON CONFLICT (id) DO UPDATE
            SET reference = EXCLUDED.reference,
                order_type = EXCLUDED.order_type,
                status = EXCLUDED.status
            RETURNING id
            """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer orderId;

            try (PreparedStatement ps = conn.prepareStatement(upsertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                if (orderToSave.getId() != null) {
                    ps.setInt(1, orderToSave.getId());
                } else {
                    ps.setNull(1, Types.INTEGER);
                }

                ps.setString(2, orderToSave.getReference());
                ps.setTimestamp(3, Timestamp.from(
                    orderToSave.getCreationDatetime() != null
                    ? orderToSave.getCreationDatetime()
                    : java.time.Instant.now()
                ));

                if (orderToSave.getOrderType() != null) {
                    ps.setString(4, orderToSave.getOrderType().name());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }

                if (orderToSave.getStatus() != null) {
                    ps.setString(5, orderToSave.getStatus().name());
                } else {
                    ps.setString(5, OrderStatusEnum.CREATED.name());
                }

                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Saving order failed, no rows affected.");
                }

                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        orderId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Saving order failed, no ID obtained.");
                    }
                }
            }

            if (orderToSave.getDishOrders() != null && !orderToSave.getDishOrders().isEmpty()) {
                saveDishOrders(conn, orderId, orderToSave.getDishOrders());
            }

            conn.commit();
            return findOrderById(orderId);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Order findOrderById(Integer id) {
        String sql = """
            SELECT id, reference, creation_datetime, order_type, status
            FROM "order"
            WHERE id = ?
            """;

        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                Order order = new Order();
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

                order.setDishOrders(findDishOrdersByOrderId(id));
                return order;
            }
            return null;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<DishOrder> findDishOrdersByOrderId(Integer orderId) {
        List<DishOrder> dishOrders = new ArrayList<>();
        String sql = """
            SELECT do.id, do.id_dish, do.quantity
            FROM dish_order do
            WHERE do.id_order = ?
            """;

        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                DishOrder dishOrder = new DishOrder();
                dishOrder.setId(rs.getInt("id"));

                Dish dish = findDishById(rs.getInt("id_dish"));
                dishOrder.setDish(dish);

                dishOrder.setQuantity(rs.getInt("quantity"));
                dishOrders.add(dishOrder);
            }

            return dishOrders;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveDishOrders(Connection conn, Integer orderId, List<DishOrder> dishOrders) throws SQLException {
        String deleteSql = "DELETE FROM dish_order WHERE id_order = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }

        String insertSql = """
            INSERT INTO dish_order (id_order, id_dish, quantity)
            VALUES (?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (DishOrder dishOrder : dishOrders) {
                ps.setInt(1, orderId);
                ps.setInt(2, dishOrder.getDish().getId());
                ps.setInt(3, dishOrder.getQuantity());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Ingredient findIngredientById(Integer idIngredient) {
        DBConnection dbConnection = new DBConnection();
        try (Connection connection = dbConnection.getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement(
                "select id, name, price, category from ingredient where id = ?;");
            preparedStatement.setInt(1, idIngredient);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int idIng = resultSet.getInt("id");
                String name = resultSet.getString("name");
                CategoryEnum category = CategoryEnum.valueOf(resultSet.getString("category"));
                Double price = resultSet.getDouble("price");
                List<StockMovement> stockMovements = findStockMovementsByIngredientId(idIng);
                return new Ingredient(idIng, name, category, price, stockMovements);
            }
            throw new RuntimeException("Ingredient not found " + idIngredient);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Ingredient saveIngredient(Ingredient ingredientToSave) {
        String upsertIngredientSql = """
                INSERT INTO ingredient (id, name, price, category)
                VALUES (?, ?, ?, ?::ingredient_category)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    category = EXCLUDED.category,
                    price = EXCLUDED.price
                RETURNING id
            """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer ingredientId;

            try (PreparedStatement ps = conn.prepareStatement(upsertIngredientSql)) {
                if (ingredientToSave.getId() != null) {
                    ps.setInt(1, ingredientToSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                }

                ps.setString(2, ingredientToSave.getName());

                if (ingredientToSave.getPrice() != null) {
                    ps.setDouble(3, ingredientToSave.getPrice());
                } else {
                    ps.setNull(3, Types.DOUBLE);
                }

                ps.setString(4, ingredientToSave.getCategory().name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ingredientId = rs.getInt(1);
                }
            }

            if (ingredientToSave.getStockMovementList() != null && !ingredientToSave.getStockMovementList().isEmpty()) {
                insertIngredientStockMovements(conn, ingredientId, ingredientToSave.getStockMovementList());
            }

            conn.commit();
            return findIngredientById(ingredientId);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Dish findDishById(Integer idDish) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    """
                            select dish.id as dish_id, dish.name as dish_name, dish_type, dish.selling_price as dish_price
                            from dish
                            where dish.id = ?;
                            """);
            preparedStatement.setInt(1, idDish);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                Dish dish = new Dish();
                dish.setId(resultSet.getInt("dish_id"));
                dish.setName(resultSet.getString("dish_name"));
                dish.setDishType(DishTypeEnum.valueOf(resultSet.getString("dish_type")));
                dish.setPrice(resultSet.getObject("dish_price") == null
                        ? null : resultSet.getDouble("dish_price"));
                dish.setDishIngredients(findIngredientByDishId(idDish));
                return dish;
            }
            dbConnection.closeConnection(connection);
            throw new RuntimeException("Dish not found " + idDish);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Dish saveDish(Dish dishToSave) {
        String upsertDishSql = """
                INSERT INTO dish (id, selling_price, name, dish_type)
                VALUES (?, ?, ?, ?::dish_type)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    dish_type = EXCLUDED.dish_type,
                    selling_price = EXCLUDED.selling_price
                RETURNING id
            """;

        try (Connection conn = new DBConnection().getConnection()) {
            conn.setAutoCommit(false);
            Integer dishId;

            try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                if (dishToSave.getId() != null) {
                    ps.setInt(1, dishToSave.getId());
                } else {
                    ps.setInt(1, getNextSerialValue(conn, "dish", "id"));
                }

                if (dishToSave.getPrice() != null) {
                    ps.setDouble(2, dishToSave.getPrice());
                } else {
                    ps.setNull(2, Types.DOUBLE);
                }

                ps.setString(3, dishToSave.getName());
                ps.setString(4, dishToSave.getDishType().name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    dishId = rs.getInt(1);
                }
            }

            List<DishIngredient> newDishIngredients = dishToSave.getDishIngredients();
            if (newDishIngredients != null) {
                detachIngredients(conn, newDishIngredients);
                attachIngredients(conn, newDishIngredients);
            }

            conn.commit();
            return findDishById(dishId);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Ingredient> createIngredients(List<Ingredient> newIngredients) {
        if (newIngredients == null || newIngredients.isEmpty()) {
            return List.of();
        }

        List<Ingredient> savedIngredients = new ArrayList<>();
        DBConnection dbConnection = new DBConnection();
        Connection conn = dbConnection.getConnection();
        try {
            conn.setAutoCommit(false);
            String insertSql = """
                    INSERT INTO ingredient (id, name, category, price)
                    VALUES (?, ?, ?::ingredient_category, ?)
                    RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Ingredient ingredient : newIngredients) {
                    if (ingredient.getId() != null) {
                        ps.setInt(1, ingredient.getId());
                    } else {
                        ps.setInt(1, getNextSerialValue(conn, "ingredient", "id"));
                    }
                    ps.setString(2, ingredient.getName());
                    ps.setString(3, ingredient.getCategory().name());

                    if (ingredient.getPrice() != null) {
                        ps.setDouble(4, ingredient.getPrice());
                    } else {
                        ps.setNull(4, Types.DOUBLE);
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        int generatedId = rs.getInt(1);
                        ingredient.setId(generatedId);
                        savedIngredients.add(ingredient);
                    }
                }
                conn.commit();
                return savedIngredients;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.closeConnection(conn);
        }
    }

    private void insertIngredientStockMovements(Connection conn, Integer ingredientId, List<StockMovement> stockMovementList) {
        String sql = """
                insert into stock_movement(id, id_ingredient, quantity, type, unit, creation_datetime)
                values (?, ?, ?, ?::movement_type, ?::unit, ?)
                on conflict (id) do nothing
                """;
        try {
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            for (StockMovement stockMovement : stockMovementList) {
                preparedStatement.setInt(1, getNextSerialValue(conn, "stock_movement", "id"));
                preparedStatement.setInt(2, ingredientId);
                preparedStatement.setDouble(3, stockMovement.getValue().getQuantity());
                preparedStatement.setString(4, stockMovement.getType().name());
                preparedStatement.setString(5, stockMovement.getValue().getUnit().name());
                preparedStatement.setTimestamp(6, Timestamp.from(stockMovement.getCreationDatetime()));
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    List<StockMovement> findStockMovementsByIngredientId(Integer id) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<StockMovement> stockMovementList = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    """
                            select id, quantity, unit, type, creation_datetime
                            from stock_movement
                            where stock_movement.id_ingredient = ?;
                            """);
            preparedStatement.setInt(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                StockMovement stockMovement = new StockMovement();
                stockMovement.setId(resultSet.getInt("id"));
                stockMovement.setType(MovementTypeEnum.valueOf(resultSet.getString("type")));
                stockMovement.setCreationDatetime(resultSet.getTimestamp("creation_datetime").toInstant());

                StockValue stockValue = new StockValue();
                stockValue.setQuantity(resultSet.getDouble("quantity"));
                stockValue.setUnit(Unit.valueOf(resultSet.getString("unit")));
                stockMovement.setValue(stockValue);

                stockMovementList.add(stockMovement);
            }
            dbConnection.closeConnection(connection);
            return stockMovementList;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<DishIngredient> findIngredientByDishId(Integer idDish) {
        DBConnection dbConnection = new DBConnection();
        Connection connection = dbConnection.getConnection();
        List<DishIngredient> dishIngredients = new ArrayList<>();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(
                    """
                            select ingredient.id, ingredient.name, ingredient.price, ingredient.category, di.required_quantity, di.unit
                            from ingredient join dish_ingredient di on di.id_ingredient = ingredient.id where id_dish = ?;
                            """);
            preparedStatement.setInt(1, idDish);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Ingredient ingredient = new Ingredient();
                ingredient.setId(resultSet.getInt("id"));
                ingredient.setName(resultSet.getString("name"));
                ingredient.setPrice(resultSet.getDouble("price"));
                ingredient.setCategory(CategoryEnum.valueOf(resultSet.getString("category")));

                DishIngredient dishIngredient = new DishIngredient();
                dishIngredient.setIngredient(ingredient);
                dishIngredient.setQuantity(resultSet.getObject("required_quantity") == null ? null : resultSet.getDouble("required_quantity"));
                dishIngredient.setUnit(Unit.valueOf(resultSet.getString("unit")));

                dishIngredients.add(dishIngredient);
            }
            dbConnection.closeConnection(connection);
            return dishIngredients;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void detachIngredients(Connection conn, List<DishIngredient> dishIngredients) {
        Map<Integer, List<DishIngredient>> dishIngredientsGroupByDishId = dishIngredients.stream()
                .collect(Collectors.groupingBy(dishIngredient -> dishIngredient.getDish().getId()));
        dishIngredientsGroupByDishId.forEach((dishId, dishIngredientList) -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM dish_ingredient where id_dish = ?")) {
                ps.setInt(1, dishId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void attachIngredients(Connection conn, List<DishIngredient> ingredients)
            throws SQLException {

        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        String attachSql = """
                insert into dish_ingredient (id, id_ingredient, id_dish, required_quantity, unit)
                values (?, ?, ?, ?, ?::unit)
            """;

        try (PreparedStatement ps = conn.prepareStatement(attachSql)) {
            for (DishIngredient dishIngredient : ingredients) {
                ps.setInt(1, getNextSerialValue(conn, "dish_ingredient", "id"));
                ps.setInt(2, dishIngredient.getIngredient().getId());
                ps.setInt(3, dishIngredient.getDish().getId());
                ps.setDouble(4, dishIngredient.getQuantity());
                ps.setString(5, dishIngredient.getUnit().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String getSerialSequenceName(Connection conn, String tableName, String columnName)
            throws SQLException {

        String sql = "SELECT pg_get_serial_sequence(?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return null;
    }

    private int getNextSerialValue(Connection conn, String tableName, String columnName)
            throws SQLException {

        String sequenceName = getSerialSequenceName(conn, tableName, columnName);
        if (sequenceName == null) {
            throw new IllegalArgumentException(
                    "Any sequence found for " + tableName + "." + columnName
            );
        }
        updateSequenceNextValue(conn, tableName, columnName, sequenceName);

        String nextValSql = "SELECT nextval(?)";

        try (PreparedStatement ps = conn.prepareStatement(nextValSql)) {
            ps.setString(1, sequenceName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void updateSequenceNextValue(Connection conn, String tableName, String columnName, String sequenceName) throws SQLException {
        String setValSql = String.format(
                "SELECT setval('%s', (SELECT COALESCE(MAX(%s), 0) FROM %s))",
                sequenceName, columnName, tableName
        );

        try (PreparedStatement ps = conn.prepareStatement(setValSql)) {
            ps.executeQuery();
        }
    }
}