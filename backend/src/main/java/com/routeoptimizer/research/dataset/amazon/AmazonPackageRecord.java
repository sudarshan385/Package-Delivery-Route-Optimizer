package com.routeoptimizer.research.dataset.amazon;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.Map;

@Value
@Builder
public class AmazonPackageRecord {
    String packageId;
    String scanStatus;
    OffsetDateTime timeWindowStartUtc;
    OffsetDateTime timeWindowEndUtc;
    Double plannedServiceTimeSeconds;
    Map<String, Double> dimensions;
}
