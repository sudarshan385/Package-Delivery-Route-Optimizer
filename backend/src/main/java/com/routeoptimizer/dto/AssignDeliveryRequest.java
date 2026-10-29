package com.routeoptimizer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignDeliveryRequest {

    @NotNull(message = "driverId is required")
    private Long driverId;

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    private String notes;
}
