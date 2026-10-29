package com.routeoptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproveLocationRequest {
    private Long connectToLocationId;
    private Boolean createIncomingEdge; // existing -> pending
    private Boolean createOutgoingEdge; // pending -> existing
    private Double incomingDistance;
    private Double incomingEstimatedTime;
    private String incomingGeometryGeojson;
    private Double outgoingDistance;
    private Double outgoingEstimatedTime;
    private String outgoingGeometryGeojson;
}

