package com.cartify.store;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class StoreOrderDao {

    private final JdbcTemplate jdbcTemplate;

    public StoreOrderDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StoreOrder save(StoreOrder order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO customer_orders (" +
                            "customer_id, status, total_amount, shipping_full_name, shipping_phone, shipping_line1, shipping_line2, " +
                            "shipping_city, shipping_state, shipping_postal_code, shipping_country) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setLong(1, order.getCustomerId());
            ps.setString(2, order.getStatus().name());
            ps.setBigDecimal(3, order.getTotalAmount());
            ps.setString(4, order.getShippingFullName());
            ps.setString(5, order.getShippingPhone());
            ps.setString(6, order.getShippingLine1());
            ps.setString(7, order.getShippingLine2());
            ps.setString(8, order.getShippingCity());
            ps.setString(9, order.getShippingState());
            ps.setString(10, order.getShippingPostalCode());
            ps.setString(11, order.getShippingCountry());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            order.setId(key.longValue());
        }
        return order;
    }

    public List<StoreOrder> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
                "SELECT id, customer_id, status, total_amount, shipping_full_name, shipping_phone, shipping_line1, shipping_line2, " +
                        "shipping_city, shipping_state, shipping_postal_code, shipping_country, created_at, updated_at " +
                        "FROM customer_orders WHERE customer_id = ? ORDER BY id DESC",
                (rs, rowNum) -> mapOrder(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("shipping_full_name"),
                        rs.getString("shipping_phone"),
                        rs.getString("shipping_line1"),
                        rs.getString("shipping_line2"),
                        rs.getString("shipping_city"),
                        rs.getString("shipping_state"),
                        rs.getString("shipping_postal_code"),
                        rs.getString("shipping_country"),
                        rs.getString("created_at"),
                        rs.getString("updated_at"),
                        null),
                customerId);
    }

    public List<StoreOrder> findAllWithCustomerEmail() {
        return jdbcTemplate.query(
                "SELECT o.id, o.customer_id, o.status, o.total_amount, o.shipping_full_name, o.shipping_phone, o.shipping_line1, o.shipping_line2, " +
                        "o.shipping_city, o.shipping_state, o.shipping_postal_code, o.shipping_country, o.created_at, o.updated_at, u.email AS customer_email " +
                        "FROM customer_orders o JOIN users u ON u.id = o.customer_id ORDER BY o.id DESC",
                (rs, rowNum) -> mapOrder(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("shipping_full_name"),
                        rs.getString("shipping_phone"),
                        rs.getString("shipping_line1"),
                        rs.getString("shipping_line2"),
                        rs.getString("shipping_city"),
                        rs.getString("shipping_state"),
                        rs.getString("shipping_postal_code"),
                        rs.getString("shipping_country"),
                        rs.getString("created_at"),
                        rs.getString("updated_at"),
                        rs.getString("customer_email")));
    }

    public Optional<StoreOrder> findById(Long orderId) {
        List<StoreOrder> rows = jdbcTemplate.query(
                "SELECT id, customer_id, status, total_amount, shipping_full_name, shipping_phone, shipping_line1, shipping_line2, " +
                        "shipping_city, shipping_state, shipping_postal_code, shipping_country, created_at, updated_at " +
                        "FROM customer_orders WHERE id = ? LIMIT 1",
                (rs, rowNum) -> mapOrder(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getString("shipping_full_name"),
                        rs.getString("shipping_phone"),
                        rs.getString("shipping_line1"),
                        rs.getString("shipping_line2"),
                        rs.getString("shipping_city"),
                        rs.getString("shipping_state"),
                        rs.getString("shipping_postal_code"),
                        rs.getString("shipping_country"),
                        rs.getString("created_at"),
                        rs.getString("updated_at"),
                        null),
                orderId);
        return rows.stream().findFirst();
    }

    public void updateStatus(Long orderId, OrderStatus status) {
        jdbcTemplate.update(
                "UPDATE customer_orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                status.name(), orderId);
    }

    public void deleteById(Long orderId) {
        jdbcTemplate.update("DELETE FROM customer_orders WHERE id = ?", orderId);
    }

    private StoreOrder mapOrder(
            Long id,
            Long customerId,
            String status,
            java.math.BigDecimal totalAmount,
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country,
            String createdAt,
            String updatedAt,
            String customerEmail) {
        StoreOrder order = new StoreOrder();
        order.setId(id);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.valueOf(status));
        order.setTotalAmount(totalAmount);
        order.setShippingFullName(fullName);
        order.setShippingPhone(phone);
        order.setShippingLine1(line1);
        order.setShippingLine2(line2);
        order.setShippingCity(city);
        order.setShippingState(state);
        order.setShippingPostalCode(postalCode);
        order.setShippingCountry(country);
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(updatedAt);
        order.setCustomerEmail(customerEmail);
        return order;
    }
}
