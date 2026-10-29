package com.routeoptimizer.dto;

import com.routeoptimizer.enums.DeliveryStatus;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryStatusHistoryResponse {
    private Long id;
    private Long deliveryId;
    private DeliveryStatus previousStatus;
    private DeliveryStatus newStatus;
    private Long changedByUserId;
    private String changedByUsername;
    private String reason;
    private OffsetDateTime createdAt;
}
