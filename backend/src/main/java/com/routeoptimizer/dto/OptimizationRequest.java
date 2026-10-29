package com.routeoptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationRequest {

    private Long startLocationId;
    private Long destinationLocationId;
    private Long deliveryId;
    private String criterion;
}
