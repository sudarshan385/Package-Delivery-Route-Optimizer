package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManifestStop {
    private Integer sequenceNumber;
    private Long legId;
    private Long deliveryId;
    private String trackingNumber;
    private String stopType; // PICKUP, HUB_DROP, DESTINATION_DELIVERY, HUB_START
    private String legType;  // FIRST_MILE, FINAL_MILE

    private Long locationId;
    private String locationName;
    private String address;
    private Double latitude;
    private Double longitude;

    private String contactPerson;
    private String contactPhone;
    private Double packageWeightKg;
    private String itemName;
    private String instructions;

    private String legStatus; // PENDING, ASSIGNED, IN_TRANSIT, COMPLETED
    private boolean isCurrentStop;
    private boolean isCompleted;
    private OffsetDateTime estimatedArrivalTime;
    private OffsetDateTime completedAt;
}
