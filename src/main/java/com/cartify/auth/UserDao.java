package com.cartify.auth;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbcTemplate.query(
                "SELECT id, full_name, email, password, role FROM users WHERE LOWER(email) = LOWER(?) LIMIT 1",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("password"), rs.getString("role")),
                email);
        return rows.stream().findFirst();
    }

    public Optional<User> findByEmailAndRole(String email, UserRole role) {
        List<User> rows = jdbcTemplate.query(
                "SELECT id, full_name, email, password, role FROM users WHERE LOWER(email) = LOWER(?) AND role = ? LIMIT 1",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("password"), rs.getString("role")),
                email, role.name());
        return rows.stream().findFirst();
    }

    public User save(User user) {
        if (user.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO users (full_name, email, password, role) VALUES (?, ?, ?, ?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getEmail());
                ps.setString(3, user.getPassword());
                ps.setString(4, user.getRole().name());
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                user.setId(key.longValue());
            }
            return user;
        }

        jdbcTemplate.update(
                "UPDATE users SET full_name = ?, email = ?, password = ?, role = ? WHERE id = ?",
                user.getFullName(), user.getEmail(), user.getPassword(), user.getRole().name(), user.getId());
        return user;
    }

    private User mapUser(Long id, String fullName, String email, String password, String role) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(UserRole.valueOf(role));
        return user;
    }
}
