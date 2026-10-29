package com.routeoptimizer.dto;

import com.routeoptimizer.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusUpdateRequest {

    @NotNull(message = "status is required")
    private DeliveryStatus status;

    private String reason;
}
