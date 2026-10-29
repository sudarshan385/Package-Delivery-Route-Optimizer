package com.routeoptimizer.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Dedicated server-side preflight verification endpoint for E2E and integration scripts.
 * 
 * Safeguards:
 * 1. Only instantiated when Spring profile is strictly 'test'.
 * 2. Explicitly verifies that 'dev' and 'prod' profiles are NOT present.
 * 3. Checks connected database identity using SELECT current_database() == 'route_optimizer_test_db'.
 * 4. Returns only a safe pass/fail status without exposing connection strings or credentials.
 * 5. Fails closed (404/403) on unverified or production/development environments.
 */
@RestController
@RequestMapping("/api/test-guard")
@Profile("test")
public class TestDatabasePreflightController {

    private final JdbcTemplate jdbcTemplate;
    private final Environment env;

    public TestDatabasePreflightController(JdbcTemplate jdbcTemplate, Environment env) {
        this.jdbcTemplate = jdbcTemplate;
        this.env = env;
    }

    @GetMapping("/preflight")
    public ResponseEntity<Map<String, Object>> verifyTestEnvironmentPreflight() {
        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        // Fail closed if 'test' is missing, or if 'dev' / 'prod' is unexpectedly present
        if (!activeProfiles.contains("test") || activeProfiles.contains("dev") || activeProfiles.contains("prod")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test preflight endpoint unavailable in non-test profiles.");
        }

        // Verify connected database identity
        String currentDb = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        if (!"route_optimizer_test_db".equals(currentDb)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Server-side database identity verification failed.");
        }

        return ResponseEntity.ok(Map.of(
                "verified", true,
                "environment", "test"
        ));
    }
}
