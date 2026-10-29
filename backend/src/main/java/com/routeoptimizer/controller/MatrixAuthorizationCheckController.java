package com.routeoptimizer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller strictly implementing the Section 44 Permission Matrix
 * authorization endpoints for verification and integration testing.
 */
@RestController
@RequestMapping("/api/matrix-check")
public class MatrixAuthorizationCheckController {

    // 1. Create Users: ADMIN only
    @GetMapping("/create-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkCreateUsers() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 2. Manage User Roles: ADMIN only
    @GetMapping("/manage-roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManageRoles() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 3. View Dashboard: ADMIN: YES, DRIVER: YES (Own data)
    @GetMapping("/view-dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<Map<String, String>> checkViewDashboard() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 4. Manage Locations: ADMIN: YES, DRIVER: NO
    @GetMapping("/manage-locations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManageLocations() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 5. Manage Roads/Edges: ADMIN: YES, DRIVER: NO
    @GetMapping("/manage-roads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManageRoads() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 6. Manage Packages: ADMIN: YES, DRIVER: NO
    @GetMapping("/manage-packages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManagePackages() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 7. Manage Drivers: ADMIN: YES, DRIVER: NO
    @GetMapping("/manage-drivers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManageDrivers() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 8. Manage Vehicles: ADMIN: YES, DRIVER: NO
    @GetMapping("/manage-vehicles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkManageVehicles() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 9. Create Deliveries: ADMIN: YES, DRIVER: NO
    @GetMapping("/create-deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkCreateDeliveries() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 10. Assign Driver/Vehicle: ADMIN: YES, DRIVER: NO
    @GetMapping("/assign-resources")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkAssignResources() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 11. Optimize Route: ADMIN: YES, DRIVER: NO
    @GetMapping("/optimize-route")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkOptimizeRoute() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 12. Re-optimize Route: ADMIN: YES, DRIVER: NO
    @GetMapping("/reoptimize-route")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkReoptimizeRoute() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 13. View All Routes: ADMIN: YES, DRIVER: NO
    @GetMapping("/view-all-routes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkViewAllRoutes() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 14. View Assigned Routes: ADMIN: YES, DRIVER: YES
    @GetMapping("/view-assigned-routes")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<Map<String, String>> checkViewAssignedRoutes() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 15. Start Own Delivery: ADMIN: YES, DRIVER: YES
    @GetMapping("/start-own-delivery")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<Map<String, String>> checkStartOwnDelivery() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 16. Complete Own Delivery: ADMIN: YES, DRIVER: YES
    @GetMapping("/complete-own-delivery")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<Map<String, String>> checkCompleteOwnDelivery() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 17. Mark Own Delivery Failed: ADMIN: YES, DRIVER: YES
    @GetMapping("/mark-own-delivery-failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<Map<String, String>> checkMarkOwnDeliveryFailed() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 18. Cancel Delivery: ADMIN: YES, DRIVER: NO
    @GetMapping("/cancel-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkCancelDelivery() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 19. View Analytics: ADMIN: YES, DRIVER: NO
    @GetMapping("/view-analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkViewAnalytics() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }

    // 20. Generate Reports: ADMIN: YES, DRIVER: NO
    @GetMapping("/generate-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> checkGenerateReports() {
        return ResponseEntity.ok(Map.of("status", "ALLOWED"));
    }
}
