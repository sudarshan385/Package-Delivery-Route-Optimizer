package com.routeoptimizer.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiStopManifestResponse {
    private Long routeId;
    private Long hubId;
    private String hubName;
    private String hubAddress;
    private Double hubLatitude;
    private Double hubLongitude;
    private Boolean returnToHub;
    private Double totalDistance;
    private Double totalEstimatedTime;
    private String algorithmUsed;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private Long vehicleId;
    private String vehicleRegistration;
    private Double vehicleCapacityKg;
    private Double totalWeightKg;
    private Boolean isCompleted;
    private List<ManifestStopResponse> stops;
    private List<List<Double>> geometryCoordinates;
}
