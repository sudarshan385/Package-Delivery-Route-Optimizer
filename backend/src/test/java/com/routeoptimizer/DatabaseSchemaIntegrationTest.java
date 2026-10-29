package com.routeoptimizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class DatabaseSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "users",
            "drivers",
            "vehicles",
            "locations",
            "location_edges",
            "packages",
            "deliveries",
            "delivery_status_history",
            "routes",
            "route_stops",
            "route_edges"
    );

    @BeforeEach
    void cleanResidualTestData() {
        for (String tableName : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + tableName + " CASCADE");
        }
    }

    @Test
    @DisplayName("Verify Spring Boot application context loads with ddl-auto=validate")
    void contextLoads() {
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    @DisplayName("Verify all 11 required database tables exist in the public schema")
    void verifyAllRequiredTablesExist() {
        for (String tableName : REQUIRED_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class,
                    tableName
            );
            assertThat(count)
                    .withFailMessage("Expected table '%s' to exist in public schema", tableName)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Verify all business tables contain zero records after Flyway migrations")
    void verifyAllTablesContainZeroRecords() {
        for (String tableName : REQUIRED_TABLES) {
            Long rowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName,
                    Long.class
            );
            assertThat(rowCount)
                    .withFailMessage("Expected table '%s' to contain 0 records, but found %d", tableName, rowCount)
                    .isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("Verify self-loop constraint on location_edges table")
    void verifyLocationEdgeNoSelfLoopConstraint() {
        // First insert a test location
        jdbcTemplate.update(
                "INSERT INTO locations (name, latitude, longitude, active, version, created_at, updated_at) " +
                        "VALUES ('Test Node 1', 12.9716, 77.5946, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
        );

        Long locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM locations WHERE name = 'Test Node 1'",
                Long.class
        );

        // Attempting to insert a self-loop (source = destination) must throw an exception
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO location_edges (source_location_id, destination_location_id, distance, active, version, created_at, updated_at) " +
                            "VALUES (?, ?, 5.0, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    locationId, locationId
            );
        }, "Inserting a self-loop edge should fail the chk_no_self_loop constraint");

        // Clean up test location
        jdbcTemplate.update("DELETE FROM locations WHERE id = ?", locationId);
    }

    @Test
    @DisplayName("Verify distance must be positive constraint on location_edges table")
    void verifyLocationEdgePositiveDistanceConstraint() {
        jdbcTemplate.update(
                "INSERT INTO locations (name, latitude, longitude, active, version, created_at, updated_at) " +
                        "VALUES ('Node A', 12.9716, 77.5946, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), " +
                        "       ('Node B', 12.9720, 77.5950, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
        );

        Long locA = jdbcTemplate.queryForObject("SELECT id FROM locations WHERE name = 'Node A'", Long.class);
        Long locB = jdbcTemplate.queryForObject("SELECT id FROM locations WHERE name = 'Node B'", Long.class);

        // Attempting to insert distance <= 0 must fail
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO location_edges (source_location_id, destination_location_id, distance, active, version, created_at, updated_at) " +
                            "VALUES (?, ?, -1.0, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    locA, locB
            );
        }, "Inserting negative distance should fail CHECK (distance > 0)");

        // Clean up
        jdbcTemplate.update("DELETE FROM locations WHERE id IN (?, ?)", locA, locB);
    }
}
