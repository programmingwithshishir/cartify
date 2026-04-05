package com.cartify.store;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CartItemDao {

    private final JdbcTemplate jdbcTemplate;

    public CartItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CartItem> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
                "SELECT id, customer_id, item_id, quantity FROM cart_items WHERE customer_id = ? ORDER BY id DESC",
                (rs, rowNum) -> mapCartItem(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getLong("item_id"),
                        rs.getInt("quantity")),
                customerId);
    }

    public Optional<CartItem> findByCustomerIdAndItemId(Long customerId, Long itemId) {
        List<CartItem> rows = jdbcTemplate.query(
                "SELECT id, customer_id, item_id, quantity FROM cart_items WHERE customer_id = ? AND item_id = ? LIMIT 1",
                (rs, rowNum) -> mapCartItem(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getLong("item_id"),
                        rs.getInt("quantity")),
                customerId, itemId);
        return rows.stream().findFirst();
    }

    public Optional<CartItem> findByIdAndCustomerId(Long id, Long customerId) {
        List<CartItem> rows = jdbcTemplate.query(
                "SELECT id, customer_id, item_id, quantity FROM cart_items WHERE id = ? AND customer_id = ? LIMIT 1",
                (rs, rowNum) -> mapCartItem(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getLong("item_id"),
                        rs.getInt("quantity")),
                id, customerId);
        return rows.stream().findFirst();
    }

    public CartItem save(CartItem cartItem) {
        if (cartItem.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO cart_items (customer_id, item_id, quantity) VALUES (?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                ps.setLong(1, cartItem.getCustomerId());
                ps.setLong(2, cartItem.getItemId());
                ps.setInt(3, cartItem.getQuantity());
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                cartItem.setId(key.longValue());
            }
            return cartItem;
        }

        jdbcTemplate.update(
                "UPDATE cart_items SET customer_id = ?, item_id = ?, quantity = ? WHERE id = ?",
                cartItem.getCustomerId(), cartItem.getItemId(), cartItem.getQuantity(), cartItem.getId());
        return cartItem;
    }

    public void delete(CartItem cartItem) {
        jdbcTemplate.update("DELETE FROM cart_items WHERE id = ?", cartItem.getId());
    }

    private CartItem mapCartItem(Long id, Long customerId, Long itemId, Integer quantity) {
        CartItem cartItem = new CartItem();
        cartItem.setId(id);
        cartItem.setCustomerId(customerId);
        cartItem.setItemId(itemId);
        cartItem.setQuantity(quantity);
        return cartItem;
    }
}
