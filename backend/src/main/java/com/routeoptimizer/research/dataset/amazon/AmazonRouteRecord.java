package com.routeoptimizer.research.dataset.amazon;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Value
@Builder
public class AmazonRouteRecord {
    String routeId;
    String stationCode;
    LocalDate routeDate;
    LocalTime departureTimeUtc;
    Double executorCapacityCm3;
    String routeScore;
    List<AmazonStopRecord> stops;
}
