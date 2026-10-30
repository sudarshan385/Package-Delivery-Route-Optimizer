package com.routeoptimizer.dto;

import com.routeoptimizer.enums.DeliveryStatus;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryResponse {
    private Long id;
    private Long packageId;
    private String trackingNumber;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Long driverId;
    private String driverName;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private DeliveryStatus status;
    private Double estimatedDistance;
    private Double estimatedTime;
    private String notes;
    private OffsetDateTime completedAt;
    private String routeStatus;
    private Boolean canConfirmReturn;
    private OffsetDateTime returnConfirmedAt;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Multi-leg and Smart Hub Network Fields
    private Long hubId;
    private String hubName;
    private String hubAddress;
    private Double hubLatitude;
    private Double hubLongitude;
    private String hubSelectionBasis;
    private String currentStage;
    private PackageResponse packageDetails;
    private java.util.List<DeliveryLegResponse> legs;
    private java.util.List<ShipmentEventResponse> events;
}
