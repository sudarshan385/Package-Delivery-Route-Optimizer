package com.routeoptimizer.controller;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponse> createDelivery(
            @Valid @RequestBody CreateDeliveryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        DeliveryResponse response = deliveryService.createDelivery(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    public ResponseEntity<DeliveryResponse> getDeliveryById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.getDeliveryById(id, userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    public ResponseEntity<List<DeliveryResponse>> getAllDeliveries(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.getAllDeliveries(userDetails.getUsername()));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponse> assignDelivery(
            @PathVariable Long id,
            @Valid @RequestBody AssignDeliveryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.assignDelivery(id, request, userDetails.getUsername()));
    }

    @PostMapping("/batch-assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchAssignmentResponse> batchAssignDeliveries(
            @Valid @RequestBody BatchAssignmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.batchAssignDeliveries(request, userDetails.getUsername()));
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponse>> getUnassignedDeliveries(
            @RequestParam(required = false) Long hubId) {
        return ResponseEntity.ok(deliveryService.getUnassignedDeliveriesByHub(hubId));
    }

    @GetMapping("/manifest/driver")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<MultiStopManifestResponse> getDriverActiveManifest(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.getDriverActiveManifest(userDetails.getUsername()));
    }

    @GetMapping("/manifest/route/{routeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<MultiStopManifestResponse> getRouteManifest(
            @PathVariable Long routeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.getRouteManifest(routeId, userDetails.getUsername()));
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<DeliveryResponse> updateDeliveryStatus(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.updateDeliveryStatus(id, request, userDetails.getUsername()));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    public ResponseEntity<List<DeliveryStatusHistoryResponse>> getDeliveryHistory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.getDeliveryHistory(id, userDetails.getUsername()));
    }

    @PostMapping("/routes/{routeId}/confirm-return")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<MultiStopManifestResponse> confirmRouteReturnToHub(
            @PathVariable Long routeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.confirmRouteReturnToHub(routeId, userDetails.getUsername()));
    }

    @PostMapping("/{id}/confirm-return")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    public ResponseEntity<DeliveryResponse> confirmDeliveryReturnToHub(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(deliveryService.confirmDeliveryReturnToHub(id, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponse> deactivateDelivery(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.deactivateDelivery(id));
    }
}
