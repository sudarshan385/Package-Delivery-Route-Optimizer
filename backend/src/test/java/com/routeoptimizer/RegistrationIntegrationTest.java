package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.RegisterRequest;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routeoptimizer.repository.DriverRepository;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
public class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private com.routeoptimizer.security.RateLimiterService rateLimiterService;

    @BeforeEach
    public void setup() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
        driverRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testUserRegistrationSucceedsAndCanLogin() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("testuser1")
                .email("testuser1@test.com")
                .password("password123")
                .role(Role.USER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists());

        // Login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"testuser1\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    public void testDriverRegistrationSucceedsAndCanLogin() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("testdriver1")
                .email("testdriver1@test.com")
                .password("password123")
                .role(Role.DRIVER)
                .phone("555-1234")
                .licenseNumber("LIC-1234")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"testdriver1\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    public void testDispatcherRegistrationIsRejected() throws Exception {
        String reqJson = "{\"username\": \"testdisp1\", \"email\": \"testdisp1@test.com\", \"password\": \"password123\", \"role\": \"DISPATCHER\"}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unsupported role")));
    }

    @Test
    public void testAdminRegistrationThroughNormalEndpointFails() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("testadmin1")
                .email("testadmin1@test.com")
                .password("password123")
                .role(Role.ADMIN)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testFirstAdminSetupSucceedsWhenNoAdminExists() throws Exception {
        // DB is empty in setup()
        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin1\", \"email\": \"admin1@test.com\", \"password\": \"password123!\", \"fullName\": \"Admin\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    public void testFirstAdminSetupFailsWhenAdminAlreadyExists() throws Exception {
        // DB is empty, create first admin
        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin1\", \"email\": \"admin1@test.com\", \"password\": \"password123!\", \"fullName\": \"Admin\"}"))
                .andExpect(status().isCreated());

        // Try second admin
        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin2\", \"email\": \"admin2@test.com\", \"password\": \"password123!\", \"fullName\": \"Admin2\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void testDuplicateEmailRegistrationFails() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("userA")
                .email("duplicate@test.com")
                .password("password123")
                .role(Role.USER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        RegisterRequest req2 = RegisterRequest.builder()
                .username("userB")
                .email("duplicate@test.com")
                .password("password123")
                .role(Role.USER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }
}
