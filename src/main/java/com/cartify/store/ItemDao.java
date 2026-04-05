package com.cartify.store;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ItemDao {

    private final JdbcTemplate jdbcTemplate;

    public ItemDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Item> findAll() {
        return jdbcTemplate.query(
                "SELECT id, name, description, price, image_urls, category_tags FROM items ORDER BY id DESC",
                (rs, rowNum) -> mapItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getString("image_urls"),
                        rs.getString("category_tags")));
    }

    public List<Item> search(String q) {
        if (q == null || q.trim().isBlank()) {
            return findAll();
        }

        List<String> terms = Arrays.stream(q.trim().toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (terms.isEmpty()) {
            return findAll();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, name, description, price, image_urls, category_tags FROM items WHERE ");
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append("(")
                    .append("(LOWER(name) = ? OR LOWER(name) LIKE ? OR LOWER(name) LIKE ? OR LOWER(name) LIKE ?)")
                    .append(" OR ")
                    .append("(LOWER(description) = ? OR LOWER(description) LIKE ? OR LOWER(description) LIKE ? OR LOWER(description) LIKE ?)")
                    .append(" OR ")
                    .append("(',' || LOWER(category_tags) || ',') LIKE ?")
                    .append(")");

            params.add(term);
            params.add(term + " %");
            params.add("% " + term + " %");
            params.add("% " + term);
            params.add(term);
            params.add(term + " %");
            params.add("% " + term + " %");
            params.add("% " + term);
            params.add("%," + term + ",%");
        }

        sql.append(" ORDER BY id DESC");

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> mapItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getString("image_urls"),
                        rs.getString("category_tags")),
                params.toArray());
    }

    public Optional<Item> findById(Long id) {
        List<Item> rows = jdbcTemplate.query(
                "SELECT id, name, description, price, image_urls, category_tags FROM items WHERE id = ? LIMIT 1",
                (rs, rowNum) -> mapItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getString("image_urls"),
                        rs.getString("category_tags")),
                id);
        return rows.stream().findFirst();
    }

    public List<Item> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query(
                "SELECT id, name, description, price, image_urls, category_tags FROM items WHERE id IN (" + placeholders + ")",
                (rs, rowNum) -> mapItem(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("price"),
                        rs.getString("image_urls"),
                        rs.getString("category_tags")),
                ids.toArray());
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id = ?",
                Integer.class,
                id);
        return count != null && count > 0;
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM items WHERE LOWER(name) = LOWER(?)",
                Integer.class,
                name);
        return count != null && count > 0;
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items", Integer.class);
        return count == null ? 0 : count;
    }

    public Item save(Item item) {
        if (item.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO items (name, description, price, image_urls, category_tags) VALUES (?, ?, ?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                ps.setString(1, item.getName());
                ps.setString(2, item.getDescription());
                ps.setBigDecimal(3, item.getPrice());
                ps.setString(4, item.getImageUrls());
                ps.setString(5, item.getCategoryTags());
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                item.setId(key.longValue());
            }
            return item;
        }

        jdbcTemplate.update(
                "UPDATE items SET name = ?, description = ?, price = ?, image_urls = ?, category_tags = ? WHERE id = ?",
                item.getName(), item.getDescription(), item.getPrice(), item.getImageUrls(), item.getCategoryTags(), item.getId());
        return item;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM items WHERE id = ?", id);
    }

    private Item mapItem(Long id, String name, String description, java.math.BigDecimal price, String imageUrls, String categoryTags) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setDescription(description);
        item.setPrice(price);
        item.setImageUrls(imageUrls);
        item.setCategoryTags(categoryTags);
        return item;
    }
}
