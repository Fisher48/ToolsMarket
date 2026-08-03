package ru.fisher.ToolsMarket.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderAdminDto;
import ru.fisher.ToolsMarket.dto.OrderDTO.OrderStatisticsDto;
import ru.fisher.ToolsMarket.dto.UserDTO.UserFilterDto;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderAdminJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Динамический SQL: условия добавляются только если параметр не null.
     */
    public List<OrderAdminDto> findOrdersForAdmin(String status, String search, Long userId) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                o.id,
                o.order_number,
                o.status,
                o.total_price,
                o.created_at,
                o.note,
                u.id as user_id,
                COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as user_name,
                u.email,
                COALESCE(SUM(oi.quantity), 0) as items_count,
                COUNT(DISTINCT oi.product_id) as products_count
            FROM "order" o
            JOIN users u ON u.id = o.user_id
            LEFT JOIN order_item oi ON oi.order_id = o.id
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            params.add(status);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND CAST(o.order_number AS TEXT) LIKE ?");
            params.add("%" + search + "%");
        }
        if (userId != null) {
            sql.append(" AND o.user_id = ?");
            params.add(userId);
        }

        sql.append("""
             GROUP BY o.id, o.order_number, o.status, o.total_price, o.created_at, o.note,
                     u.id, u.first_name, u.last_name, u.username, u.email
            ORDER BY o.created_at DESC
            """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return OrderAdminDto.builder()
                    .id(rs.getLong("id"))
                    .orderNumber(rs.getLong("order_number"))
                    .status(rs.getString("status"))
                    .totalPrice(rs.getBigDecimal("total_price"))
                    .createdAt(createdAt != null ? createdAt.toInstant() : null)
                    .note(rs.getString("note"))
                    .userId(rs.getLong("user_id"))
                    .userName(rs.getString("user_name"))
                    .userEmail(rs.getString("email"))
                    .itemsCount(rs.getLong("items_count"))
                    .productsCount(rs.getLong("products_count"))
                    .build();
        }, params.toArray());
    }

    /**
     * Статистика
     */
    public OrderStatisticsDto getOrderStatistics(String status, String search, Long userId) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                SUM(CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END) as new_orders,
                SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END) as paid_orders,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed_orders,
                SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_orders
            FROM "order"
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND CAST(order_number AS TEXT) LIKE ?");
            params.add("%" + search + "%");
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
            params.add(userId);
        }

        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) ->
                        OrderStatisticsDto.builder()
                                .newOrdersCount(rs.getLong("new_orders"))
                                .paidOrdersCount(rs.getLong("paid_orders"))
                                .completedOrdersCount(rs.getLong("completed_orders"))
                                .cancelledOrdersCount(rs.getLong("cancelled_orders"))
                                .build(),
                params.toArray()
        );
    }

    /**
     * Пользователи, у которых есть заказы — для фильтра.
     */
    public List<UserFilterDto> findUsersWithOrders() {
        String sql = """
            SELECT 
                u.id,
                COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ' '), u.username) as display_name
            FROM users u
            WHERE EXISTS (SELECT 1 FROM "order" o WHERE o.user_id = u.id)
            ORDER BY display_name
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                UserFilterDto.builder()
                        .id(rs.getLong("id"))
                        .displayName(rs.getString("display_name"))
                        .build()
        );
    }
}
