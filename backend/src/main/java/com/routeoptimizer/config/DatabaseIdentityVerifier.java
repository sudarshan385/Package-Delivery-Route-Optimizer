package com.routeoptimizer.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
public class DatabaseIdentityVerifier {

    public static String resolveExpectedDatabaseName(Environment env) {
        if (env == null) {
            throw new IllegalStateException("FATAL: Environment cannot be null for database identity verification.");
        }

        java.util.List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        if (activeProfiles.contains("test")) {
            return "route_optimizer_test_db";
        }
        if (activeProfiles.contains("dev")) {
            return "packagedeliveryrouteoptimizer";
        }
        if (activeProfiles.contains("prod")) {
            String prodDb = env.getProperty("PROD_DB_NAME");
            if (prodDb == null || prodDb.trim().isEmpty()) {
                throw new IllegalStateException(
                    "FATAL: Database Identity validation failed! Profile 'prod' requires 'PROD_DB_NAME' configuration property to be explicitly set."
                );
            }
            return prodDb.trim();
        }

        // Default or unconfigured profile: require explicit EXPECTED_DB_NAME to fail closed safely
        String explicitExpected = env.getProperty("EXPECTED_DB_NAME");
        if (explicitExpected != null && !explicitExpected.trim().isEmpty()) {
            return explicitExpected.trim();
        }

        throw new IllegalStateException(
            "FATAL: Database Identity validation failed! No supported active profile (dev, test, prod) is set, and 'EXPECTED_DB_NAME' is not configured. Aborting startup to prevent unverified database access."
        );
    }

    public static void verifyDatabaseIdentity(String actualDbName, Environment env, String stage) {
        String expectedDbName = resolveExpectedDatabaseName(env);

        if (actualDbName == null || actualDbName.trim().isEmpty()) {
            throw new IllegalStateException("FATAL: Connected database identity could not be determined.");
        }

        if (!expectedDbName.equals(actualDbName.trim())) {
            throw new IllegalStateException(
                String.format("FATAL: Database Identity mismatch! Expected database '%s' but connected to '%s'.", 
                              expectedDbName, actualDbName)
            );
        }
        System.out.println("Database Identity Verified (" + stage + "): Connected to " + actualDbName);
    }

    private void verify(JdbcTemplate jdbcTemplate, Environment env, String stage) {
        String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        verifyDatabaseIdentity(dbName, env, stage);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource, Environment env) {
        return flyway -> {
            verify(new JdbcTemplate(dataSource), env, "PRE-FLYWAY");
            flyway.migrate();
        };
    }
    
    @Bean
    public CommandLineRunner verifyOnStartup(DataSource dataSource, Environment env) {
        return args -> verify(new JdbcTemplate(dataSource), env, "POST-STARTUP");
    }
}
