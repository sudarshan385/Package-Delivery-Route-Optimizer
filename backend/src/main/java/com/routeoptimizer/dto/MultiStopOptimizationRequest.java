package com.routeoptimizer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiStopOptimizationRequest {

    @NotNull(message = "Hub location ID must not be null")
    private Long hubLocationId;

    @NotEmpty(message = "Delivery IDs list must not be empty")
    private List<Long> deliveryIds;

    @Builder.Default
    private String criterion = "SHORTEST_DISTANCE";

    @NotNull(message = "Explicit returnToHub selection is required")
    private Boolean returnToHub;

    private OffsetDateTime departureTime;

    private Long vehicleId;
}
