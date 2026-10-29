package com.routeoptimizer;

import com.routeoptimizer.dto.ChangePasswordRequest;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
public class AdminUserRecoveryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // Enforce database guard before running any test operation
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
    }

    @Test
    @Transactional
    @DisplayName("Secure admin credential rotation preserves account identity without creating duplicate admins")
    public void testSecureAdminCredentialRotationPreservesIdentity() {
        // Dynamic random passwords — no hardcoded credentials
        String initialPassword = "Init_" + UUID.randomUUID().toString().substring(0, 8) + "!1Aa";
        String rotatedPassword = "Rot_" + UUID.randomUUID().toString().substring(0, 8) + "!2Bb";
        String testAdminUsername = "test_admin_" + UUID.randomUUID().toString().substring(0, 6);

        long initialAdminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();

        // 1. Create a test administrator
        User admin = User.builder()
                .username(testAdminUsername)
                .email(testAdminUsername + "@test-domain.local")
                .password(passwordEncoder.encode(initialPassword))
                .fullName("Test Admin")
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        admin.setVersion(0L);
        User savedAdmin = userRepository.save(admin);
        Long originalId = savedAdmin.getId();
        String originalEmail = savedAdmin.getEmail();

        // 2. Rotate credential through UserService
        ChangePasswordRequest request = new ChangePasswordRequest(initialPassword, rotatedPassword);
        userService.changePassword(testAdminUsername, request);

        // 3. Verify user identity, attributes, and roles are preserved
        User updatedAdmin = userRepository.findByUsernameIgnoreCase(testAdminUsername)
                .orElseThrow();
        assertThat(updatedAdmin.getId()).isEqualTo(originalId);
        assertThat(updatedAdmin.getEmail()).isEqualTo(originalEmail);
        assertThat(updatedAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(updatedAdmin.getActive()).isTrue();

        // 4. Verify password update correctness
        assertThat(passwordEncoder.matches(rotatedPassword, updatedAdmin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(initialPassword, updatedAdmin.getPassword())).isFalse();

        // 5. Verify no duplicate administrator accounts were created
        long finalAdminCount = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .count();
        assertThat(finalAdminCount).isEqualTo(initialAdminCount + 1);

        // 6. Verify incorrect current password is rejected
        ChangePasswordRequest invalidRequest = new ChangePasswordRequest("WrongPassword!99", "NewPassword!100");
        assertThatThrownBy(() -> userService.changePassword(testAdminUsername, invalidRequest))
                .isInstanceOf(BadCredentialsException.class);
    }
}
