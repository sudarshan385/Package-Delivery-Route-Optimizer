package com.routeoptimizer;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CreateDbTest {

    @Test
    public void createDatabase() {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://localhost:5432/postgres";
            try (Connection conn = DriverManager.getConnection(url, "postgres", "root");
                 Statement stmt = conn.createStatement()) {
                
                ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'route_optimizer_test_db'");
                if (!rs.next()) {
                    stmt.executeUpdate("CREATE DATABASE route_optimizer_test_db");
                    System.out.println("TEST DB CREATED SUCCESSFULLY");
                } else {
                    System.out.println("TEST DB ALREADY EXISTS");
                }
            }
        } catch (Exception e) {
            System.out.println("CreateDbTest note: " + e.getMessage());
        }
    }
}
