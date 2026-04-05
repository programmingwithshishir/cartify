package com.cartify.store;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AddressDao {

    private final JdbcTemplate jdbcTemplate;

    public AddressDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Address> findByCustomerId(Long customerId) {
        return jdbcTemplate.query(
                "SELECT id, customer_id, full_name, phone, line1, line2, city, state, postal_code, country, created_at " +
                        "FROM addresses WHERE customer_id = ? ORDER BY id DESC",
                (rs, rowNum) -> mapAddress(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getString("line1"), rs.getString("line2"), rs.getString("city"),
                        rs.getString("state"), rs.getString("postal_code"), rs.getString("country"), rs.getString("created_at")),
                customerId);
    }

    public Optional<Address> findByIdAndCustomerId(Long id, Long customerId) {
        List<Address> rows = jdbcTemplate.query(
                "SELECT id, customer_id, full_name, phone, line1, line2, city, state, postal_code, country, created_at " +
                        "FROM addresses WHERE id = ? AND customer_id = ? LIMIT 1",
                (rs, rowNum) -> mapAddress(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getString("line1"), rs.getString("line2"), rs.getString("city"),
                        rs.getString("state"), rs.getString("postal_code"), rs.getString("country"), rs.getString("created_at")),
                id, customerId);
        return rows.stream().findFirst();
    }

    public Address save(Address address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO addresses (customer_id, full_name, phone, line1, line2, city, state, postal_code, country) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            ps.setLong(1, address.getCustomerId());
            ps.setString(2, address.getFullName());
            ps.setString(3, address.getPhone());
            ps.setString(4, address.getLine1());
            ps.setString(5, address.getLine2());
            ps.setString(6, address.getCity());
            ps.setString(7, address.getState());
            ps.setString(8, address.getPostalCode());
            ps.setString(9, address.getCountry());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            address.setId(key.longValue());
        }
        return address;
    }

    private Address mapAddress(
            Long id,
            Long customerId,
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country,
            String createdAt) {
        Address address = new Address();
        address.setId(id);
        address.setCustomerId(customerId);
        address.setFullName(fullName);
        address.setPhone(phone);
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postalCode);
        address.setCountry(country);
        address.setCreatedAt(createdAt);
        return address;
    }
}
