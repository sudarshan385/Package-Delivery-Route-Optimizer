package com.routeoptimizer;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerifyDbStatsTest {
    
    @Test
    public void test() {
        verify("packagedeliveryrouteoptimizer");
    }

    private void verify(String dbName) {
        String url = "jdbc:postgresql://localhost:5432/" + dbName;
        try (Connection conn = DriverManager.getConnection(url, "app_user", "app_password");
             Statement stmt = conn.createStatement()) {
             
            System.out.println("=== Database Identity: " + dbName + " ===");
            ResultSet rs = stmt.executeQuery("SELECT current_database()");
            if (rs.next()) {
                System.out.println("Authoritative DB Name: " + rs.getString(1));
            }
            
            String[] tables = {
                "locations", "location_edges", "users", "drivers", "vehicles", 
                "packages", "deliveries", "delivery_legs", "shipment_events", 
                "flyway_schema_history"
            };
            for (String table : tables) {
                try {
                    ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    if (countRs.next()) {
                        System.out.println("Table [" + table + "] row count: " + countRs.getInt(1));
                    }
                } catch (Exception ex) {
                    System.out.println("Table " + table + " check failed: " + ex.getMessage());
                }
            }
            
            System.out.println("\n--- Flyway Schema Migrations ---");
            ResultSet flyRs = stmt.executeQuery("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");
            while (flyRs.next()) {
                System.out.println("  V" + flyRs.getString("version") + ": " + flyRs.getString("description") + " (Success: " + flyRs.getBoolean("success") + ")");
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("Could not connect to " + dbName + ": " + e.getMessage());
        }
    }
}
