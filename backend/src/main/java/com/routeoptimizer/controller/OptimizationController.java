package com.routeoptimizer.controller;

import com.routeoptimizer.dto.MultiStopOptimizationRequest;
import com.routeoptimizer.dto.MultiStopOptimizationResponse;
import com.routeoptimizer.dto.OptimizationRequest;
import com.routeoptimizer.dto.OptimizationResponse;
import com.routeoptimizer.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/optimization")
@RequiredArgsConstructor
public class OptimizationController {

    private final RouteService routeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OptimizationResponse> optimize(
            @Valid @RequestBody OptimizationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routeService.optimizeAndPersistRoute(request, userDetails.getUsername()));
    }

    @PostMapping("/multi-stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MultiStopOptimizationResponse> optimizeMultiStop(
            @Valid @RequestBody MultiStopOptimizationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routeService.optimizeAndPersistMultiDeliveryRoute(request, userDetails.getUsername()));
    }
}
