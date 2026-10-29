package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.SetupRequest;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
class InitialAdminSetupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("Initial setup succeeds on empty users table and creates ADMIN with BCrypt password")
    void initialSetupSucceedsOnEmptyUsersTable() throws Exception {
        // 1. Verify status endpoint reports setupRequired = true
        mockMvc.perform(get("/api/auth/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));

        // 2. Perform initial admin setup
        SetupRequest request = SetupRequest.builder()
                .username("systemadmin")
                .email("admin@routeoptimizer.com")
                .password("AdminSecurePassword123")
                .fullName("System Administrator")
                .build();

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("systemadmin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 3. Verify database state
        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        User admin = users.getFirst();
        assertThat(admin.getUsername()).isEqualTo("systemadmin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getActive()).isTrue();

        // 4. Verify password is saved using BCrypt
        assertThat(admin.getPassword()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("AdminSecurePassword123", admin.getPassword())).isTrue();
        assertThat(admin.getPassword()).isNotEqualTo("AdminSecurePassword123");

        // 5. Verify status endpoint reports setupRequired = false
        mockMvc.perform(get("/api/auth/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(false));
    }

    @Test
    @DisplayName("Setup is rejected with 409 Conflict after a user already exists")
    void setupIsRejectedAfterUserExists() throws Exception {
        // Create first admin
        SetupRequest firstRequest = SetupRequest.builder()
                .username("firstadmin")
                .email("first@example.com")
                .password("Password123")
                .fullName("First Admin")
                .build();

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Attempt second setup
        SetupRequest secondRequest = SetupRequest.builder()
                .username("secondadmin")
                .email("second@example.com")
                .password("Password456")
                .fullName("Second Admin")
                .build();

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409));

        // Ensure database still has only the first admin
        assertThat(userRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Concurrent setup requests allow exactly one successful first-admin creation")
    void concurrentSetupAllowsExactlyOneAdminCreation() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously

                    SetupRequest request = SetupRequest.builder()
                            .username("admin_" + index)
                            .email("admin_" + index + "@test.com")
                            .password("SecurePassword" + index)
                            .fullName("Admin " + index)
                            .build();

                    var result = mockMvc.perform(post("/api/auth/setup")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int statusCode = result.getResponse().getStatus();
                    if (statusCode == 201) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads at the exact same moment
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(userRepository.count()).isEqualTo(1L);
    }
}
