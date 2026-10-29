package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.CreateUserRequest;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.dto.RegisterRequest;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.security.JwtTokenProvider;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class AdminCreationAndRotationIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String primaryAdminToken;
    private String userToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        driverRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Primary Administrator
        User primaryAdmin = User.builder()
                .username("primary_admin")
                .email("primary.admin@test.com")
                .password(passwordEncoder.encode("PrimaryAdminPass123!"))
                .fullName("Primary Administrator")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build();
        primaryAdmin.setActive(true);
        primaryAdmin.setVersion(0L);
        userRepository.save(primaryAdmin);
        primaryAdminToken = jwtTokenProvider.generateTokenForUsername("primary_admin");

        // 2. Standard User
        User standardUser = User.builder()
                .username("standard_user")
                .email("standard.user@test.com")
                .password(passwordEncoder.encode("StandardUserPass123!"))
                .fullName("Standard User")
                .role(Role.USER)
                .isVerified(false)
                .isBlocked(false)
                .build();
        standardUser.setActive(true);
        standardUser.setVersion(0L);
        userRepository.save(standardUser);
        userToken = jwtTokenProvider.generateTokenForUsername("standard_user");

        // 3. Driver User
        User driverUser = User.builder()
                .username("driver_user")
                .email("driver.user@test.com")
                .password(passwordEncoder.encode("DriverUserPass123!"))
                .fullName("Driver User")
                .role(Role.DRIVER)
                .isVerified(false)
                .isBlocked(false)
                .build();
        driverUser.setActive(true);
        driverUser.setVersion(0L);
        User savedDriverUser = userRepository.save(driverUser);

        Driver driverProfile = Driver.builder()
                .user(savedDriverUser)
                .name("Driver User")
                .phone("+91-9876500000")
                .licenseNumber("DL-TEST-99999")
                .status(com.routeoptimizer.enums.DriverStatus.AVAILABLE)
                .build();
        driverProfile.setActive(true);
        driverProfile.setVersion(0L);
        driverRepository.save(driverProfile);

        driverToken = jwtTokenProvider.generateTokenForUsername("driver_user");
    }

    @Test
    @DisplayName("1. Authenticated ADMIN can create a new ADMIN with database-generated ID and BCrypt password")
    void adminCanCreateNewAdminSuccessfully() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("secondary_admin")
                .email("secondary.admin@test.com")
                .password("SecAdminPass123!")
                .fullName("Secondary Admin")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + primaryAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username", is("secondary_admin")))
                .andExpect(jsonPath("$.email", is("secondary.admin@test.com")))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.isVerified", is(true)))
                .andExpect(jsonPath("$.isBlocked", is(false)))
                // Verify password and hashes are NEVER returned in response
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify database state directly
        Optional<User> createdAdminOpt = userRepository.findByUsername("secondary_admin");
        assertThat(createdAdminOpt).isPresent();
        User createdAdmin = createdAdminOpt.get();
        assertThat(createdAdmin.getId()).isNotNull().isPositive();
        assertThat(createdAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(createdAdmin.getActive()).isTrue();
        assertThat(createdAdmin.getIsVerified()).isTrue();
        assertThat(createdAdmin.getIsBlocked()).isFalse();

        // Verify password is stored strictly as BCrypt hash matching raw password
        assertThat(createdAdmin.getPassword()).startsWith("$2a$10$");
        assertThat(createdAdmin.getPassword()).hasSize(60);
        assertThat(passwordEncoder.matches("SecAdminPass123!", createdAdmin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword!", createdAdmin.getPassword())).isFalse();
    }

    @Test
    @DisplayName("2. Newly created ADMIN can immediately authenticate via login endpoint and access protected resources")
    void newlyCreatedAdminCanLoginAndAccessProtectedResources() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("login_admin")
                .email("login.admin@test.com")
                .password("LoginAdminPass123!")
                .fullName("Login Admin")
                .role(Role.ADMIN)
                .build();

        // Create admin
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + primaryAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Login as new admin
        LoginRequest loginRequest = new LoginRequest("login_admin", "LoginAdminPass123!");
        String loginResponseJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.username", is("login_admin")))
                .andReturn().getResponse().getContentAsString();

        com.routeoptimizer.dto.AuthResponse authResponse = objectMapper.readValue(loginResponseJson, com.routeoptimizer.dto.AuthResponse.class);
        String newAdminToken = authResponse.getAccessToken();

        // Access protected ADMIN endpoint using new token
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + newAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("3. Duplicate username and email requests are rejected with HTTP 409 Conflict")
    void duplicateUsernameAndEmailAreRejected() throws Exception {
        // Attempt duplicate username
        CreateUserRequest dupUserReq = CreateUserRequest.builder()
                .username("primary_admin") // Existing username
                .email("unique.email@test.com")
                .password("Password123!")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + primaryAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupUserReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("primary_admin")));

        // Attempt duplicate email
        CreateUserRequest dupEmailReq = CreateUserRequest.builder()
                .username("unique_user")
                .email("primary.admin@test.com") // Existing email
                .password("Password123!")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + primaryAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupEmailReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("primary.admin@test.com")));
    }

    @Test
    @DisplayName("4. Standard USER and DRIVER cannot create administrators (HTTP 403 Forbidden)")
    void userAndDriverCannotCreateAdministrators() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("rogue_admin")
                .email("rogue.admin@test.com")
                .password("RogueAdminPass123!")
                .role(Role.ADMIN)
                .build();

        // USER attempt
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // DRIVER attempt
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByUsername("rogue_admin")).isEmpty();
    }

    @Test
    @DisplayName("5. Public registration endpoint strictly rejects attempts to register as ADMIN")
    void publicRegistrationCannotGrantAdminPrivileges() throws Exception {
        RegisterRequest registerReq = RegisterRequest.builder()
                .username("public_admin")
                .email("public.admin@test.com")
                .password("PublicAdminPass123!")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByUsername("public_admin")).isEmpty();
    }

    @Test
    @DisplayName("6. Password rotation utility successfully updates password and cryptographically verifies persisted hash")
    void passwordRotatorSuccessfullyUpdatesAndVerifies() {
        // Enforce test database guard
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        String initialPass = "PrimaryAdminPass123!";
        String rotatedPass = "RotatedAdminPass999!";
        String testUser = "primary_admin";

        User adminBefore = userRepository.findByUsername(testUser).orElseThrow();
        Long originalId = adminBefore.getId();
        String originalEmail = adminBefore.getEmail();
        Role originalRole = adminBefore.getRole();
        boolean originalActive = adminBefore.getActive();
        boolean originalVerified = adminBefore.getIsVerified();
        boolean originalBlocked = adminBefore.getIsBlocked();

        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();

        int exitCode;
        try {
            System.setIn(new ByteArrayInputStream((rotatedPass + "\n").getBytes()));
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            exitCode = com.routeoptimizer.tools.AdminPasswordRotator.executeRotation(new String[]{
                    "--username", testUser,
                    "--database", "route_optimizer_test_db",
                    "--stdin",
                    "--force"
            }, false);
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
            System.setErr(origErr);
        }

        assertThat(exitCode).isEqualTo(0);

        // Verify success output
        String output = outContent.toString();
        assertThat(output).contains("SUCCESS: Password successfully rotated for administrator 'primary_admin'.");
        assertThat(output).contains("VERIFIED: Stored hash cryptographically verified with Spring Security BCryptPasswordEncoder.");

        // Verify database state: password changed, all other fields preserved
        User adminAfter = userRepository.findByUsername(testUser).orElseThrow();
        assertThat(adminAfter.getId()).isEqualTo(originalId);
        assertThat(adminAfter.getEmail()).isEqualTo(originalEmail);
        assertThat(adminAfter.getRole()).isEqualTo(originalRole);
        assertThat(adminAfter.getActive()).isEqualTo(originalActive);
        assertThat(adminAfter.getIsVerified()).isEqualTo(originalVerified);
        assertThat(adminAfter.getIsBlocked()).isEqualTo(originalBlocked);

        assertThat(passwordEncoder.matches(rotatedPass, adminAfter.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(initialPass, adminAfter.getPassword())).isFalse();
    }

    @Test
    @DisplayName("7. Password rotation handles cancellation without changing credentials or claiming success")
    void passwordRotatorHandlesCancellationSafely() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        String testUser = "primary_admin";
        User adminBefore = userRepository.findByUsername(testUser).orElseThrow();
        String originalHash = adminBefore.getPassword();

        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();

        int exitCode;
        try {
            // Operator enters new password then cancels on confirmation prompt ('n')
            String simulatedInteractiveInput = "NewSecret123!\nn\n";
            System.setIn(new ByteArrayInputStream(simulatedInteractiveInput.getBytes()));
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            exitCode = com.routeoptimizer.tools.AdminPasswordRotator.executeRotation(new String[]{
                    "--username", testUser,
                    "--database", "route_optimizer_test_db"
                    // No --force, no --stdin: triggers cancellation logic
            }, false);
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
            System.setErr(origErr);
        }

        // Must return non-zero exit code on cancellation
        assertThat(exitCode).isEqualTo(2);

        // Output must NEVER claim success
        String stdout = outContent.toString();
        String stderr = errContent.toString();
        assertThat(stdout).doesNotContain("SUCCESS:");
        assertThat(stderr).contains("OPERATION CANCELLED");

        // Verify database credential was strictly untouched
        User adminAfter = userRepository.findByUsername(testUser).orElseThrow();
        assertThat(adminAfter.getPassword()).isEqualTo(originalHash);
        assertThat(passwordEncoder.matches("PrimaryAdminPass123!", adminAfter.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("NewSecret123!", adminAfter.getPassword())).isFalse();
    }

    @Test
    @DisplayName("8. Password rotation handles EOF or empty stdin stream with error and zero credential alteration")
    void passwordRotatorHandlesEmptyStdinSafely() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        String testUser = "primary_admin";
        User adminBefore = userRepository.findByUsername(testUser).orElseThrow();
        String originalHash = adminBefore.getPassword();

        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();

        int exitCode;
        try {
            // Empty stdin stream (immediate EOF)
            System.setIn(new ByteArrayInputStream(new byte[0]));
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            exitCode = com.routeoptimizer.tools.AdminPasswordRotator.executeRotation(new String[]{
                    "--username", testUser,
                    "--database", "route_optimizer_test_db",
                    "--stdin",
                    "--force"
            }, false);
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
            System.setErr(origErr);
        }

        assertThat(exitCode).isEqualTo(1);
        assertThat(outContent.toString()).doesNotContain("SUCCESS:");
        assertThat(errContent.toString()).contains("FATAL: Standard input stream was empty");

        User adminAfter = userRepository.findByUsername(testUser).orElseThrow();
        assertThat(adminAfter.getPassword()).isEqualTo(originalHash);
    }

    @Test
    @DisplayName("9. Password rotation rejects unauthorized target databases and passwords shorter than 8 characters")
    void passwordRotatorRejectsInvalidDatabaseAndShortPasswords() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        String testUser = "primary_admin";
        User adminBefore = userRepository.findByUsername(testUser).orElseThrow();
        String originalHash = adminBefore.getPassword();

        // Database safety violation test
        int dbExitCode = com.routeoptimizer.tools.AdminPasswordRotator.executeRotation(new String[]{
                "--username", testUser,
                "--database", "unauthorized_production_db",
                "--stdin",
                "--force"
        }, false);
        assertThat(dbExitCode).isEqualTo(1);

        // Short password test
        InputStream origIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream("short\n".getBytes()));
            int shortPassCode = com.routeoptimizer.tools.AdminPasswordRotator.executeRotation(new String[]{
                    "--username", testUser,
                    "--database", "route_optimizer_test_db",
                    "--stdin",
                    "--force"
            }, false);
            assertThat(shortPassCode).isEqualTo(1);
        } finally {
            System.setIn(origIn);
        }

        User adminAfter = userRepository.findByUsername(testUser).orElseThrow();
        assertThat(adminAfter.getPassword()).isEqualTo(originalHash);
    }
}
