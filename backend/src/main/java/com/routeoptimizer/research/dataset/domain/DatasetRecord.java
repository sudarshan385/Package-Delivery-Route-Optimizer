package com.routeoptimizer.research.dataset.domain;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class DatasetRecord {
    String routeId;
    Long riderId;
    Long taskId;
    Long pickupLocationId;
    Long deliveryLocationId;
    OffsetDateTime actualPickupTime;
    OffsetDateTime actualDeliveryTime;
    OffsetDateTime pickupTimeWindowStart;
    OffsetDateTime pickupTimeWindowEnd;
    OffsetDateTime deliveryTimeWindowStart;
    OffsetDateTime deliveryTimeWindowEnd;
    Integer sequenceOrder;
}
