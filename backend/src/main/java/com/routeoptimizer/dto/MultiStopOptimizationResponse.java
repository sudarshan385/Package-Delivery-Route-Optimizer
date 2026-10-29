package com.routeoptimizer.dto;

import com.routeoptimizer.enums.AlgorithmType;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiStopOptimizationResponse {
    private Long routeId;
    private Integer versionNumber;
    private AlgorithmType algorithm;
    private Long hubLocationId;
    private String hubLocationName;
    private Double totalDistance;
    private Double totalEstimatedTime;
    private OffsetDateTime departureTime;
    private OffsetDateTime estimatedCompletionTime;
    private Boolean returnToHub;
    private List<Long> orderedDeliveryIds;
    private List<CustomerStopResponse> customerStops;
    private List<RouteStopResponse> allStops;
    private List<RouteEdgeResponse> orderedEdges;
    private Double totalWeightKg;
    private Double vehicleCapacityKg;
    private String criterion;
    private String explanation;
}
