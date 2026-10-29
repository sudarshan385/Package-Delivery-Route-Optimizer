package com.routeoptimizer.dto;

import com.routeoptimizer.enums.FulfillmentMode;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageRequest {

    private FulfillmentMode fulfillmentMode;

    @Size(max = 50, message = "Tracking number must not exceed 50 characters")
    private String trackingNumber;

    @NotBlank(message = "Sender name is required")
    @Size(max = 100, message = "Sender name must not exceed 100 characters")
    private String senderName;

    @Size(max = 20, message = "Sender phone must not exceed 20 characters")
    private String senderPhone;

    @NotBlank(message = "Receiver name is required")
    @Size(max = 100, message = "Receiver name must not exceed 100 characters")
    private String receiverName;

    @Size(max = 20, message = "Receiver phone must not exceed 20 characters")
    private String receiverPhone;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 255, message = "Pickup location must not exceed 255 characters")
    private String pickupLocation;

    @Size(max = 255, message = "Delivery location must not exceed 255 characters")
    private String deliveryLocation;

    private Long sourceLocationId;

    private Long destinationLocationId;

    @NotNull(message = "Weight is required")
    @Positive(message = "Weight must be greater than zero")
    private Double weight;

    private Priority priority;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    private PackageStatus status;

    private Long customerId;
}
