package com.routeoptimizer.optimizer;

import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.enums.AlgorithmType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationResult {
    private AlgorithmType algorithm;
    private Location source;
    private Location destination;
    private List<Location> orderedLocations;
    private List<LocationEdge> orderedEdges;
    private Double totalDistance;
    private Double totalEstimatedTime;
    private boolean pathExists;
    private String criterion;
    private String explanation;
}
