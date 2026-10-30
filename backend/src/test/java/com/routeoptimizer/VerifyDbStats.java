package com.routeoptimizer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerifyDbStats {
    public static void main(String[] args) {
        verify("route_optimizer_db");
        verify("route_optimizer_test_db");
    }

    private static void verify(String dbName) {
        String url = "jdbc:postgresql://localhost:5432/" + dbName;
        try (Connection conn = DriverManager.getConnection(url, "postgres", "root");
             Statement stmt = conn.createStatement()) {
             
            System.out.println("=== Database: " + dbName + " ===");
            try (ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
                if (rs.next()) {
                    System.out.println("Connected DB Name: " + rs.getString(1));
                }
            }
            
            String[] tables = {"users", "locations", "route_edges", "packages", "deliveries"};
            for (String table : tables) {
                try {
                    try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        if (countRs.next()) {
                            System.out.println("Table " + table + " row count: " + countRs.getInt(1));
                        }
                    }
                } catch (Exception ex) {
                    System.out.println("Table " + table + " check failed: " + ex.getMessage());
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("Could not connect to " + dbName + ": " + e.getMessage());
        }
    }
}
