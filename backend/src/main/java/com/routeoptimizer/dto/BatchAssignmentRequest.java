package com.routeoptimizer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchAssignmentRequest {

    @NotNull(message = "Route ID is required")
    private Long routeId;

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotEmpty(message = "Delivery IDs list cannot be empty")
    private List<Long> deliveryIds;

    private String notes;
}
