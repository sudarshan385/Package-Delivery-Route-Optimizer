package com.routeoptimizer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class PreRecoveryCheck {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/route_optimizer_db";
        try (Connection conn = DriverManager.getConnection(url, "postgres", "root");
             Statement stmt = conn.createStatement()) {
             
            System.out.println("=== Database Verification ===");
            try (ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
                if (rs.next()) {
                    System.out.println("Connected DB Name: " + rs.getString(1));
                }
            }
            
            System.out.println("\n=== User Check ===");
            try (ResultSet userRs = stmt.executeQuery("SELECT id, role, active FROM users LIMIT 1")) {
                if (userRs.next()) {
                    System.out.println("User ID: " + userRs.getLong("id"));
                    System.out.println("Role: " + userRs.getString("role"));
                    System.out.println("Active: " + userRs.getBoolean("active"));
                } else {
                    System.out.println("No users found.");
                }
            }
            
            System.out.println("\n=== Table Counts ===");
            String[] tables = {"locations", "route_edges", "location_edges", "drivers", "vehicles", "packages", "deliveries", "routes", "route_stops"};
            for (String table : tables) {
                try {
                    try (ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        if (countRs.next()) {
                            System.out.println(table + " count: " + countRs.getInt(1));
                        }
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
