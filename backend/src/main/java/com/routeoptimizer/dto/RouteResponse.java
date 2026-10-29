package com.routeoptimizer.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteResponse {
    private Long id;
    private Long deliveryId;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Integer versionNumber;
    private Boolean isCurrent;
    private Double totalDistance;
    private Double estimatedTime;
    private String algorithmUsed;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<RouteStopResponse> stops;
    private List<RouteEdgeResponse> edges;
}
