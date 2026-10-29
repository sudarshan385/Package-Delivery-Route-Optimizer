package com.routeoptimizer.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerShipmentRequest {

    // SENDER / PICKUP
    @NotBlank(message = "Sender name is required")
    private String senderName;

    @NotBlank(message = "Sender phone is required")
    private String senderPhone;

    private String senderEmail;

    @NotBlank(message = "Pickup address is required")
    private String pickupAddress;

    @Builder.Default
    private String pickupCity = "Bengaluru";

    @NotNull(message = "Pickup latitude is required")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    private Double pickupLongitude;

    private String pickupInstructions;

    // RECIPIENT / DESTINATION
    @NotBlank(message = "Recipient name is required")
    private String receiverName;

    @NotBlank(message = "Recipient phone is required")
    private String receiverPhone;

    private String receiverEmail;

    @NotBlank(message = "Delivery address is required")
    private String deliveryAddress;

    @Builder.Default
    private String deliveryCity = "Bengaluru";

    @NotNull(message = "Delivery latitude is required")
    private Double deliveryLatitude;

    @NotNull(message = "Delivery longitude is required")
    private Double deliveryLongitude;

    private String deliveryInstructions;

    // PACKAGE DETAILS
    @NotBlank(message = "Product or item name is required")
    private String itemName;

    private String itemCategory;

    private String description;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Builder.Default
    private Integer quantity = 1;

    @NotNull(message = "Package weight is required")
    @Positive(message = "Weight must be greater than zero")
    private Double weight;

    @Positive(message = "Length must be greater than zero")
    private Double lengthCm;

    @Positive(message = "Width must be greater than zero")
    private Double widthCm;

    @Positive(message = "Height must be greater than zero")
    private Double heightCm;

    @Builder.Default
    private Boolean fragile = false;

    private Double declaredValue;

    @Builder.Default
    private String priority = "MEDIUM";
}
