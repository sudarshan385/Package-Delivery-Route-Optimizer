package com.routeoptimizer.controller;

import com.routeoptimizer.dto.BatchDispatchAssignmentRequest;
import com.routeoptimizer.dto.BatchDispatchAssignmentResponse;
import com.routeoptimizer.dto.BatchRouteProposalResponse;
import com.routeoptimizer.dto.DriverManifestResponse;
import com.routeoptimizer.service.BatchRoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class BatchRoutingController {

    private final BatchRoutingService batchRoutingService;

    @GetMapping("/batch/config")
    public ResponseEntity<Map<String, Object>> getBatchConfiguration() {
        return ResponseEntity.ok(Map.of(
                "pickupRadiusKm", batchRoutingService.getConfiguredPickupRadiusKm(),
                "deliveryRadiusKm", batchRoutingService.getConfiguredDeliveryRadiusKm(),
                "unit", "km",
                "defaultCorridorLimit", 10.0
        ));
    }

    @GetMapping("/batch/propose-pickup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchRouteProposalResponse> proposeBatchPickup(
            @RequestParam Long hubId,
            @RequestParam(required = false) Double maxRadiusKm,
            @RequestParam(required = false) Long vehicleId) {
        return ResponseEntity.ok(batchRoutingService.proposeBatchPickup(hubId, maxRadiusKm, vehicleId));
    }

    @GetMapping("/batch/propose-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchRouteProposalResponse> proposeBatchDelivery(
            @RequestParam Long hubId,
            @RequestParam(required = false) Double maxRadiusKm,
            @RequestParam(required = false) Long vehicleId) {
        return ResponseEntity.ok(batchRoutingService.proposeBatchDelivery(hubId, maxRadiusKm, vehicleId));
    }

    @PostMapping("/batch/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDispatchAssignmentResponse> dispatchBatch(
            @Valid @RequestBody BatchDispatchAssignmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(batchRoutingService.dispatchBatch(request, userDetails.getUsername()));
    }

    @GetMapping("/driver/manifest")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<DriverManifestResponse> getDriverManifest(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(batchRoutingService.getDriverManifest(userDetails.getUsername()));
    }
}
