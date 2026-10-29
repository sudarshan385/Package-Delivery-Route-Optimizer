package com.routeoptimizer;

import org.springframework.jdbc.core.JdbcTemplate;

public class TestDatabaseGuard {
    private static final String REQUIRED_TEST_DB = "route_optimizer_test_db";

    public static void verifyTestDatabase(JdbcTemplate jdbcTemplate) {
        String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        if (!REQUIRED_TEST_DB.equals(dbName)) {
            throw new IllegalStateException("FATAL: Destructive test operation aborted! " +
                    "Connected to database '" + dbName + "' but expected '" + REQUIRED_TEST_DB + "'.");
        }
    }
}
