package com.routeoptimizer.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStopResponse {
    private Integer customerStopNumber;
    private Long deliveryId;
    private Long packageId;
    private String trackingNumber;
    private Long locationId;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private Double legDistance;
    private Double legDuration;
    private Double cumulativeDistance;
    private Double cumulativeDuration;
    private OffsetDateTime estimatedArrivalTime;
    private Double packageWeight;
}
