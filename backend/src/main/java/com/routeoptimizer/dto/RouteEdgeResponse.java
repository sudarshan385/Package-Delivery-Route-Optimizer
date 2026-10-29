package com.routeoptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteEdgeResponse {
    private Long id;
    private Integer edgeOrder;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Double distance;
    private Double estimatedTime;
    private String geometryGeojson;
}

