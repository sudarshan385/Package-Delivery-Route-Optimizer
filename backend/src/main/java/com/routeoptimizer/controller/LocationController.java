package com.routeoptimizer.controller;

import com.routeoptimizer.dto.GeocodeResponse;
import com.routeoptimizer.dto.LocationRequest;
import com.routeoptimizer.dto.LocationResponse;
import com.routeoptimizer.service.GeocodingService;
import com.routeoptimizer.service.LocationService;
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
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final GeocodingService geocodingService;

    @GetMapping("/geocode")
    public java.util.concurrent.CompletableFuture<ResponseEntity<GeocodeResponse>> geocodeAddress(@RequestParam String address) {
        return geocodingService.geocodeAddressAsync(address)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping
    public ResponseEntity<LocationResponse> createLocation(@Valid @RequestBody LocationRequest request) {
        LocationResponse response = locationService.createLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> getLocationById(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.getLocationById(id));
    }

    @GetMapping
    public ResponseEntity<Page<LocationResponse>> getAllLocations(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(locationService.getAllLocations(name, active, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationResponse> updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request) {
        return ResponseEntity.ok(locationService.updateLocation(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<LocationResponse> deactivateLocation(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.deactivateLocation(id));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<LocationResponse> activateLocation(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.activateLocation(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<LocationResponse> approveLocation(
            @PathVariable Long id,
            @Valid @RequestBody com.routeoptimizer.dto.ApproveLocationRequest request) {
        return ResponseEntity.ok(locationService.approveLocation(id, request));
    }
}
