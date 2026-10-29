package com.routeoptimizer.controller;

import com.routeoptimizer.dto.AssignLegRequest;
import com.routeoptimizer.dto.CustomerShipmentRequest;
import com.routeoptimizer.dto.DeliveryLegResponse;
import com.routeoptimizer.dto.DeliveryResponse;
import com.routeoptimizer.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<DeliveryResponse> createShipment(
            @Valid @RequestBody CustomerShipmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        DeliveryResponse response = shipmentService.createCustomerShipment(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeliveryResponse>> getAllShipments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(shipmentService.getMyShipments(userDetails.getUsername()));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DeliveryResponse>> getMyShipments(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(shipmentService.getMyShipments(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeliveryResponse> getShipmentDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(shipmentService.getShipmentDetails(id, userDetails.getUsername()));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<DeliveryResponse> trackShipment(
            @PathVariable String trackingNumber) {
        return ResponseEntity.ok(shipmentService.trackShipmentByTrackingNumber(trackingNumber));
    }

    @PostMapping(value = {"/legs/{legId}/assign", "/{deliveryId}/legs/{legId}/assign"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponse> assignDeliveryLeg(
            @PathVariable(required = false) Long deliveryId,
            @PathVariable Long legId,
            @Valid @RequestBody AssignLegRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(shipmentService.assignDeliveryLeg(deliveryId, legId, request, userDetails.getUsername()));
    }

    @RequestMapping(value = "/legs/{legId}/status", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<DeliveryResponse> updateLegStatus(
            @PathVariable Long legId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String targetStatus = body.get("status");
        String reason = body.get("reason");
        return ResponseEntity.ok(shipmentService.updateLegStatus(legId, targetStatus, reason, userDetails.getUsername()));
    }

    @RequestMapping(value = "/{deliveryId}/override-hub", method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeliveryResponse> overrideHub(
            @PathVariable Long deliveryId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long newHubId = Long.valueOf(body.get("newHubId").toString());
        String reason = body.getOrDefault("reason", "Administrative rerouting").toString();
        return ResponseEntity.ok(shipmentService.overrideHub(deliveryId, newHubId, reason, userDetails.getUsername()));
    }

    @GetMapping("/driver/assigned-legs")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    public ResponseEntity<List<DeliveryLegResponse>> getDriverAssignedLegs(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(shipmentService.getDriverAssignedLegs(userDetails.getUsername()));
    }
}
