package com.cartify.store;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoreOrderItemDao {

    private final JdbcTemplate jdbcTemplate;

    public StoreOrderItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<StoreOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO order_items (order_id, item_id, item_name, item_price, quantity, line_total, image_url) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                items,
                items.size(),
                (PreparedStatement ps, StoreOrderItem item) -> {
                    ps.setLong(1, item.getOrderId());
                    ps.setLong(2, item.getItemId());
                    ps.setString(3, item.getItemName());
                    ps.setBigDecimal(4, item.getItemPrice());
                    ps.setInt(5, item.getQuantity());
                    ps.setBigDecimal(6, item.getLineTotal());
                    ps.setString(7, item.getImageUrl());
                });
    }

    public List<StoreOrderItem> findByOrderId(Long orderId) {
        return jdbcTemplate.query(
                "SELECT id, order_id, item_id, item_name, item_price, quantity, line_total, image_url " +
                        "FROM order_items WHERE order_id = ? ORDER BY id ASC",
                (rs, rowNum) -> mapOrderItem(
                        rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getLong("item_id"),
                        rs.getString("item_name"),
                        rs.getBigDecimal("item_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total"),
                        rs.getString("image_url")),
                orderId);
    }

    public List<StoreOrderItem> findByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT id, order_id, item_id, item_name, item_price, quantity, line_total, image_url " +
                        "FROM order_items WHERE order_id IN (" + placeholders + ") ORDER BY id ASC",
                (rs, rowNum) -> mapOrderItem(
                        rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getLong("item_id"),
                        rs.getString("item_name"),
                        rs.getBigDecimal("item_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total"),
                        rs.getString("image_url")),
                orderIds.toArray());
    }

    public void deleteByOrderId(Long orderId) {
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", orderId);
    }

    private StoreOrderItem mapOrderItem(
            Long id,
            Long orderId,
            Long itemId,
            String itemName,
            java.math.BigDecimal itemPrice,
            Integer quantity,
            java.math.BigDecimal lineTotal,
            String imageUrl) {
        StoreOrderItem item = new StoreOrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setItemId(itemId);
        item.setItemName(itemName);
        item.setItemPrice(itemPrice);
        item.setQuantity(quantity);
        item.setLineTotal(lineTotal);
        item.setImageUrl(imageUrl);
        return item;
    }
}
