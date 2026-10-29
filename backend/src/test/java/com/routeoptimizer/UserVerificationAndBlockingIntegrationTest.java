package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.security.JwtTokenProvider;
import com.routeoptimizer.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UserVerificationAndBlockingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin1;
    private User admin2;
    private User regularUser;
    private String admin1Token;
    private String admin2Token;
    private String userToken;

    @BeforeEach
    void setUp() {
        // Enforce test database safeguard before destructive operations
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }

        driverRepository.deleteAll();
        userRepository.deleteAll();

        // Seed Admin 1 (Usable Admin)
        admin1 = User.builder()
                .username("primary_admin")
                .email("admin1@test.com")
                .password(passwordEncoder.encode("AdminPass123"))
                .fullName("Primary Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build();
        admin1.setActive(true);
        admin1.setVersion(0L);
        admin1 = userRepository.save(admin1);

        // Seed Admin 2 (Secondary Usable Admin)
        admin2 = User.builder()
                .username("secondary_admin")
                .email("admin2@test.com")
                .password(passwordEncoder.encode("AdminPass123"))
                .fullName("Secondary Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build();
        admin2.setActive(true);
        admin2.setVersion(0L);
        admin2 = userRepository.save(admin2);

        // Seed Standard User (unverified, unblocked)
        regularUser = User.builder()
                .username("johndoe")
                .email("john@test.com")
                .password(passwordEncoder.encode("UserPass123"))
                .fullName("John Doe")
                .role(Role.USER)
                .isVerified(false)
                .isBlocked(false)
                .build();
        regularUser.setActive(true);
        regularUser.setVersion(0L);
        regularUser = userRepository.save(regularUser);

        admin1Token = jwtTokenProvider.generateTokenForUsername(admin1.getUsername());
        admin2Token = jwtTokenProvider.generateTokenForUsername(admin2.getUsername());
        userToken = jwtTokenProvider.generateTokenForUsername(regularUser.getUsername());
    }

    @Test
    @DisplayName("Migration defaults and entity defaults properly initialize flags")
    void testMigrationAndEntityDefaults() {
        User defaultUser = User.builder()
                .username("default_user")
                .email("default@test.com")
                .password(passwordEncoder.encode("Pass123456"))
                .fullName("Default User")
                .role(Role.USER)
                .build();
        defaultUser.setActive(true);
        defaultUser.setVersion(0L);
        defaultUser = userRepository.save(defaultUser);

        assertThat(defaultUser.getIsVerified()).isFalse();
        assertThat(defaultUser.getIsBlocked()).isFalse();
        assertThat(defaultUser.getActive()).isTrue();
    }

    @Test
    @DisplayName("Admin can verify and unverify an eligible user independently of other states")
    void testVerifyAndUnverifyUser() throws Exception {
        // 1. Verify John Doe
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/verify")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId()))
                .andExpect(jsonPath("$.isVerified").value(true))
                .andExpect(jsonPath("$.isBlocked").value(false))
                .andExpect(jsonPath("$.active").value(true));

        User updatedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(updatedUser.getIsVerified()).isTrue();
        assertThat(updatedUser.getIsBlocked()).isFalse();

        // 2. Unverify John Doe
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/unverify")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified").value(false))
                .andExpect(jsonPath("$.isBlocked").value(false))
                .andExpect(jsonPath("$.active").value(true));

        updatedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(updatedUser.getIsVerified()).isFalse();
        assertThat(updatedUser.getIsBlocked()).isFalse();
        assertThat(updatedUser.getActive()).isTrue();
    }

    @Test
    @DisplayName("Admin can block and unblock a user independently of verification")
    void testBlockAndUnblockUser() throws Exception {
        // 1. Verify John Doe first
        regularUser.setIsVerified(true);
        userRepository.save(regularUser);

        // 2. Block John Doe
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/block")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(true))
                .andExpect(jsonPath("$.isVerified").value(true)) // verification remains intact
                .andExpect(jsonPath("$.active").value(true));

        User updatedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(updatedUser.getIsBlocked()).isTrue();
        assertThat(updatedUser.getIsVerified()).isTrue();

        // 3. Unblock John Doe
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/unblock")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isBlocked").value(false))
                .andExpect(jsonPath("$.isVerified").value(true));

        updatedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(updatedUser.getIsBlocked()).isFalse();
        assertThat(updatedUser.getIsVerified()).isTrue();
    }

    @Test
    @DisplayName("Blocked user login is rejected, and succeeds after unblocking")
    void testBlockedUserLoginRejectionAndRecovery() throws Exception {
        // Block user
        regularUser.setIsBlocked(true);
        userRepository.save(regularUser);

        // Attempt login
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("johndoe");
        loginReq.setPassword("UserPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());

        // Unblock user via admin endpoint
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/unblock")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // Attempt login again — must succeed
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    @Test
    @DisplayName("Rejection of existing valid JWT immediately after account is blocked")
    void testImmediateJwtInvalidationOnBlocking() throws Exception {
        // User accesses profile endpoint with existing token
        mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"));

        // Admin blocks user
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/block")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // User immediately repeats request with same token — must be rejected
        mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // Admin unblocks user
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/unblock")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // User repeats request with same token — must succeed again
        mockMvc.perform(get("/api/users/profile")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"));
    }

    @Test
    @DisplayName("Endpoints enforce ADMIN-only authorization and reject USER/DRIVER/Anonymous access")
    void testEndpointAuthorizationRoles() throws Exception {
        // Regular user cannot verify or block
        mockMvc.perform(put("/api/users/" + admin2.getId() + "/verify")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/" + admin2.getId() + "/block")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // Anonymous cannot access admin endpoints
        mockMvc.perform(put("/api/users/" + admin2.getId() + "/verify"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/users/" + admin2.getId() + "/block"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Search, filters, and pagination functionality")
    void testSearchFiltersAndPagination() throws Exception {
        // Query users with search parameter
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + admin1Token)
                        .param("search", "john")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("johndoe"));

        // Filter by role = ADMIN
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + admin1Token)
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Filter by verification = true
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + admin1Token)
                        .param("isVerified", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Pagination page size limit
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + admin1Token)
                        .param("page", "0")
                        .param("size", "2")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$..totalElements").value(hasItem(3)))
                .andExpect(jsonPath("$..totalPages").value(hasItem(2)));
    }

    @Test
    @DisplayName("Prevent administrator from blocking or unverifying their own account")
    void testSelfLockoutPrevention() throws Exception {
        // Admin1 attempts to block Admin1
        mockMvc.perform(put("/api/users/" + admin1.getId() + "/block")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot block their own account")));

        // Admin1 attempts to unverify Admin1
        mockMvc.perform(put("/api/users/" + admin1.getId() + "/unverify")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot unverify their own account")));
    }

    @Test
    @DisplayName("Prevent blocking or unverifying the last usable ADMIN account")
    void testLastUsableAdminProtection() throws Exception {
        // Admin1 blocks Admin2 (leaving Admin1 as the ONLY usable admin)
        mockMvc.perform(put("/api/users/" + admin2.getId() + "/block")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // Unblock Admin2 with Admin1
        mockMvc.perform(put("/api/users/" + admin2.getId() + "/unblock")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // Now Admin1 unverifies Admin2
        mockMvc.perform(put("/api/users/" + admin2.getId() + "/unverify")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk());

        // At this point:
        // Admin1 is verified, active, unblocked (1 usable admin)
        // Admin2 is unverified, active, unblocked (NOT usable admin)
        // Now Admin2 (or any other session) attempts to unverify or block Admin1 -> Must fail as last usable admin
        mockMvc.perform(put("/api/users/" + admin1.getId() + "/block")
                        .header("Authorization", "Bearer " + admin2Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("last usable administrator account")));

        mockMvc.perform(put("/api/users/" + admin1.getId() + "/unverify")
                        .header("Authorization", "Bearer " + admin2Token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("last usable administrator account")));
    }

    @Test
    @DisplayName("Preserve password hash and credential confidentiality across actions")
    void testCredentialConfidentialityAndDataIntegrity() throws Exception {
        String originalPasswordHash = regularUser.getPassword();

        // Perform verify action
        mockMvc.perform(put("/api/users/" + regularUser.getId() + "/verify")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isOk())
                // Ensure password is not present in UserResponse
                .andExpect(jsonPath("$.password").doesNotExist());

        // Check database
        User postVerifyUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(postVerifyUser.getPassword()).isEqualTo(originalPasswordHash);
        assertThat(postVerifyUser.getId()).isEqualTo(regularUser.getId());
        assertThat(postVerifyUser.getEmail()).isEqualTo(regularUser.getEmail());
    }

    @Test
    @DisplayName("Concurrent attempts to block or unverify administrators maintain last admin safeguard")
    void testConcurrentAdminSafeguard() throws Exception {
        // admin1 and admin2 are both usable admins
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        // Attempt 1: Admin1 blocks Admin2
        Callable<Integer> task1 = () -> {
            latch.await();
            return mockMvc.perform(put("/api/users/" + admin2.getId() + "/block")
                            .header("Authorization", "Bearer " + admin1Token))
                    .andReturn().getResponse().getStatus();
        };

        // Attempt 2: Admin2 blocks Admin1
        Callable<Integer> task2 = () -> {
            latch.await();
            return mockMvc.perform(put("/api/users/" + admin1.getId() + "/block")
                            .header("Authorization", "Bearer " + admin2Token))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        latch.countDown(); // Release both tasks simultaneously

        int status1 = f1.get(10, TimeUnit.SECONDS);
        int status2 = f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed (200), and the other must fail (either 400 Bad Request via the last-admin guard, or 401 Unauthorized if the blocked user's token is immediately rejected)
        List<Integer> statuses = List.of(status1, status2);
        assertThat(statuses).contains(200);
        assertThat(statuses).anyMatch(status -> status == 400 || status == 401);

        // Verify that at least one usable admin still exists in the database
        long usableAdmins = userRepository.countUsableAdmins(Role.ADMIN);
        assertThat(usableAdmins).isGreaterThanOrEqualTo(1);
    }
}
