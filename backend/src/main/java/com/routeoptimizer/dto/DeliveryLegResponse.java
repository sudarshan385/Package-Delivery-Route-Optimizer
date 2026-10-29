package com.routeoptimizer.dto;

import com.routeoptimizer.entity.DeliveryLeg;
import com.routeoptimizer.enums.LegStatus;
import com.routeoptimizer.enums.LegType;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryLegResponse {
    private Long id;
    private Long deliveryId;
    private LegType legType;
    private Integer legOrder;
    private Long sourceLocationId;
    private String sourceLocationName;
    private String sourceAddress;
    private Double sourceLatitude;
    private Double sourceLongitude;
    private Long destinationLocationId;
    private String destinationLocationName;
    private String destinationAddress;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private String vehicleModel;
    private Long routeId;
    private LegStatus status;
    private OffsetDateTime assignedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Double distance;
    private Double estimatedTime;
    private String notes;
    private String routeGeometry;

    // Contextual package and contact fields for Driver UI
    private String trackingNumber;
    private String senderName;
    private String senderPhone;
    private String recipientName;
    private String recipientPhone;
    private Double packageWeight;
    private Boolean fragile;
    private String itemName;
    private String category;

    // Origin location aliases
    private String originLocationName;
    private String originAddress;
    private Double originLatitude;
    private Double originLongitude;

    public static DeliveryLegResponse fromEntity(DeliveryLeg leg) {
        if (leg == null) return null;

        com.routeoptimizer.entity.Package pkg = leg.getDelivery() != null ? leg.getDelivery().getPackage() : null;
        String trackNum = pkg != null ? pkg.getTrackingNumber() : null;
        String sName = pkg != null ? pkg.getSenderName() : null;
        String sPhone = pkg != null ? pkg.getSenderPhone() : null;
        String rName = pkg != null ? pkg.getReceiverName() : null;
        String rPhone = pkg != null ? pkg.getReceiverPhone() : null;
        Double pWeight = pkg != null ? pkg.getWeight() : null;
        Boolean isFragile = pkg != null ? pkg.getFragile() : null;
        String iName = pkg != null ? pkg.getItemName() : null;
        String cat = pkg != null ? pkg.getItemCategory() : null;

        String srcName = leg.getSourceLocation() != null ? leg.getSourceLocation().getName() : null;
        String srcAddr = leg.getSourceLocation() != null ? leg.getSourceLocation().getAddress() : null;
        Double srcLat = leg.getSourceLocation() != null ? leg.getSourceLocation().getLatitude() : null;
        Double srcLng = leg.getSourceLocation() != null ? leg.getSourceLocation().getLongitude() : null;

        return DeliveryLegResponse.builder()
                .id(leg.getId())
                .deliveryId(leg.getDelivery() != null ? leg.getDelivery().getId() : null)
                .legType(leg.getLegType())
                .legOrder(leg.getLegOrder())
                .sourceLocationId(leg.getSourceLocation() != null ? leg.getSourceLocation().getId() : null)
                .sourceLocationName(srcName)
                .sourceAddress(srcAddr)
                .sourceLatitude(srcLat)
                .sourceLongitude(srcLng)
                .destinationLocationId(leg.getDestinationLocation() != null ? leg.getDestinationLocation().getId() : null)
                .destinationLocationName(leg.getDestinationLocation() != null ? leg.getDestinationLocation().getName() : null)
                .destinationAddress(leg.getDestinationLocation() != null ? leg.getDestinationLocation().getAddress() : null)
                .destinationLatitude(leg.getDestinationLocation() != null ? leg.getDestinationLocation().getLatitude() : null)
                .destinationLongitude(leg.getDestinationLocation() != null ? leg.getDestinationLocation().getLongitude() : null)
                .driverId(leg.getDriver() != null ? leg.getDriver().getId() : null)
                .driverName(leg.getDriver() != null && leg.getDriver().getUser() != null ? leg.getDriver().getUser().getFullName() : null)
                .driverPhone(leg.getDriver() != null && leg.getDriver().getUser() != null ? leg.getDriver().getUser().getEmail() : null)
                .vehicleId(leg.getVehicle() != null ? leg.getVehicle().getId() : null)
                .vehicleRegistrationNumber(leg.getVehicle() != null ? leg.getVehicle().getRegistrationNumber() : null)
                .vehicleModel(leg.getVehicle() != null && leg.getVehicle().getVehicleType() != null ? leg.getVehicle().getVehicleType().name() : null)
                .routeId(leg.getRoute() != null ? leg.getRoute().getId() : null)
                .status(leg.getStatus())
                .assignedAt(leg.getAssignedAt())
                .startedAt(leg.getStartedAt())
                .completedAt(leg.getCompletedAt())
                .distance(leg.getDistance())
                .estimatedTime(leg.getEstimatedTime())
                .notes(leg.getNotes())
                .trackingNumber(trackNum)
                .senderName(sName)
                .senderPhone(sPhone)
                .recipientName(rName)
                .recipientPhone(rPhone)
                .packageWeight(pWeight)
                .fragile(isFragile)
                .itemName(iName)
                .category(cat)
                .originLocationName(srcName)
                .originAddress(srcAddr)
                .originLatitude(srcLat)
                .originLongitude(srcLng)
                .build();
    }
}
