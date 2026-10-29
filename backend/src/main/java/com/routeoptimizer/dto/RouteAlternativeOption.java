package com.routeoptimizer.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteAlternativeOption {
    private Integer alternativeIndex;
    private String label;
    private Double distanceKm;
    private Double durationMin;
    private String geometryGeoJson;
    private String transportMode;
    private boolean recommended;
    private String summary;
}
