package edu.cit.nunez;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class DbResetTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Disabled("Only enable manually when a fresh database reset is needed")
    public void resetDatabaseForCleanSlate() {
        System.out.println("=== Resetting database tables for clean slate ===");
        try {
            jdbcTemplate.execute("DELETE FROM order_items");
            jdbcTemplate.execute("DELETE FROM orders");
            jdbcTemplate.execute("DELETE FROM tiangge_orders");
            jdbcTemplate.execute("DELETE FROM tiangge_processed_events");
            jdbcTemplate.execute("DELETE FROM tiangge_backorders");
            jdbcTemplate.execute("DELETE FROM tiangge_cursor");
            jdbcTemplate.execute("DELETE FROM supplier_orders");

            jdbcTemplate.execute("""
                INSERT INTO inventory (product_id, name, stock)
                VALUES
                    ('P100', 'Wireless Mouse', 20),
                    ('P200', 'Mechanical Keyboard', 20),
                    ('P300', 'USB-C Hub', 20)
                ON CONFLICT (product_id) DO UPDATE SET stock = EXCLUDED.stock
            """);

            System.out.println("=== Database reset successful! Stock reset to 20 each. ===");
        } catch (Exception e) {
            System.err.println("Error during database reset: " + e.getMessage());
            throw e;
        }
    }
}
