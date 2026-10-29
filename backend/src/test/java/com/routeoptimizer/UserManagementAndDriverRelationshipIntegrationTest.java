package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.CreateUserRequest;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
class UserManagementAndDriverRelationshipIntegrationTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        driverRepository.deleteAll();
        userRepository.deleteAll();

        // Admin user
        User admin = User.builder()
                .username("admin")
                .email("admin@test.com")
                .password(passwordEncoder.encode("AdminPass123"))
                .fullName("Admin User")
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        admin.setVersion(0L);
        userRepository.save(admin);
        adminToken = jwtTokenProvider.generateTokenForUsername("admin");

        // Standard user
        User standardUser = User.builder()
                .username("standarduser")
                .email("user@test.com")
                .password(passwordEncoder.encode("UserPass123"))
                .fullName("Standard User")
                .role(Role.USER)
                .build();
        standardUser.setActive(true);
        standardUser.setVersion(0L);
        userRepository.save(standardUser);
        userToken = jwtTokenProvider.generateTokenForUsername("standarduser");
    }

    @AfterEach
    void tearDown() {
        driverRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Admin can create a USER user successfully")
    void adminCanCreateStandardUser() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_user")
                .email("newuser@example.com")
                .password("Password123!")
                .fullName("New User")
                .role(Role.USER)
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new_user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.driverId").doesNotExist());

        assertThat(userRepository.findByUsername("new_user")).isPresent();
    }

    @Test
    @DisplayName("Attempt to create user with DISPATCHER role is rejected (HTTP 400 Bad Request)")
    void createDispatcherUserIsRejected() throws Exception {
        String requestJson = "{\"username\":\"bad_disp\",\"email\":\"baddisp@example.com\",\"password\":\"Password123!\",\"fullName\":\"Bad Disp\",\"role\":\"DISPATCHER\"}";

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unsupported role")));

        assertThat(userRepository.findByUsername("bad_disp")).isEmpty();
    }

    @Test
    @DisplayName("Section 45: Admin creates DRIVER user with Driver profile transactionally")
    void adminCreatesDriverUserWithDriverProfileTransactionally() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("driver_john")
                .email("john.driver@example.com")
                .password("DriverPass123")
                .fullName("John Driver")
                .role(Role.DRIVER)
                .phone("+91-9876543210")
                .licenseNumber("DL-KA-01-2024-0099")
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("driver_john"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.driverId").isNumber());

        // Verify user and driver in database
        Optional<User> userOpt = userRepository.findByUsername("driver_john");
        assertThat(userOpt).isPresent();
        User driverUser = userOpt.get();

        Optional<Driver> driverOpt = driverRepository.findByUserId(driverUser.getId());
        assertThat(driverOpt).isPresent();
        Driver driver = driverOpt.get();
        assertThat(driver.getLicenseNumber()).isEqualTo("DL-KA-01-2024-0099");
        assertThat(driver.getPhone()).isEqualTo("+91-9876543210");
        assertThat(driver.getName()).isEqualTo("John Driver");
    }

    @Test
    @DisplayName("Section 45: DRIVER user without Driver profile license number is rejected with HTTP 400")
    void driverUserWithoutLicenseNumberIsRejected() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("driver_nolicense")
                .email("nolicense@example.com")
                .password("DriverPass123")
                .fullName("No License Driver")
                .role(Role.DRIVER)
                .phone("+91-9876543211")
                // Missing licenseNumber
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Ensure transactional rejection: no user created
        assertThat(userRepository.findByUsername("driver_nolicense")).isEmpty();
    }

    @Test
    @DisplayName("Section 45: Duplicate driver license number is rejected and transaction rolled back")
    void duplicateLicenseNumberIsRejectedAndRolledBack() throws Exception {
        // Create first driver
        CreateUserRequest first = CreateUserRequest.builder()
                .username("driver_first")
                .email("first.driver@example.com")
                .password("DriverPass123")
                .fullName("First Driver")
                .role(Role.DRIVER)
                .licenseNumber("DL-UNIQUE-12345")
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Attempt second driver with identical license number
        CreateUserRequest duplicate = CreateUserRequest.builder()
                .username("driver_second")
                .email("second.driver@example.com")
                .password("DriverPass123")
                .fullName("Second Driver")
                .role(Role.DRIVER)
                .licenseNumber("DL-UNIQUE-12345")
                .build();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Verify second user was rolled back
        assertThat(userRepository.findByUsername("driver_second")).isEmpty();
    }

    @Test
    @DisplayName("Section 44: Non-admin users cannot create users (HTTP 403 Forbidden)")
    void nonAdminCannotCreateUsers() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("another_user")
                .email("another@example.com")
                .password("Password123!")
                .role(Role.USER)
                .build();

        // Standard user attempting to create user
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }
}
