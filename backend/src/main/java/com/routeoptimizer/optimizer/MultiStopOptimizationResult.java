package com.routeoptimizer.optimizer;

import com.routeoptimizer.dto.CustomerStopResponse;
import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.RouteEdge;
import com.routeoptimizer.entity.RouteStop;
import com.routeoptimizer.enums.AlgorithmType;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiStopOptimizationResult {
    private AlgorithmType algorithm;
    private Location hubLocation;
    private Location finalLocation;
    private Double totalDistance;
    private Double totalEstimatedTime;
    private OffsetDateTime departureTime;
    private OffsetDateTime estimatedCompletionTime;
    private boolean returnToHub;
    private List<Delivery> orderedDeliveries;
    private List<CustomerStopResponse> customerStops;
    private List<RouteStop> stops;
    private List<RouteEdge> edges;
    private String criterion;
    private String explanation;
}
