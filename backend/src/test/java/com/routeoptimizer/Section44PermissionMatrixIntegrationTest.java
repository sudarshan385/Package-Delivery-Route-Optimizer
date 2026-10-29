package com.routeoptimizer;

import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Section44PermissionMatrixIntegrationTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;
    private String driverToken;

    @BeforeAll
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        driverRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Admin user
        User admin = User.builder()
                .username("admin_matrix")
                .email("admin@matrix.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        admin.setVersion(0L);
        userRepository.save(admin);
        adminToken = jwtTokenProvider.generateTokenForUsername("admin_matrix");

        // 2. Standard user
        User user = User.builder()
                .username("user_matrix")
                .email("user@matrix.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.USER)
                .build();
        user.setActive(true);
        user.setVersion(0L);
        userRepository.save(user);
        userToken = jwtTokenProvider.generateTokenForUsername("user_matrix");

        // 3. Driver user + profile
        User driverUser = User.builder()
                .username("driver_matrix")
                .email("driver@matrix.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.DRIVER)
                .build();
        driverUser.setActive(true);
        driverUser.setVersion(0L);
        User savedDriverUser = userRepository.save(driverUser);

        Driver driver = Driver.builder()
                .user(savedDriverUser)
                .name("Driver Matrix")
                .licenseNumber("DL-MATRIX-001")
                .status(DriverStatus.AVAILABLE)
                .build();
        driver.setActive(true);
        driver.setVersion(0L);
        driverRepository.save(driver);
        driverToken = jwtTokenProvider.generateTokenForUsername("driver_matrix");
    }

    @AfterAll
    void tearDown() {
        driverRepository.deleteAll();
        userRepository.deleteAll();
    }

    @ParameterizedTest(name = "Operation: {0}, Role: {1}, ExpectedStatus: {2}")
    @CsvSource({
            // Endpoint, Role, Expected HTTP Status
            // 1. Create users: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/create-users, ADMIN, 200",
            "/api/matrix-check/create-users, DRIVER, 403",
            "/api/matrix-check/create-users, USER, 403",

            // 2. Manage user roles: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-roles, ADMIN, 200",
            "/api/matrix-check/manage-roles, DRIVER, 403",
            "/api/matrix-check/manage-roles, USER, 403",

            // 3. View dashboard: ADMIN: YES, DRIVER: YES, USER: NO
            "/api/matrix-check/view-dashboard, ADMIN, 200",
            "/api/matrix-check/view-dashboard, DRIVER, 200",
            "/api/matrix-check/view-dashboard, USER, 403",

            // 4. Manage locations: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-locations, ADMIN, 200",
            "/api/matrix-check/manage-locations, DRIVER, 403",
            "/api/matrix-check/manage-locations, USER, 403",

            // 5. Manage roads/edges: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-roads, ADMIN, 200",
            "/api/matrix-check/manage-roads, DRIVER, 403",
            "/api/matrix-check/manage-roads, USER, 403",

            // 6. Manage packages: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-packages, ADMIN, 200",
            "/api/matrix-check/manage-packages, DRIVER, 403",
            "/api/matrix-check/manage-packages, USER, 403",

            // 7. Manage drivers: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-drivers, ADMIN, 200",
            "/api/matrix-check/manage-drivers, DRIVER, 403",
            "/api/matrix-check/manage-drivers, USER, 403",

            // 8. Manage vehicles: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/manage-vehicles, ADMIN, 200",
            "/api/matrix-check/manage-vehicles, DRIVER, 403",
            "/api/matrix-check/manage-vehicles, USER, 403",

            // 9. Create deliveries: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/create-deliveries, ADMIN, 200",
            "/api/matrix-check/create-deliveries, DRIVER, 403",
            "/api/matrix-check/create-deliveries, USER, 403",

            // 10. Assign driver/vehicle: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/assign-resources, ADMIN, 200",
            "/api/matrix-check/assign-resources, DRIVER, 403",
            "/api/matrix-check/assign-resources, USER, 403",

            // 11. Optimize route: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/optimize-route, ADMIN, 200",
            "/api/matrix-check/optimize-route, DRIVER, 403",
            "/api/matrix-check/optimize-route, USER, 403",

            // 12. Re-optimize route: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/reoptimize-route, ADMIN, 200",
            "/api/matrix-check/reoptimize-route, DRIVER, 403",
            "/api/matrix-check/reoptimize-route, USER, 403",

            // 13. View all routes: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/view-all-routes, ADMIN, 200",
            "/api/matrix-check/view-all-routes, DRIVER, 403",
            "/api/matrix-check/view-all-routes, USER, 403",

            // 14. View assigned routes: ADMIN: YES, DRIVER: YES, USER: NO
            "/api/matrix-check/view-assigned-routes, ADMIN, 200",
            "/api/matrix-check/view-assigned-routes, DRIVER, 200",
            "/api/matrix-check/view-assigned-routes, USER, 403",

            // 15. Start own delivery: ADMIN: YES, DRIVER: YES, USER: NO
            "/api/matrix-check/start-own-delivery, ADMIN, 200",
            "/api/matrix-check/start-own-delivery, DRIVER, 200",
            "/api/matrix-check/start-own-delivery, USER, 403",

            // 16. Complete own delivery: ADMIN: YES, DRIVER: YES, USER: NO
            "/api/matrix-check/complete-own-delivery, ADMIN, 200",
            "/api/matrix-check/complete-own-delivery, DRIVER, 200",
            "/api/matrix-check/complete-own-delivery, USER, 403",

            // 17. Mark own delivery failed: ADMIN: YES, DRIVER: YES, USER: NO
            "/api/matrix-check/mark-own-delivery-failed, ADMIN, 200",
            "/api/matrix-check/mark-own-delivery-failed, DRIVER, 200",
            "/api/matrix-check/mark-own-delivery-failed, USER, 403",

            // 18. Cancel delivery: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/cancel-delivery, ADMIN, 200",
            "/api/matrix-check/cancel-delivery, DRIVER, 403",
            "/api/matrix-check/cancel-delivery, USER, 403",

            // 19. View analytics: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/view-analytics, ADMIN, 200",
            "/api/matrix-check/view-analytics, DRIVER, 403",
            "/api/matrix-check/view-analytics, USER, 403",

            // 20. Generate reports: ADMIN: YES, DRIVER: NO, USER: NO
            "/api/matrix-check/generate-reports, ADMIN, 200",
            "/api/matrix-check/generate-reports, DRIVER, 403",
            "/api/matrix-check/generate-reports, USER, 403"
    })
    @DisplayName("Verify Section 44 Permission Matrix for each operation and role")
    void testPermissionMatrix(String endpoint, String role, int expectedStatus) throws Exception {
        String token = switch (role) {
            case "ADMIN" -> adminToken;
            case "DRIVER" -> driverToken;
            case "USER" -> userToken;
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };

        mockMvc.perform(get(endpoint)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("Verify DISPATCHER authority has no access to operational endpoints (403 Forbidden)")
    @WithMockUser(username = "legacy_dispatcher", roles = "DISPATCHER")
    void testDispatcherAuthorityDenied() throws Exception {
        mockMvc.perform(get("/api/matrix-check/manage-packages"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/matrix-check/manage-locations"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/matrix-check/manage-roads"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/matrix-check/optimize-route"))
                .andExpect(status().isForbidden());
    }
}
