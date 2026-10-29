package com.routeoptimizer.controller;

import com.routeoptimizer.dto.LocationEdgeRequest;
import com.routeoptimizer.dto.LocationEdgeResponse;
import com.routeoptimizer.service.LocationEdgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edges")
@RequiredArgsConstructor
public class LocationEdgeController {

    private final LocationEdgeService locationEdgeService;
    private final com.routeoptimizer.service.RoutingService routingService;
    private final com.routeoptimizer.repository.LocationRepository locationRepository;

    @GetMapping("/calculate-route")
    public ResponseEntity<com.routeoptimizer.dto.RoutingResponse> calculateRoute(
            @RequestParam Long sourceId,
            @RequestParam Long destinationId) {
        return ResponseEntity.ok(locationEdgeService.calculateRoute(sourceId, destinationId));
    }

    @GetMapping("/route-alternatives")
    public ResponseEntity<com.routeoptimizer.dto.RoutingResponse> getRouteAlternatives(
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) Double sourceLat,
            @RequestParam(required = false) Double sourceLon,
            @RequestParam(required = false) Double destLat,
            @RequestParam(required = false) Double destLon,
            @RequestParam(defaultValue = "CAR") String mode,
            @RequestParam(required = false) String transportMode) {
        
        String effectiveMode = transportMode != null ? transportMode : mode;
        Double sLat = sourceLat;
        Double sLon = sourceLon;
        Double dLat = destLat;
        Double dLon = destLon;

        if ((sLat == null || sLon == null) && sourceId != null) {
            var src = locationRepository.findById(sourceId).orElse(null);
            if (src != null) {
                sLat = src.getLatitude();
                sLon = src.getLongitude();
            }
        }
        if ((dLat == null || dLon == null) && destinationId != null) {
            var dst = locationRepository.findById(destinationId).orElse(null);
            if (dst != null) {
                dLat = dst.getLatitude();
                dLon = dst.getLongitude();
            }
        }

        if (sLat == null || sLon == null || dLat == null || dLon == null) {
            throw new com.routeoptimizer.exception.BadRequestException("Source and destination coordinates or valid location IDs are required.");
        }

        return ResponseEntity.ok(routingService.calculateRouteWithAlternatives(sLat, sLon, dLat, dLon, effectiveMode));
    }

    @PostMapping("/sync")
    public ResponseEntity<java.util.List<LocationEdgeResponse>> syncRoadEdges(
            @RequestParam Long sourceId,
            @RequestParam Long destinationId,
            @RequestParam(defaultValue = "true") boolean bidirectional) {
        return ResponseEntity.ok(locationEdgeService.syncRoadEdges(sourceId, destinationId, bidirectional));
    }

    @PostMapping
    public ResponseEntity<LocationEdgeResponse> createEdge(@Valid @RequestBody LocationEdgeRequest request) {
        LocationEdgeResponse response = locationEdgeService.createEdge(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationEdgeResponse> getEdgeById(@PathVariable Long id) {
        return ResponseEntity.ok(locationEdgeService.getEdgeById(id));
    }

    @GetMapping
    public ResponseEntity<Page<LocationEdgeResponse>> getAllEdges(
            @RequestParam(required = false) Long sourceLocationId,
            @RequestParam(required = false) Long destinationLocationId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(locationEdgeService.getAllEdges(sourceLocationId, destinationLocationId, active, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationEdgeResponse> updateEdge(
            @PathVariable Long id,
            @Valid @RequestBody LocationEdgeRequest request) {
        return ResponseEntity.ok(locationEdgeService.updateEdge(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<LocationEdgeResponse> deactivateEdge(@PathVariable Long id) {
        return ResponseEntity.ok(locationEdgeService.deactivateEdge(id));
    }
}
