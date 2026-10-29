package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverManifestResponse {
    private Long routeId;
    private Long driverId;
    private String driverName;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private Double vehicleCapacityKg;
    private Double currentLoadKg;
    private Double remainingCapacityKg;

    private String batchType; // FIRST_MILE, FINAL_MILE, or MIXED
    private Integer totalStopsCount;
    private Integer completedStopsCount;
    private Integer pendingStopsCount;

    private ManifestStop currentActiveStop;
    @Builder.Default
    private List<ManifestStop> stops = new ArrayList<>();

    private String routeGeometryGeoJson;
    private Double totalDistanceKm;
    private Double estimatedDurationMin;
}
