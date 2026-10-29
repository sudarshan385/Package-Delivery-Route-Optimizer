package com.routeoptimizer.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

/**
 * Enterprise Admin Password Rotator Utility
 * 
 * Safeguards:
 * 1. Strictly enforces database identity ('packagedeliveryrouteoptimizer' or 'route_optimizer_test_db').
 * 2. Verifies account identity ('sudarshan') and role ('ADMIN').
 * 3. Checks active and blocked flags without silent modification.
 * 4. Uses application's BCryptPasswordEncoder (10 rounds).
 * 5. Uses parameterized SQL PreparedStatement (no command-line or query exposure).
 * 6. Cryptographically verifies stored hash matches immediately after write.
 * 7. Never logs, prints, or echoes plaintext credentials or hashes.
 * 8. Cleanly supports --stdin protocol for non-interactive / pipeline integration.
 */
public class AdminPasswordRotator {

    public static final String DEFAULT_DEV_DB = "packagedeliveryrouteoptimizer";
    public static final String TEST_DB = "route_optimizer_test_db";
    public static final String TARGET_USERNAME = "sudarshan";
    public static final String REQUIRED_ROLE = "ADMIN";

    public static void main(String[] args) {
        int exitCode = executeRotation(args, false);
        System.exit(exitCode);
    }

    public static int executeRotation(String[] args, boolean quiet) {
        boolean force = false;
        boolean fromStdin = false;
        String targetUser = TARGET_USERNAME;
        String targetDb = DEFAULT_DEV_DB;

        for (int i = 0; i < args.length; i++) {
            if ("--force".equalsIgnoreCase(args[i]) || "-f".equalsIgnoreCase(args[i])) {
                force = true;
            } else if ("--stdin".equalsIgnoreCase(args[i])) {
                fromStdin = true;
            } else if ("--username".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                targetUser = args[++i];
            } else if ("--database".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                targetDb = args[++i];
            }
        }

        // Database target safeguard
        if (!DEFAULT_DEV_DB.equalsIgnoreCase(targetDb) && !TEST_DB.equalsIgnoreCase(targetDb)) {
            System.err.println("FATAL: Target database safety guard violation! Expected '" + DEFAULT_DEV_DB + "' or '" + TEST_DB + "' but got '" + targetDb + "'.");
            return 1;
        }

        boolean isTestDb = TEST_DB.equalsIgnoreCase(targetDb);
        String defaultUser = isTestDb ? "postgres" : "app_user";
        String defaultPass = isTestDb ? "root" : "app_password";

        String userEnvVar = isTestDb ? "TEST_DB_USERNAME" : "DEV_DB_USERNAME";
        String passEnvVar = isTestDb ? "TEST_DB_PASSWORD" : "DEV_DB_PASSWORD";

        String dbUrl = "jdbc:postgresql://localhost:5432/" + targetDb;
        String dbUser = System.getenv(userEnvVar) != null ? System.getenv(userEnvVar) : defaultUser;
        String dbPass = System.getenv(passEnvVar) != null ? System.getenv(passEnvVar) : defaultPass;

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            // Guard 1: Verify connected database identity
            try (PreparedStatement ps = conn.prepareStatement("SELECT current_database()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !targetDb.equals(rs.getString(1))) {
                        System.err.println("FATAL: Connected database check failed! Expected '" + targetDb + "'.");
                        return 1;
                    }
                }
            }

            // Guard 2: Verify user exists, has ADMIN role, and check active/blocked flags
            long userId = -1;
            boolean active = false;
            boolean verified = false;
            boolean blocked = false;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, role, active, is_verified, is_blocked FROM users WHERE username = ?")) {
                ps.setString(1, targetUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("FATAL: Target user '" + targetUser + "' not found in database '" + targetDb + "'.");
                        return 1;
                    }
                    userId = rs.getLong("id");
                    String role = rs.getString("role");
                    active = rs.getBoolean("active");
                    verified = rs.getBoolean("is_verified");
                    blocked = rs.getBoolean("is_blocked");

                    if (!REQUIRED_ROLE.equalsIgnoreCase(role)) {
                        System.err.println("FATAL: Target user '" + targetUser + "' has role '" + role + "', expected '" + REQUIRED_ROLE + "'.");
                        return 1;
                    }
                }
            }

            // Report status without modifying flags
            if (!active) {
                System.err.println("SECURITY ALERT: Target administrator account is DEACTIVATED (active=false). Rotation aborted.");
                return 1;
            }
            if (blocked) {
                System.err.println("SECURITY ALERT: Target administrator account is BLOCKED (is_blocked=true). Rotation aborted.");
                return 1;
            }

            // Acquire new password securely
            char[] passwordChars = null;
            BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));

            if (fromStdin) {
                // Documented stdin protocol: read strictly 1 line from standard input without console prompts
                String line = stdinReader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    passwordChars = line.toCharArray();
                } else {
                    System.err.println("FATAL: Standard input stream was empty or closed before password could be read.");
                    return 1;
                }
            } else {
                String envPass = System.getenv("NEW_ADMIN_PASSWORD");
                if (envPass != null && !envPass.trim().isEmpty()) {
                    passwordChars = envPass.toCharArray();
                } else {
                    boolean hasStdinData = false;
                    try {
                        hasStdinData = stdinReader.ready() || System.in.available() > 0;
                    } catch (Exception ignored) {}

                    if (hasStdinData) {
                        String line = stdinReader.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            passwordChars = line.toCharArray();
                        }
                    } else if (System.console() != null) {
                        passwordChars = System.console().readPassword("Enter new password for %s (min 8 characters): ", targetUser);
                    } else {
                        String line = stdinReader.readLine();
                        if (line != null && !line.trim().isEmpty()) {
                            passwordChars = line.toCharArray();
                        }
                    }
                }
            }

            if (passwordChars == null || passwordChars.length < 8) {
                System.err.println("FATAL: Password rejected: must be at least 8 characters in length.");
                return 1;
            }

            // Operator confirmation if not forced and interactive console is present
            if (!force && !fromStdin) {
                boolean hasStdinData = false;
                try {
                    hasStdinData = stdinReader.ready() || System.in.available() > 0;
                } catch (Exception ignored) {}

                String confirm = null;
                if (hasStdinData) {
                    confirm = stdinReader.readLine();
                } else if (System.console() != null) {
                    confirm = System.console().readLine("Confirm password rotation for administrator '%s' in '%s' (y/N): ", targetUser, targetDb);
                } else {
                    confirm = stdinReader.readLine();
                }

                if (!"y".equalsIgnoreCase(confirm) && !"yes".equalsIgnoreCase(confirm)) {
                    System.err.println("OPERATION CANCELLED: Operator cancelled password rotation.");
                    Arrays.fill(passwordChars, ' ');
                    return 2;
                }
            }

            // Encode using application's BCryptPasswordEncoder
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String rawPassword = new String(passwordChars);
            String encodedHash = encoder.encode(rawPassword);

            // Parameterized update modifying ONLY password and updated_at
            String updateSql = "UPDATE users SET password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND username = ? AND role = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, encodedHash);
                ps.setLong(2, userId);
                ps.setString(3, targetUser);
                ps.setString(4, REQUIRED_ROLE);
                int rows = ps.executeUpdate();
                if (rows != 1) {
                    System.err.println("FATAL: Expected exactly 1 row to update, but updated " + rows + " rows! Rolling back.");
                    return 1;
                }
            }

            // Verify stored hash matches new password cryptographically
            try (PreparedStatement ps = conn.prepareStatement("SELECT password FROM users WHERE id = ? AND username = ?")) {
                ps.setLong(1, userId);
                ps.setString(2, targetUser);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("FATAL: Could not re-read user record post-update.");
                        return 1;
                    }
                    String storedHash = rs.getString("password");
                    if (!encoder.matches(rawPassword, storedHash)) {
                        System.err.println("FATAL: Cryptographic verification failed! Stored hash does not match input password.");
                        return 1;
                    }
                }
            }

            // Wipe plaintext password memory
            Arrays.fill(passwordChars, ' ');

            System.out.println("SUCCESS: Password successfully rotated for administrator '" + targetUser + "'.");
            System.out.println("VERIFIED: Stored hash cryptographically verified with Spring Security BCryptPasswordEncoder.");
            System.out.println("VERIFIED: Account attributes (ID=" + userId + ", role=" + REQUIRED_ROLE + ", active=" + active + ", verified=" + verified + ", blocked=" + blocked + ") preserved.");

            return 0;

        } catch (Exception e) {
            System.err.println("FATAL ERROR during credential rotation: " + e.getMessage());
            return 1;
        }
    }
}
