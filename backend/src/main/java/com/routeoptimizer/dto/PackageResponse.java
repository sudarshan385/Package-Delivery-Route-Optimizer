package com.routeoptimizer.dto;

import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.FulfillmentMode;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageResponse {

    private Long id;
    private String trackingNumber;
    private FulfillmentMode fulfillmentMode;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String city;
    private String pickupLocation;
    private String deliveryLocation;
    private Long customerId;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long departureHubId;
    private String departureHubName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Long nearestHubId;
    private String nearestHubName;
    private Double nearestHubDistance;
    private Double weight;
    private Priority priority;
    private PackageStatus status;
    private String description;
    private String itemName;
    private String itemCategory;
    private Integer quantity;
    private Boolean fragile;
    private Double declaredValue;
    private Double lengthCm;
    private Double widthCm;
    private Double heightCm;
    private String pickupInstructions;
    private String deliveryInstructions;
    private String senderEmail;
    private String receiverEmail;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static PackageResponse fromEntity(Package pkg) {
        if (pkg == null) {
            return null;
        }
        String sourceName = pkg.getSourceLocation() != null ? pkg.getSourceLocation().getName() : pkg.getPickupLocation();
        String destName = pkg.getDestinationLocation() != null ? pkg.getDestinationLocation().getName() : pkg.getDeliveryLocation();

        FulfillmentMode mode = FulfillmentMode.CUSTOMER_PICKUP;
        if (pkg.getSourceLocation() != null && pkg.getSourceLocation().getLocationType() != null &&
                ("HUB".equalsIgnoreCase(pkg.getSourceLocation().getLocationType()) ||
                 "DEPOT".equalsIgnoreCase(pkg.getSourceLocation().getLocationType()))) {
            mode = FulfillmentMode.HUB_STAGED;
        }

        Long depHubId = null;
        String depHubName = null;
        if (mode == FulfillmentMode.HUB_STAGED && pkg.getSourceLocation() != null) {
            depHubId = pkg.getSourceLocation().getId();
            depHubName = pkg.getSourceLocation().getName();
        }

        Long nearHubId = pkg.getNearestHub() != null ? pkg.getNearestHub().getId() : null;
        String nearHubName = pkg.getNearestHub() != null ? pkg.getNearestHub().getName() : null;

        return PackageResponse.builder()
                .id(pkg.getId())
                .trackingNumber(pkg.getTrackingNumber())
                .fulfillmentMode(mode)
                .senderName(pkg.getSenderName())
                .senderPhone(pkg.getSenderPhone())
                .receiverName(pkg.getReceiverName())
                .receiverPhone(pkg.getReceiverPhone())
                .city(pkg.getCity())
                .pickupLocation(pkg.getPickupLocation())
                .deliveryLocation(pkg.getDeliveryLocation())
                .customerId(pkg.getCustomer() != null ? pkg.getCustomer().getId() : null)
                .sourceLocationId(pkg.getSourceLocation() != null ? pkg.getSourceLocation().getId() : null)
                .sourceLocationName(sourceName)
                .departureHubId(depHubId)
                .departureHubName(depHubName)
                .destinationLocationId(pkg.getDestinationLocation() != null ? pkg.getDestinationLocation().getId() : null)
                .destinationLocationName(destName)
                .nearestHubId(nearHubId)
                .nearestHubName(nearHubName)
                .nearestHubDistance(pkg.getNearestHubDistance())
                .weight(pkg.getWeight())
                .priority(pkg.getPriority())
                .status(pkg.getStatus())
                .description(pkg.getDescription())
                .itemName(pkg.getItemName())
                .itemCategory(pkg.getItemCategory())
                .quantity(pkg.getQuantity())
                .fragile(pkg.getFragile())
                .declaredValue(pkg.getDeclaredValue())
                .lengthCm(pkg.getLengthCm())
                .widthCm(pkg.getWidthCm())
                .heightCm(pkg.getHeightCm())
                .pickupInstructions(pkg.getPickupInstructions())
                .deliveryInstructions(pkg.getDeliveryInstructions())
                .senderEmail(pkg.getSenderEmail())
                .receiverEmail(pkg.getReceiverEmail())
                .active(pkg.getActive())
                .version(pkg.getVersion())
                .createdAt(pkg.getCreatedAt())
                .updatedAt(pkg.getUpdatedAt())
                .build();
    }
}
