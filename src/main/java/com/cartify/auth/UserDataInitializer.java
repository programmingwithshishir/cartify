package com.cartify.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final UserDao userDao;

    public UserDataInitializer(JdbcTemplate jdbcTemplate, UserDao userDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDao = userDao;
    }

    @Override
    public void run(String... args) {
        createTablesIfMissing();
        migrateCustomersTableToUsers();
        ensureAdminUserExists();
    }

    private void createTablesIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    price NUMERIC NOT NULL,
                    image_urls TEXT NOT NULL,
                    category_tags TEXT NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cart_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER NOT NULL,
                    item_id INTEGER NOT NULL,
                    quantity INTEGER NOT NULL
                )
                """);
    }

    private void migrateCustomersTableToUsers() {
        if (tableExists("customers") && !tableExists("users")) {
            jdbcTemplate.execute("ALTER TABLE customers RENAME TO users");
        }

        if (tableExists("users") && !columnExists("users", "role")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'CUSTOMER'");
        }

        if (tableExists("customers") && tableExists("users")) {
            jdbcTemplate.execute("""
                    INSERT INTO users (full_name, email, password, role)
                    SELECT c.full_name, c.email, c.password, 'CUSTOMER'
                    FROM customers c
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM users u
                        WHERE LOWER(u.email) = LOWER(c.email)
                    )
                    """);
            jdbcTemplate.execute("DROP TABLE customers");
        }
    }

    private void ensureAdminUserExists() {
        User admin = userDao.findByEmail("admin").orElseGet(User::new);
        admin.setFullName("Administrator");
        admin.setEmail("admin");
        admin.setPassword("pewpew");
        admin.setRole(UserRole.ADMIN);
        userDao.save(admin);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        return jdbcTemplate.query("PRAGMA table_info(" + tableName + ")",
                (rs, rowNum) -> rs.getString("name"))
                .stream()
                .anyMatch(columnName::equalsIgnoreCase);
    }
}
