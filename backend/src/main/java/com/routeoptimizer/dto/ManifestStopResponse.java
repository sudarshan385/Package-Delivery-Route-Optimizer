package com.routeoptimizer.dto;

import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.StopType;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManifestStopResponse {
    private Long stopId;
    private Integer stopOrder;
    private StopType stopType;
    private Integer customerStopNumber;
    private Long locationId;
    private String locationName;
    private String address;
    private Double latitude;
    private Double longitude;
    private Long deliveryId;
    private String trackingNumber;
    private String receiverName;
    private Double packageWeightKg;
    private DeliveryStatus deliveryStatus;
    private OffsetDateTime estimatedArrivalTime;
    private Double legDistance;
    private Double legDuration;
}
