package com.routeoptimizer.controller;

import com.routeoptimizer.dto.RouteResponse;
import com.routeoptimizer.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/api/routes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<RouteResponse> getRouteById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routeService.getRouteById(id, userDetails.getUsername()));
    }

    @GetMapping("/api/deliveries/{id}/routes")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<List<RouteResponse>> getRoutesByDeliveryId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routeService.getRoutesByDeliveryId(id, userDetails.getUsername()));
    }

    @GetMapping("/api/deliveries/{id}/routes/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    public ResponseEntity<RouteResponse> getCurrentRouteByDeliveryId(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routeService.getCurrentRouteByDeliveryId(id, userDetails.getUsername()));
    }
}
