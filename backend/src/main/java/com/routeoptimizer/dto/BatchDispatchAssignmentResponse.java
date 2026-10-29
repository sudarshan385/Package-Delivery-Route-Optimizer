package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDispatchAssignmentResponse {
    private Long routeId;
    private String batchType;
    private Long hubId;
    private String hubName;

    private Long driverId;
    private String driverName;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private Double vehicleCapacityKg;

    @Builder.Default
    private List<Long> assignedLegIds = new ArrayList<>();
    @Builder.Default
    private List<Long> assignedDeliveryIds = new ArrayList<>();

    private Integer totalPackages;
    private Double totalWeightKg;
    private Double remainingCapacityKg;
    private Double totalDistanceKm;
    private Double estimatedDurationMin;
    private OffsetDateTime dispatchedAt;
    private String status;
    private String message;
}
