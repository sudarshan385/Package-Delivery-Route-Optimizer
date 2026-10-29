package com.routeoptimizer.research.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class DynamicReplanRequest {
    private Long driverLocationId;
    private List<Long> currentDeliveryIds;
    private Long newDeliveryId;
}
