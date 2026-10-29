package com.routeoptimizer.dto;

import com.routeoptimizer.enums.AlgorithmType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationResponse {
    private AlgorithmType algorithm;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Double totalDistance;
    private Double totalEstimatedTime;
    private boolean pathExists;
    private List<Long> orderedLocationIds;
    private List<String> orderedLocationNames;
    private List<RouteEdgeResponse> orderedEdges;
    private Long routeId;
    private Integer versionNumber;
    private String criterion;
    private String explanation;
}
