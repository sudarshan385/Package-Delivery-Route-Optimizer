package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationAndJwtIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.routeoptimizer.security.RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");

        // Create an active test user
        User user = User.builder()
                .username("testuser")
                .email("testuser@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .fullName("Test User")
                .role(Role.ADMIN)
                .build();
        user.setActive(true);
        user.setVersion(0L);
        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Successful login returns HTTP 200 with valid JWT access token")
    void successfulLoginReturnsJwtToken() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.expiresInMs").value(jwtTokenProvider.getExpirationMs()));
    }

    @Test
    @DisplayName("Case-insensitive username login returns HTTP 200 with valid JWT access token")
    void loginWithCaseInsensitiveUsernameSucceeds() throws Exception {
        // testuser was registered as lowercase 'testuser'. Attempt login with mixed/uppercase 'TestUser' and 'TESTUSER'
        LoginRequest mixedCase = LoginRequest.builder()
                .username("TestUser")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mixedCase)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));

        LoginRequest upperCase = LoginRequest.builder()
                .username("TESTUSER")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upperCase)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("Email-based login returns HTTP 200 with valid JWT access token")
    void loginWithEmailSucceeds() throws Exception {
        LoginRequest emailRequest = LoginRequest.builder()
                .username("testuser@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));

        LoginRequest upperEmailRequest = LoginRequest.builder()
                .username("TESTUSER@EXAMPLE.COM")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upperEmailRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @DisplayName("Invalid password returns HTTP 401 Unauthorized with Problem Details")
    void invalidPasswordReturns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("WrongPassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Non-existent user returns HTTP 401 Unauthorized")
    void unknownUserReturns401() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("nonexistent")
                .password("SomePassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Accessing protected endpoint without token returns HTTP 401 Unauthorized")
    void unauthorizedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/matrix-check/view-dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Accessing protected endpoint with valid JWT token succeeds")
    void authorizedAccessWithJwtTokenSucceeds() throws Exception {
        String token = jwtTokenProvider.generateTokenForUsername("testuser");

        mockMvc.perform(get("/api/matrix-check/view-dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOWED"));
    }

    @Test
    @DisplayName("Invalid or malformed token returns HTTP 401 Unauthorized")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/matrix-check/view-dashboard")
                        .header("Authorization", "Bearer InvalidMalformedToken12345"))
                .andExpect(status().isUnauthorized());
    }
}
