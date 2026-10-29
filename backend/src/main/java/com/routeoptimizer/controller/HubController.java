package com.routeoptimizer.controller;

import com.routeoptimizer.dto.LocationResponse;
import com.routeoptimizer.service.BengaluruHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations/hubs")
@RequiredArgsConstructor
public class HubController {

    private final BengaluruHubService bengaluruHubService;

    @PostMapping("/setup-bengaluru")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> setupBengaluruHubs() {
        return ResponseEntity.ok(bengaluruHubService.initializeOrSyncBengaluruHubs());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> getAllHubsWithWorkloads() {
        return ResponseEntity.ok(bengaluruHubService.getAllActiveHubsWithWorkloads());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LocationResponse> updateHubStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        return ResponseEntity.ok(bengaluruHubService.updateHubStatus(id, active));
    }
}
