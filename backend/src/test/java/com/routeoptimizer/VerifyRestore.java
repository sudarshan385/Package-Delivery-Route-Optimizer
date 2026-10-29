package com.routeoptimizer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class VerifyRestore {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/restore_verify_db";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "root");
             Statement stmt = conn.createStatement()) {
             
            System.out.println("=== Database Verification ===");
            ResultSet rs = stmt.executeQuery("SELECT current_database()");
            if (rs.next()) {
                System.out.println("Connected DB Name: " + rs.getString(1));
            }
            
            System.out.println("\n=== Table Counts ===");
            String[] tables = {"flyway_schema_history", "locations", "route_edges", "location_edges", "drivers", "users"};
            for (String table : tables) {
                try {
                    ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    if (countRs.next()) {
                        System.out.println(table + " count: " + countRs.getInt(1));
                    }
                } catch (Exception ex) {
                    System.out.println(table + " count error: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
