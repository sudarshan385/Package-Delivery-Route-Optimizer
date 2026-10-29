package com.routeoptimizer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDeliveryRequest {

    @NotNull(message = "packageId is required")
    private Long packageId;

    private Long sourceLocationId;

    private String notes;
}
