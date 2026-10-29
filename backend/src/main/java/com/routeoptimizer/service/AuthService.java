package com.routeoptimizer.service;

import com.routeoptimizer.dto.AuthResponse;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.dto.SetupRequest;
import com.routeoptimizer.dto.SetupStatusResponse;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.enums.DriverStatus;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final JdbcTemplate jdbcTemplate;
    private final Environment env;

    // Advisory lock key used exclusively for initial admin bootstrap synchronization
    private static final long SETUP_ADVISORY_LOCK_ID = 8844221199L;

    @Transactional(readOnly = true)
    public SetupStatusResponse getSetupStatus() {
        boolean setupRequired = !userRepository.existsByRole(Role.ADMIN);
        return SetupStatusResponse.builder()
                .setupRequired(setupRequired)
                .message(setupRequired
                        ? "Initial system setup is available. First administrator account must be created."
                        : "Initial setup has already been completed.")
                .build();
    }

    /**
     * Concurrency-safe initial admin creation using PostgreSQL advisory transaction lock.
     * Guarantees exactly one admin is created even under simultaneous concurrent setup requests.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse setupInitialAdmin(SetupRequest request) {
        // Enforce execution environment
        String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        java.util.List<String> activeProfiles = java.util.Arrays.asList(env.getActiveProfiles());
        
        boolean isDevEnv = activeProfiles.contains("dev") && "packagedeliveryrouteoptimizer".equals(dbName);
        boolean isTestEnv = activeProfiles.contains("test") && "route_optimizer_test_db".equals(dbName);
        
        if (!isDevEnv && !isTestEnv) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator bootstrap is only permitted in the development environment.");
        }

        // Acquire exclusive advisory transaction lock
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + SETUP_ADVISORY_LOCK_ID + ")");

        if (userRepository.existsByRole(Role.ADMIN)) {
            throw new ConflictException("Initial setup has already been completed. System administrator account already exists.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already in use.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered.");
        }

        User admin = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build();
        admin.setActive(true);
        admin.setVersion(0L);

        User savedAdmin = userRepository.save(admin);

        // Generate immediate JWT token for the created administrator
        String token = tokenProvider.generateTokenForUsername(savedAdmin.getUsername());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .userId(savedAdmin.getId())
                .username(savedAdmin.getUsername())
                .email(savedAdmin.getEmail())
                .role(savedAdmin.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse register(com.routeoptimizer.dto.RegisterRequest request) {
        if (request.getRole() == Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Administrator registration is not allowed through this endpoint.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' is already in use.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' is already registered.");
        }

        if (request.getRole() == Role.DRIVER) {
            if (!StringUtils.hasText(request.getLicenseNumber())) {
                throw new com.routeoptimizer.exception.BadRequestException("License number is required for DRIVER.");
            }
            if (driverRepository.findByLicenseNumber(request.getLicenseNumber().trim()).isPresent()) {
                throw new ConflictException("Driver license number '" + request.getLicenseNumber() + "' is already in use.");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole())
                .isVerified(false)
                .isBlocked(false)
                .build();
        user.setActive(true);
        user.setVersion(0L);

        User savedUser = userRepository.save(user);

        if (request.getRole() == Role.DRIVER) {
            Driver driver = Driver.builder()
                    .user(savedUser)
                    .name(StringUtils.hasText(request.getFullName()) ? request.getFullName() : request.getUsername())
                    .phone(request.getPhone())
                    .licenseNumber(request.getLicenseNumber().trim())
                    .status(DriverStatus.AVAILABLE)
                    .build();
            driver.setActive(true);
            driver.setVersion(0L);
            driverRepository.save(driver);
        }

        String token = tokenProvider.generateTokenForUsername(savedUser.getUsername());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getUsername() != null ? request.getUsername().trim() : "";
        User user = userRepository.findByUsernameIgnoreCase(identifier)
                .orElseGet(() -> userRepository.findByEmailIgnoreCase(identifier).orElse(null));

        if (user != null && Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new org.springframework.security.authentication.LockedException("Account is blocked. Please contact an administrator.");
        }

        if (user != null && !Boolean.TRUE.equals(user.getActive())) {
            throw new BadCredentialsException("Account has been deactivated. Please contact an administrator.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
        );
        
        if (user == null) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = tokenProvider.generateToken(authentication);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(tokenProvider.getExpirationMs())
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .build();
    }
}
