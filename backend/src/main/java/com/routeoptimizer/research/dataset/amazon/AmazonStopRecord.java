package com.routeoptimizer.research.dataset.amazon;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AmazonStopRecord {
    String stopId;
    Double latitude;
    Double longitude;
    String type;
    String zoneId;
    List<AmazonPackageRecord> packages;
    Integer actualSequence;
}
