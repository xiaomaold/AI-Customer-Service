package com.airag.businesscenter.order.repository;

import com.airag.businesscenter.order.domain.Order;
import com.airag.businesscenter.order.domain.OrderSourceChannel;
import com.airag.businesscenter.order.domain.OrderStatus;
import com.airag.businesscenter.user.domain.UserType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Order> findByOrderNo(String orderNo) {
        List<Order> orders = jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, user_type, product_id, product_no, product_name_snapshot,
                       unit_price_snapshot, quantity, total_amount, status, cancel_reason, source_channel,
                       created_time, updated_time
                FROM bc_order
                WHERE order_no = ?
                """,
                rowMapper(),
                orderNo
        );
        return orders.stream().findFirst();
    }

    public List<Order> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, user_type, product_id, product_no, product_name_snapshot,
                       unit_price_snapshot, quantity, total_amount, status, cancel_reason, source_channel,
                       created_time, updated_time
                FROM bc_order
                ORDER BY created_time DESC
                """,
                rowMapper()
        );
    }

    public void insert(Order order) {
        jdbcTemplate.update(
                """
                INSERT INTO bc_order (
                    id, order_no, user_id, user_type, product_id, product_no, product_name_snapshot,
                    unit_price_snapshot, quantity, total_amount, status, cancel_reason, source_channel,
                    created_time, updated_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.id(),
                order.orderNo(),
                order.userId(),
                order.userType().name(),
                order.productId(),
                order.productNo(),
                order.productNameSnapshot(),
                order.unitPriceSnapshot(),
                order.quantity(),
                order.totalAmount(),
                order.status().name(),
                order.cancelReason(),
                order.sourceChannel().name(),
                order.createdTime(),
                order.updatedTime()
        );
    }

    public void updateStatus(String orderNo, OrderStatus status, String cancelReason, LocalDateTime updatedTime) {
        jdbcTemplate.update(
                "UPDATE bc_order SET status = ?, cancel_reason = ?, updated_time = ? WHERE order_no = ?",
                status.name(),
                cancelReason,
                updatedTime,
                orderNo
        );
    }

    private RowMapper<Order> rowMapper() {
        return (rs, rowNum) -> new Order(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("user_id"),
                UserType.valueOf(rs.getString("user_type")),
                rs.getLong("product_id"),
                rs.getString("product_no"),
                rs.getString("product_name_snapshot"),
                rs.getBigDecimal("unit_price_snapshot"),
                rs.getInt("quantity"),
                rs.getBigDecimal("total_amount"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("cancel_reason"),
                OrderSourceChannel.valueOf(rs.getString("source_channel")),
                toLocalDateTime(rs, "created_time"),
                toLocalDateTime(rs, "updated_time")
        );
    }

    private LocalDateTime toLocalDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toLocalDateTime();
    }
}
