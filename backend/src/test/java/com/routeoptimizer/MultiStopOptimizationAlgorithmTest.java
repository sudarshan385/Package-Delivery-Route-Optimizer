package com.routeoptimizer;

import com.routeoptimizer.dto.CustomerStopResponse;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.AlgorithmType;
import com.routeoptimizer.enums.StopType;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.optimizer.MultiStopOptimizationResult;
import com.routeoptimizer.optimizer.MultiStopRouteOptimizationStrategy;
import com.routeoptimizer.optimizer.OptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiStopOptimizationAlgorithmTest {

    private MultiStopRouteOptimizationStrategy strategy;
    private Location hub;
    private Location loc1;
    private Location loc2;
    private Location loc3;
    private OffsetDateTime fixedDeparture;

    @BeforeEach
    void setUp() {
        strategy = new MultiStopRouteOptimizationStrategy(null, null, null);

        hub = Location.builder().id(100L).name("Central Hub").latitude(12.9716).longitude(77.5946).build();
        loc1 = Location.builder().id(101L).name("Customer Stop 1").latitude(12.9800).longitude(77.6000).build();
        loc2 = Location.builder().id(102L).name("Customer Stop 2").latitude(12.9900).longitude(77.6100).build();
        loc3 = Location.builder().id(103L).name("Customer Stop 3").latitude(13.0000).longitude(77.6200).build();

        fixedDeparture = OffsetDateTime.of(2026, 10, 22, 9, 0, 0, 0, ZoneOffset.UTC);
    }

    private Delivery createDelivery(Long id, Location dest, double weight) {
        Package pkg = Package.builder().id(id * 10).trackingNumber("TRK-" + id).weight(weight).build();
        return Delivery.builder().id(id).pkg(pkg).sourceLocation(hub).destinationLocation(dest).build();
    }

    // -------------------------------------------------------------------------
    // Test 1: Simple hub and three customers
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. Correct route for a simple hub and three customers")
    void testSimpleHubAndThreeCustomers() {
        Delivery d1 = createDelivery(1L, loc1, 2.5);
        Delivery d2 = createDelivery(2L, loc2, 3.0);
        Delivery d3 = createDelivery(3L, loc3, 1.5);

        // Linear road network: Hub -> 1 -> 2 -> 3
        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            double dist = Math.abs(to.getId() - from.getId()) * 5.0;
            double time = dist * 1.5;
            LocationEdge edge = LocationEdge.builder()
                    .sourceLocation(from).destinationLocation(to)
                    .distance(dist).estimatedTime(time)
                    .build();
            return OptimizationResult.builder()
                    .source(from).destination(to)
                    .totalDistance(dist).totalEstimatedTime(time)
                    .pathExists(true)
                    .orderedLocations(List.of(from, to))
                    .orderedEdges(List.of(edge))
                    .build();
        };

        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2, d3), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result).isNotNull();
        assertThat(result.getAlgorithm()).isEqualTo(AlgorithmType.MULTI_STOP_2OPT);
        assertThat(result.getOrderedDeliveries()).hasSize(3);
        assertThat(result.getCustomerStops()).hasSize(3);
        assertThat(result.getCustomerStops().get(0).getCustomerStopNumber()).isEqualTo(1);
        assertThat(result.getCustomerStops().get(1).getCustomerStopNumber()).isEqualTo(2);
        assertThat(result.getCustomerStops().get(2).getCustomerStopNumber()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Test 2: Case where 2-Opt optimizes and improves on original input order
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2. 2-Opt untangles crossing paths and improves upon suboptimal initial ordering")
    void test2OptImprovesInitialOrdering() {
        // Positions along a line: Hub (0), C1 (10), C2 (20), C3 (30)
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);
        Delivery d3 = createDelivery(3L, loc3, 1.0);

        Map<String, Double> distanceMap = new HashMap<>();
        // Distances from hub (100)
        distanceMap.put("100->101", 10.0); distanceMap.put("101->100", 10.0);
        distanceMap.put("100->102", 20.0); distanceMap.put("102->100", 20.0);
        distanceMap.put("100->103", 30.0); distanceMap.put("103->100", 30.0);
        // Inter-customer distances
        distanceMap.put("101->102", 10.0); distanceMap.put("102->101", 10.0);
        distanceMap.put("102->103", 10.0); distanceMap.put("103->102", 10.0);
        distanceMap.put("101->103", 20.0); distanceMap.put("103->101", 20.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            String key = from.getId() + "->" + to.getId();
            double dist = distanceMap.getOrDefault(key, 50.0);
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(dist).estimatedTime(dist).build();
            return OptimizationResult.builder()
                    .source(from).destination(to)
                    .totalDistance(dist).totalEstimatedTime(dist)
                    .pathExists(true)
                    .orderedLocations(List.of(from, to))
                    .orderedEdges(List.of(edge))
                    .build();
        };

        // Pass input in zig-zag order: d3 (furthest), d1 (closest), d2 (middle)
        // If unoptimized: H -> d3 -> d1 -> d2 = 30 + 20 + 10 = 60 km
        // Optimal tour:   H -> d1 -> d2 -> d3 = 10 + 10 + 10 = 30 km
        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d3, d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result.getTotalDistance()).isEqualTo(30.0);
        assertThat(result.getOrderedDeliveries().get(0).getId()).isEqualTo(1L);
        assertThat(result.getOrderedDeliveries().get(1).getId()).isEqualTo(2L);
        assertThat(result.getOrderedDeliveries().get(2).getId()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------------
    // Test 3: Shortest-distance vs fastest-time criteria producing correct objective
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. Shortest-distance vs fastest-time criteria choose respective optimal objectives")
    void testCriteriaDifference() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);

        // PathFinder where H->loc1 has low distance (5km) but congested duration (50min),
        // while H->loc2 has higher distance (10km) but free-flow duration (10min).
        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            boolean isFastest = "FASTEST_ROUTE".equalsIgnoreCase(crit);
            double dist, time;
            if (from.getId().equals(100L) && to.getId().equals(101L)) {
                dist = 5.0; time = 50.0;
            } else if (from.getId().equals(100L) && to.getId().equals(102L)) {
                dist = 10.0; time = 10.0;
            } else {
                dist = 8.0; time = 15.0;
            }
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(dist).estimatedTime(time).build();
            return OptimizationResult.builder()
                    .source(from).destination(to)
                    .totalDistance(dist).totalEstimatedTime(time)
                    .pathExists(true)
                    .orderedLocations(List.of(from, to))
                    .orderedEdges(List.of(edge))
                    .build();
        };

        // When criterion is SHORTEST_DISTANCE, visit loc1 first (5km vs 10km)
        MultiStopOptimizationResult distResult = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );
        assertThat(distResult.getOrderedDeliveries().get(0).getId()).isEqualTo(1L);

        // When criterion is FASTEST_ROUTE, visit loc2 first (10min vs 50min)
        MultiStopOptimizationResult timeResult = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "FASTEST_ROUTE", false, fixedDeparture, pathFinder
        );
        assertThat(timeResult.getOrderedDeliveries().get(0).getId()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Test 4: Directed-road costs where forward and reverse paths differ
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. Directed road costs: asymmetry between forward and reverse legs is respected")
    void testDirectedAsymmetricCosts() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);

        Map<String, Double> costs = new HashMap<>();
        costs.put("100->101", 10.0); costs.put("101->100", 10.0);
        costs.put("100->102", 10.0); costs.put("102->100", 10.0);
        // One-way asymmetry: 101 -> 102 is 2 km, but 102 -> 101 requires a 25 km detour
        costs.put("101->102", 2.0);
        costs.put("102->101", 25.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            String key = from.getId() + "->" + to.getId();
            double c = costs.getOrDefault(key, 100.0);
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(c).estimatedTime(c).build();
            return OptimizationResult.builder()
                    .source(from).destination(to)
                    .totalDistance(c).totalEstimatedTime(c)
                    .pathExists(true)
                    .orderedLocations(List.of(from, to))
                    .orderedEdges(List.of(edge))
                    .build();
        };

        // Tour H -> loc1 -> loc2 = 10 + 2 = 12 km
        // Reverse H -> loc2 -> loc1 = 10 + 25 = 35 km
        // Algorithm must choose H -> loc1 -> loc2
        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d2, d1), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result.getTotalDistance()).isEqualTo(12.0);
        assertThat(result.getOrderedDeliveries().get(0).getId()).isEqualTo(1L);
        assertThat(result.getOrderedDeliveries().get(1).getId()).isEqualTo(2L);
    }

    // -------------------------------------------------------------------------
    // Test 5: Every requested delivery appears exactly once
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("5. Every requested delivery appears exactly once in the sequence")
    void testEveryDeliveryAppearsExactlyOnce() {
        Delivery d1 = createDelivery(101L, loc1, 1.0);
        Delivery d2 = createDelivery(102L, loc2, 2.0);
        Delivery d3 = createDelivery(103L, loc3, 3.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(5.0).estimatedTime(5.0).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(5.0).totalEstimatedTime(5.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2, d3), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        List<Long> deliveryIds = result.getOrderedDeliveries().stream().map(Delivery::getId).toList();
        assertThat(deliveryIds).hasSize(3).containsExactlyInAnyOrder(101L, 102L, 103L);
        assertThat(new HashSet<>(deliveryIds)).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // Test 6: Reproducible stop order for identical inputs
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("6. Stable tie-breaker guarantees reproducible stop order across repeated executions")
    void testReproducibleStopOrder() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);
        Delivery d3 = createDelivery(3L, loc3, 1.0);

        // Symmetric equidistant graph: all distances 10.0
        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(10.0).estimatedTime(10.0).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(10.0).totalEstimatedTime(10.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        MultiStopOptimizationResult run1 = strategy.optimizeMultiStop(hub, List.of(d1, d2, d3), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder);
        MultiStopOptimizationResult run2 = strategy.optimizeMultiStop(hub, List.of(d1, d2, d3), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder);
        MultiStopOptimizationResult run3 = strategy.optimizeMultiStop(hub, List.of(d1, d2, d3), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder);

        List<Long> order1 = run1.getOrderedDeliveries().stream().map(Delivery::getId).toList();
        List<Long> order2 = run2.getOrderedDeliveries().stream().map(Delivery::getId).toList();
        List<Long> order3 = run3.getOrderedDeliveries().stream().map(Delivery::getId).toList();

        assertThat(order1).isEqualTo(order2);
        assertThat(order2).isEqualTo(order3);
    }

    // -------------------------------------------------------------------------
    // Test 7: Unreachable customer destinations fail safely
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("7. Unreachable customer destinations fail safely with ResourceNotFoundException")
    void testUnreachableDestinationFails() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            if (to.getId().equals(102L)) {
                // loc2 is disconnected / unreachable
                return OptimizationResult.builder().source(from).destination(to).pathExists(false).build();
            }
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(5.0).estimatedTime(5.0).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(5.0).totalEstimatedTime(5.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        assertThrows(ResourceNotFoundException.class, () ->
                strategy.optimizeMultiStop(hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder)
        );
    }

    // -------------------------------------------------------------------------
    // Test 8: Open-tour and return-to-hub behavior
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("8. Open-tour ends at last customer; return-to-hub appends return leg and HUB_RETURN stop")
    void testOpenTourAndReturnToHub() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Delivery d2 = createDelivery(2L, loc2, 1.0);

        // Unambiguous costs:
        // H -> loc1 (10km), loc1 -> loc2 (10km), loc2 -> H (15km), loc1 -> H (20km), H -> loc2 (12km)
        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            double dist;
            if (from.getId().equals(100L) && to.getId().equals(101L)) dist = 10.0;
            else if (from.getId().equals(101L) && to.getId().equals(102L)) dist = 10.0;
            else if (from.getId().equals(102L) && to.getId().equals(100L)) dist = 15.0;
            else if (from.getId().equals(101L) && to.getId().equals(100L)) dist = 20.0;
            else if (from.getId().equals(100L) && to.getId().equals(102L)) dist = 12.0;
            else dist = 10.0;

            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(dist).estimatedTime(dist).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(dist).totalEstimatedTime(dist).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        // Open tour (returnToHub = false): distance = 10 + 10 = 20 km
        MultiStopOptimizationResult openResult = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );
        assertThat(openResult.getTotalDistance()).isEqualTo(20.0);
        assertThat(openResult.isReturnToHub()).isFalse();
        assertThat(openResult.getStops().get(openResult.getStops().size() - 1).getStopType()).isEqualTo(StopType.CUSTOMER_STOP);

        // Return to hub (returnToHub = true): distance = 10 + 10 + 15 = 35 km
        MultiStopOptimizationResult returnResult = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", true, fixedDeparture, pathFinder
        );
        assertThat(returnResult.getTotalDistance()).isEqualTo(35.0);
        assertThat(returnResult.isReturnToHub()).isTrue();
        RouteStop lastStop = returnResult.getStops().get(returnResult.getStops().size() - 1);
        assertThat(lastStop.getStopType()).isEqualTo(StopType.HUB_RETURN);
        assertThat(lastStop.getLocation().getId()).isEqualTo(hub.getId());
    }

    // -------------------------------------------------------------------------
    // Test 9: Real road edges and geometries stitched in correct order
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("9. Real road edges and GeoJSON geometries are sequentially stitched without duplicates")
    void testRoadEdgeAndGeometryStitching() {
        Delivery d1 = createDelivery(1L, loc1, 1.0);
        Location intermediateWp = Location.builder().id(999L).name("Waypoint Junction").latitude(12.975).longitude(77.598).build();

        // Path from Hub to C1 traverses intermediate waypoint: Hub -> Wp -> C1
        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            LocationEdge e1 = LocationEdge.builder().sourceLocation(from).destinationLocation(intermediateWp).distance(3.0).estimatedTime(5.0).geometryGeojson("{\"type\":\"LineString\",\"coordinates\":[[77.5946,12.9716],[77.598,12.975]]}").build();
            LocationEdge e2 = LocationEdge.builder().sourceLocation(intermediateWp).destinationLocation(to).distance(4.0).estimatedTime(6.0).geometryGeojson("{\"type\":\"LineString\",\"coordinates\":[[77.598,12.975],[77.600,12.980]]}").build();
            return OptimizationResult.builder()
                    .source(from).destination(to)
                    .totalDistance(7.0).totalEstimatedTime(11.0)
                    .pathExists(true)
                    .orderedLocations(List.of(from, intermediateWp, to))
                    .orderedEdges(List.of(e1, e2))
                    .build();
        };

        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result.getEdges()).hasSize(2);
        assertThat(result.getEdges().get(0).getEdgeOrder()).isEqualTo(1);
        assertThat(result.getEdges().get(0).getGeometryGeojson()).contains("77.5946");
        assertThat(result.getEdges().get(1).getEdgeOrder()).isEqualTo(2);
        assertThat(result.getEdges().get(1).getGeometryGeojson()).contains("77.600");

        // Stops sequence: Hub (SOURCE), Waypoint (WAYPOINT), C1 (CUSTOMER_STOP)
        assertThat(result.getStops()).hasSize(3);
        assertThat(result.getStops().get(0).getStopType()).isEqualTo(StopType.SOURCE);
        assertThat(result.getStops().get(1).getStopType()).isEqualTo(StopType.WAYPOINT);
        assertThat(result.getStops().get(2).getStopType()).isEqualTo(StopType.CUSTOMER_STOP);
    }

    // -------------------------------------------------------------------------
    // Test 10: Cumulative customer ETAs and route totals
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("10. Cumulative customer ETAs and route totals are mathematically exact")
    void testCumulativeEtasAndRouteTotals() {
        Delivery d1 = createDelivery(1L, loc1, 1.0); // Leg 1: 10 mins
        Delivery d2 = createDelivery(2L, loc2, 1.0); // Leg 2: 20 mins

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            double time = from.getId().equals(100L) ? 10.0 : 20.0;
            double dist = time * 0.5;
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(dist).estimatedTime(time).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(dist).totalEstimatedTime(time).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        // Departure: 09:00:00
        // C1 ETA: 09:00 + 10 mins = 09:10:00
        // C2 ETA: 09:10 + 20 mins = 09:30:00
        CustomerStopResponse c1 = result.getCustomerStops().get(0);
        CustomerStopResponse c2 = result.getCustomerStops().get(1);

        assertThat(c1.getLegDuration()).isEqualTo(10.0);
        assertThat(c1.getCumulativeDuration()).isEqualTo(10.0);
        assertThat(c1.getEstimatedArrivalTime()).isEqualTo(fixedDeparture.plusMinutes(10));

        assertThat(c2.getLegDuration()).isEqualTo(20.0);
        assertThat(c2.getCumulativeDuration()).isEqualTo(30.0);
        assertThat(c2.getEstimatedArrivalTime()).isEqualTo(fixedDeparture.plusMinutes(30));

        assertThat(result.getTotalEstimatedTime()).isEqualTo(30.0);
        assertThat(result.getEstimatedCompletionTime()).isEqualTo(fixedDeparture.plusMinutes(30));
    }

    // -------------------------------------------------------------------------
    // Test 11: Duplicate destinations retain individual delivery IDs and stop numbers
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("11. Duplicate customer destinations retain individual delivery IDs and separate stop numbers")
    void testDuplicateDestinationsRetainDistinctDeliveries() {
        // Both d1 and d2 have the exact same delivery destination (e.g. two packages to same building)
        Delivery d1 = createDelivery(10L, loc1, 1.5);
        Delivery d2 = createDelivery(20L, loc1, 2.5);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            LocationEdge edge = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(6.0).estimatedTime(8.0).build();
            return OptimizationResult.builder().source(from).destination(to).totalDistance(6.0).totalEstimatedTime(8.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(edge)).build();
        };

        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result.getCustomerStops()).hasSize(2);

        CustomerStopResponse stop1 = result.getCustomerStops().get(0);
        CustomerStopResponse stop2 = result.getCustomerStops().get(1);

        assertThat(stop1.getCustomerStopNumber()).isEqualTo(1);
        assertThat(stop1.getDeliveryId()).isEqualTo(10L);
        assertThat(stop1.getLocationId()).isEqualTo(loc1.getId());
        assertThat(stop1.getLegDistance()).isEqualTo(6.0);

        assertThat(stop2.getCustomerStopNumber()).isEqualTo(2);
        assertThat(stop2.getDeliveryId()).isEqualTo(20L);
        assertThat(stop2.getLocationId()).isEqualTo(loc1.getId());
        // Zero road transit between identical consecutive locations
        assertThat(stop2.getLegDistance()).isEqualTo(0.0);
        assertThat(stop2.getLegDuration()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Test 12: Asymmetric directed graph with sink destination and open tour
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("12. Directed graph with one-way sink customer node succeeds on open tour without phantom return edge")
    void testDirectedGraphWithSinkCustomerNodeSucceedsOnOpenTour() {
        // Linear directed graph: Hub -> C1 -> C2 (C2 is a sink with no outgoing edges)
        Delivery d1 = createDelivery(1L, loc1, 2.0);
        Delivery d2 = createDelivery(2L, loc2, 3.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinder = (from, to, crit) -> {
            // Only directed forward edges exist: Hub -> C1, C1 -> C2, Hub -> C2
            if (from.getId().equals(hub.getId()) && to.getId().equals(loc1.getId())) {
                LocationEdge e = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(5.0).estimatedTime(7.0).build();
                return OptimizationResult.builder().source(from).destination(to).totalDistance(5.0).totalEstimatedTime(7.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(e)).build();
            }
            if (from.getId().equals(loc1.getId()) && to.getId().equals(loc2.getId())) {
                LocationEdge e = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(4.0).estimatedTime(6.0).build();
                return OptimizationResult.builder().source(from).destination(to).totalDistance(4.0).totalEstimatedTime(6.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(e)).build();
            }
            if (from.getId().equals(hub.getId()) && to.getId().equals(loc2.getId())) {
                LocationEdge e = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(12.0).estimatedTime(15.0).build();
                return OptimizationResult.builder().source(from).destination(to).totalDistance(12.0).totalEstimatedTime(15.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(e)).build();
            }
            // Reverse edges (C2 -> C1, C2 -> Hub, C1 -> Hub) do NOT exist
            return OptimizationResult.builder().source(from).destination(to).pathExists(false).build();
        };

        // Open tour (returnToHub = false): successfully finds Hub -> C1 -> C2 (5.0 + 4.0 = 9.0 km)
        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinder
        );

        assertThat(result).isNotNull();
        assertThat(result.getTotalDistance()).isEqualTo(9.0);
        assertThat(result.getOrderedDeliveries().get(0).getId()).isEqualTo(1L);
        assertThat(result.getOrderedDeliveries().get(1).getId()).isEqualTo(2L);
        assertThat(result.isReturnToHub()).isFalse();

        // returnToHub = true MUST fail safely because C2 cannot return to Hub
        assertThrows(ResourceNotFoundException.class, () ->
                strategy.optimizeMultiStop(hub, List.of(d1, d2), "SHORTEST_DISTANCE", true, fixedDeparture, pathFinder)
        );
    }

    // -------------------------------------------------------------------------
    // Test 13: Dijkstra throwing ResourceNotFoundException on missing directed legs
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("13. Dijkstra strategy throwing ResourceNotFoundException on missing legs is safely handled in ATSP matrix")
    void testDijkstraExceptionSafelyHandledInAtspMatrix() {
        Delivery d1 = createDelivery(1L, loc1, 2.0);
        Delivery d2 = createDelivery(2L, loc2, 3.0);

        MultiStopRouteOptimizationStrategy.PathFinder pathFinderWithExceptions = (from, to, crit) -> {
            if (from.getId().equals(hub.getId()) && to.getId().equals(loc1.getId())) {
                LocationEdge e = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(5.0).estimatedTime(7.0).build();
                return OptimizationResult.builder().source(from).destination(to).totalDistance(5.0).totalEstimatedTime(7.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(e)).build();
            }
            if (from.getId().equals(loc1.getId()) && to.getId().equals(loc2.getId())) {
                LocationEdge e = LocationEdge.builder().sourceLocation(from).destinationLocation(to).distance(4.0).estimatedTime(6.0).build();
                return OptimizationResult.builder().source(from).destination(to).totalDistance(4.0).totalEstimatedTime(6.0).pathExists(true).orderedLocations(List.of(from, to)).orderedEdges(List.of(e)).build();
            }
            // All other directed pairs throw ResourceNotFoundException (Dijkstra behavior)
            throw new ResourceNotFoundException("No path exists between " + from.getName() + " and " + to.getName());
        };

        // Open tour succeeds
        MultiStopOptimizationResult result = strategy.optimizeMultiStop(
                hub, List.of(d1, d2), "SHORTEST_DISTANCE", false, fixedDeparture, pathFinderWithExceptions
        );
        assertThat(result).isNotNull();
        assertThat(result.getTotalDistance()).isEqualTo(9.0);

        // Closed tour throws ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () ->
                strategy.optimizeMultiStop(hub, List.of(d1, d2), "SHORTEST_DISTANCE", true, fixedDeparture, pathFinderWithExceptions)
        );
    }
}
