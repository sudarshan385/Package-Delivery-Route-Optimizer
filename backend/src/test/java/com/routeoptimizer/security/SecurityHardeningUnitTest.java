
package com.routeoptimizer.security;

import com.routeoptimizer.config.DatabaseIdentityVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused, fast-mode unit tests for the three approved security fixes:
 * P1 - JWT Signing Key Validation
 * P2 - Script Hardcoded Credentials Removal
 * P3 - Database Identity Validation Fail-Closed Architecture
 *
 * Runs completely in-memory in < 1 second without database connections or network requests.
 */
public class SecurityHardeningUnitTest {

    // =========================================================================
    // P1: JWT SIGNING KEY VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("P1.1 - Rejects null, blank, or whitespace-only JWT secrets")
    void rejectsMissingOrBlankJwtSecret() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or blank");

        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or blank");

        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing or blank");
    }

    @Test
    @DisplayName("P1.2 - Rejects the known default static hex key")
    void rejectsKnownDefaultHexKey() {
        String knownDefault = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey(knownDefault))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Known default signing key is rejected");
    }

    @Test
    @DisplayName("P1.3 - Rejects placeholder keys and common test placeholders")
    void rejectsPlaceholderKeys() {
        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey("${JWT_SECRET}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Placeholder signing key is rejected");

        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey("CHANGE_ME"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Placeholder signing key is rejected");

        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey("YOUR_SECRET_KEY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Placeholder signing key is rejected");
    }

    @Test
    @DisplayName("P1.4 - Rejects keys with insufficient entropy (fewer than 256 bits / 32 bytes)")
    void rejectsInsufficientKeyLength() {
        // 31 characters = 31 bytes in UTF-8 (insufficient for HS256)
        String shortKey = "1234567890123456789012345678901";
        assertThat(shortKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(31);

        assertThatThrownBy(() -> JwtTokenProvider.validateSigningKey(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits (32 bytes)");
    }

    @Test
    @DisplayName("P1.5 - Accepts valid secure key and properly signs and validates token")
    void acceptsValidKeyAndSignsToken() {
        String secureKey = "MyCustomSecureEnterpriseKeyForJwtSigningAtLeast32Bytes!";
        JwtTokenProvider.validateSigningKey(secureKey);

        JwtTokenProvider provider = new JwtTokenProvider(secureKey, 900000L);
        String token = provider.generateTokenForUsername("test_operator");

        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("test_operator");
    }

    // =========================================================================
    // P3: DATABASE IDENTITY VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("P3.1 - Resolves 'packagedeliveryrouteoptimizer' for 'dev' profile")
    void resolvesDevDatabase() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        String expected = DatabaseIdentityVerifier.resolveExpectedDatabaseName(env);
        assertThat(expected).isEqualTo("packagedeliveryrouteoptimizer");

        // Verification succeeds with matching database
        DatabaseIdentityVerifier.verifyDatabaseIdentity("packagedeliveryrouteoptimizer", env, "UNIT-TEST");

        // Verification fails closed on mismatch
        assertThatThrownBy(() -> DatabaseIdentityVerifier.verifyDatabaseIdentity("wrong_db", env, "UNIT-TEST"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database Identity mismatch");
    }

    @Test
    @DisplayName("P3.2 - Resolves 'route_optimizer_test_db' for 'test' profile")
    void resolvesTestDatabase() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        String expected = DatabaseIdentityVerifier.resolveExpectedDatabaseName(env);
        assertThat(expected).isEqualTo("route_optimizer_test_db");

        DatabaseIdentityVerifier.verifyDatabaseIdentity("route_optimizer_test_db", env, "UNIT-TEST");

        assertThatThrownBy(() -> DatabaseIdentityVerifier.verifyDatabaseIdentity("packagedeliveryrouteoptimizer", env, "UNIT-TEST"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database Identity mismatch");
    }

    @Test
    @DisplayName("P3.3 - In 'prod' profile, requires explicit PROD_DB_NAME and fails closed if missing")
    void enforcesProdDatabaseRequirement() {
        MockEnvironment envWithoutProdDb = new MockEnvironment();
        envWithoutProdDb.setActiveProfiles("prod");

        assertThatThrownBy(() -> DatabaseIdentityVerifier.resolveExpectedDatabaseName(envWithoutProdDb))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Profile 'prod' requires 'PROD_DB_NAME'");

        // Configured prod environment
        MockEnvironment envWithProdDb = new MockEnvironment();
        envWithProdDb.setActiveProfiles("prod");
        envWithProdDb.setProperty("PROD_DB_NAME", "production_route_optimizer_db");

        String expected = DatabaseIdentityVerifier.resolveExpectedDatabaseName(envWithProdDb);
        assertThat(expected).isEqualTo("production_route_optimizer_db");

        DatabaseIdentityVerifier.verifyDatabaseIdentity("production_route_optimizer_db", envWithProdDb, "UNIT-TEST");

        assertThatThrownBy(() -> DatabaseIdentityVerifier.verifyDatabaseIdentity("packagedeliveryrouteoptimizer", envWithProdDb, "UNIT-TEST"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database Identity mismatch");
    }

    @Test
    @DisplayName("P3.4 - Fails closed under default or unconfigured profiles without explicit configuration")
    void failsClosedUnderDefaultProfile() {
        MockEnvironment defaultEnv = new MockEnvironment(); // No active profiles

        assertThatThrownBy(() -> DatabaseIdentityVerifier.resolveExpectedDatabaseName(defaultEnv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No supported active profile (dev, test, prod) is set");

        // Custom profile also requires explicit configuration
        MockEnvironment customEnv = new MockEnvironment();
        customEnv.setActiveProfiles("staging");

        assertThatThrownBy(() -> DatabaseIdentityVerifier.resolveExpectedDatabaseName(customEnv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No supported active profile");

        // Configured fallback via EXPECTED_DB_NAME
        customEnv.setProperty("EXPECTED_DB_NAME", "staging_route_optimizer_db");
        assertThat(DatabaseIdentityVerifier.resolveExpectedDatabaseName(customEnv)).isEqualTo("staging_route_optimizer_db");
    }

    // =========================================================================
    // P2: SCRIPT CREDENTIALS STATIC SANITIZATION CHECKS
    // =========================================================================

    @Test
    @DisplayName("P2.1 - Verifies that maintenance and setup scripts do not contain hardcoded credentials")
    void verifyScriptsContainNoHardcodedCredentials() throws Exception {
        List<String> scriptPaths = List.of(
                "../../scripts/maintenance/db-update-password.ps1",
                "../../scripts/database/setup_dev_db.bat",
                "../../scripts/database/setup_new_dev_db.bat",
                "../../scripts/database/restore-verify.bat",
                "../../scripts/maintenance/backup.bat",
                "../../scripts/maintenance/run-backup.bat",
                "../../scripts/diagnostics/query_db.bat",
                "../../scripts/diagnostics/inspect-route-locations.ps1",
                "../../scripts/diagnostics/inspect-current-routes.ps1",
                "../../scripts/diagnostics/db-query-users.ps1",
                "../../scripts/diagnostics/db-query-users-old.ps1",
                "../../scripts/diagnostics/db-queries.ps1",
                "../../scripts/diagnostics/db-queries2.ps1",
                "../../scripts/diagnostics/check-prod-db.ps1",
                "../../scripts/diagnostics/check-edges-schema.ps1",
                "../../scripts/diagnostics/check-counts.ps1",
                "../../backend/scripts/inspect-entities-readonly.ps1"
        );

        for (String relativePath : scriptPaths) {
            File file = new File(relativePath);
            if (file.exists()) {
                String content = Files.readString(file.toPath());
                // Verify sensitive assignments were removed
                assertThat(content)
                        .withFailMessage("Script %s still contains hardcoded app_password assignment!", file.getName())
                        .doesNotContain("PGPASSWORD=\"app_password\"")
                        .doesNotContain("PGPASSWORD = \"app_password\"")
                        .doesNotContain("PGPASSWORD = 'app_password'")
                        .doesNotContain("PGPASSWORD=app_password");

                assertThat(content)
                        .withFailMessage("Script %s still contains hardcoded root password assignment!", file.getName())
                        .doesNotContain("PGPASSWORD=\"root\"")
                        .doesNotContain("PGPASSWORD = \"root\"")
                        .doesNotContain("PGPASSWORD=root");

                assertThat(content)
                        .withFailMessage("Script %s still contains hardcoded test password assignment!", file.getName())
                        .doesNotContain("PGPASSWORD=\"test_password\"");
            }
        }
    }
}
