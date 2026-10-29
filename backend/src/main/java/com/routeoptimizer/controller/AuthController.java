package com.routeoptimizer.controller;

import com.routeoptimizer.dto.AuthResponse;
import com.routeoptimizer.dto.LoginRequest;
import com.routeoptimizer.dto.SetupRequest;
import com.routeoptimizer.dto.SetupStatusResponse;
import com.routeoptimizer.security.RateLimiterService;
import com.routeoptimizer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    @GetMapping("/setup/status")
    public ResponseEntity<SetupStatusResponse> getSetupStatus() {
        return ResponseEntity.ok(authService.getSetupStatus());
    }

    @PostMapping("/setup")
    public ResponseEntity<AuthResponse> setupInitialAdmin(@Valid @RequestBody SetupRequest request) {
        AuthResponse response = authService.setupInitialAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(HttpServletRequest httpRequest, @Valid @RequestBody LoginRequest request) {
        String clientIp = RateLimiterService.getClientIp(httpRequest);
        
        // 1. IP-based login rate limit check (enforced before any expensive authentication work)
        rateLimiterService.checkLoginIpRate(clientIp);

        // 2. Identifier-based failed attempt / cooldown check
        String identifier = request.getUsername();
        rateLimiterService.checkLoginIdentifierRate(identifier);

        try {
            AuthResponse response = authService.login(request);
            // 3. Clear failed attempts on successful authentication
            rateLimiterService.recordLoginSuccess(identifier);
            return ResponseEntity.ok(response);
        } catch (org.springframework.security.authentication.BadCredentialsException |
                 org.springframework.security.authentication.LockedException e) {
            // 4. Record failed attempt for the identifier
            rateLimiterService.recordLoginFailure(identifier);
            throw e;
        }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(HttpServletRequest httpRequest, @Valid @RequestBody com.routeoptimizer.dto.RegisterRequest request) {
        String clientIp = RateLimiterService.getClientIp(httpRequest);
        
        // IP-based registration rate limit check (enforced before any database query)
        rateLimiterService.checkRegisterIpRate(clientIp);

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

