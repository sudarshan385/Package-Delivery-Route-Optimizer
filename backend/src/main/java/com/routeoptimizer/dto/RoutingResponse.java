package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingResponse {
    private Double distanceKm;
    private Double durationMin;
    private String geometryGeoJson;
    private String transportMode;
    private String provider;
    private List<RouteAlternativeOption> alternatives;
}
