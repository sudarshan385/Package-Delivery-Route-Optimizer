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

    @PostMapping("/batch/propose-pickup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchRouteProposalResponse> proposeBatchPickupPost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Long hubId,
            @RequestParam(required = false) Double maxRadiusKm,
            @RequestParam(required = false) Long vehicleId) {
        Long resolvedHubId = extractLong(body, "hubId", hubId);
        Double resolvedRadius = extractDouble(body, "radiusKm", extractDouble(body, "maxRadiusKm", maxRadiusKm));
        Long resolvedVehicleId = extractLong(body, "vehicleId", vehicleId);
        return ResponseEntity.ok(batchRoutingService.proposeBatchPickup(resolvedHubId, resolvedRadius, resolvedVehicleId));
    }

    @GetMapping("/batch/propose-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchRouteProposalResponse> proposeBatchDelivery(
            @RequestParam Long hubId,
            @RequestParam(required = false) Double maxRadiusKm,
            @RequestParam(required = false) Long vehicleId) {
        return ResponseEntity.ok(batchRoutingService.proposeBatchDelivery(hubId, maxRadiusKm, vehicleId));
    }

    @PostMapping("/batch/propose-delivery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchRouteProposalResponse> proposeBatchDeliveryPost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Long hubId,
            @RequestParam(required = false) Double maxRadiusKm,
            @RequestParam(required = false) Long vehicleId) {
        Long resolvedHubId = extractLong(body, "hubId", hubId);
        Double resolvedRadius = extractDouble(body, "radiusKm", extractDouble(body, "maxRadiusKm", maxRadiusKm));
        Long resolvedVehicleId = extractLong(body, "vehicleId", vehicleId);
        return ResponseEntity.ok(batchRoutingService.proposeBatchDelivery(resolvedHubId, resolvedRadius, resolvedVehicleId));
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

    private Long extractLong(Map<String, Object> body, String key, Long fallback) {
        if (body != null && body.containsKey(key) && body.get(key) != null) {
            try {
                return Long.valueOf(body.get(key).toString());
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private Double extractDouble(Map<String, Object> body, String key, Double fallback) {
        if (body != null && body.containsKey(key) && body.get(key) != null) {
            try {
                return Double.valueOf(body.get(key).toString());
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}
