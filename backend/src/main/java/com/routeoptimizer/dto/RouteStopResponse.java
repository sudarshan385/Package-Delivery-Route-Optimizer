package com.routeoptimizer.dto;

import com.routeoptimizer.enums.StopType;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteStopResponse {
    private Long id;
    private Integer stopOrder;
    private StopType stopType;
    private Long locationId;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private OffsetDateTime estimatedArrivalTime;
}
