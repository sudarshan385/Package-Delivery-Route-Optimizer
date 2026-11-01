package com.routeoptimizer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PackageTrackingNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Generates a database-backed, concurrency-safe, sequential tracking number.
     * Required Format: PKG-BLR-000001, PKG-BLR-000002, etc.
     */
    public String nextTrackingNumber() {
        Long nextVal;
        try {
            nextVal = jdbcTemplate.queryForObject("SELECT nextval('package_tracking_number_seq')", Long.class);
        } catch (Exception e) {
            log.warn("Sequence package_tracking_number_seq query failed, ensuring sequence exists: {}", e.getMessage());
            try {
                jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS package_tracking_number_seq START WITH 1 INCREMENT BY 1");
                nextVal = jdbcTemplate.queryForObject("SELECT nextval('package_tracking_number_seq')", Long.class);
            } catch (Exception ex) {
                log.error("Fatal error accessing tracking number sequence: {}", ex.getMessage());
                throw new IllegalStateException("Failed to generate sequential package tracking number", ex);
            }
        }
        if (nextVal == null) {
            nextVal = 1L;
        }
        return String.format("PKG-BLR-%06d", nextVal);
    }
}

