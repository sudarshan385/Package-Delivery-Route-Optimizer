package com.routeoptimizer;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class TestDatabaseGuardSafetyTest {

    @Test
    public void testGuardRejectsIncorrectDatabase() {
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        when(mockJdbcTemplate.queryForObject("SELECT current_database()", String.class))
            .thenReturn("packagedeliveryrouteoptimizer");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            TestDatabaseGuard.verifyTestDatabase(mockJdbcTemplate);
        });

        assertTrue(ex.getMessage().contains("FATAL: Destructive test operation aborted"));
    }
    
    @Test
    public void testGuardAcceptsCorrectDatabase() {
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        when(mockJdbcTemplate.queryForObject("SELECT current_database()", String.class))
            .thenReturn("route_optimizer_test_db");

        TestDatabaseGuard.verifyTestDatabase(mockJdbcTemplate);
    }
}
