package com.routeoptimizer.optimizer;

import com.routeoptimizer.dto.CustomerStopResponse;
import com.routeoptimizer.dto.OptimizationRequest;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.enums.AlgorithmType;
import com.routeoptimizer.enums.StopType;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Multi-stop delivery route optimization engine using the Two-Tier Pattern:
 * 1. Road-Cost Matrix: Computes exact directed shortest/fastest paths across real active graph edges using Dijkstra.
 * 2. Stop Sequencing: Solves Traveling Salesperson (TSP) ordering using Nearest Neighbor + 2-Opt on directed road costs.
 * 3. Route Reconstruction: Stitches consecutive road paths, numbers customer delivery stops, and computes cumulative ETAs.
 */
@Component
@RequiredArgsConstructor
public class MultiStopRouteOptimizationStrategy {

    private final DijkstraRouteOptimizationStrategy dijkstraStrategy;
    private final LocationRepository locationRepository;
    private final LocationEdgeRepository locationEdgeRepository;

    @FunctionalInterface
    public interface PathFinder {
        OptimizationResult findPath(Location from, Location to, String criterion);
    }

    public MultiStopOptimizationResult optimizeMultiStop(
            Location hubLocation,
            List<Delivery> deliveries,
            String criterion,
            boolean returnToHub,
            OffsetDateTime departureTime) {
        PathFinder defaultPathFinder = (from, to, crit) -> dijkstraStrategy.optimize(
                OptimizationRequest.builder()
                        .startLocationId(from.getId())
                        .destinationLocationId(to.getId())
                        .criterion(crit)
                        .build()
        );
        return optimizeMultiStop(hubLocation, deliveries, criterion, returnToHub, departureTime, defaultPathFinder);
    }

    public MultiStopOptimizationResult optimizeMultiStop(
            Location hubLocation,
            List<Delivery> deliveries,
            String criterion,
            boolean returnToHub,
            OffsetDateTime departureTime,
            PathFinder pathFinder) {

        if (hubLocation == null) {
            throw new BadRequestException("Hub location must not be null");
        }
        if (deliveries == null || deliveries.isEmpty()) {
            throw new BadRequestException("Deliveries list must not be empty");
        }

        OffsetDateTime effectiveDepartureTime = departureTime != null ? departureTime : OffsetDateTime.now();
        String effectiveCriterion = criterion != null ? criterion.toUpperCase() : "SHORTEST_DISTANCE";
        boolean isFastest = "FASTEST_ROUTE".equals(effectiveCriterion);

        int n = deliveries.size();

        // ---------------------------------------------------------------------
        // Stage A: Construct Road Cost Matrix across Hub and Customer Destinations
        // ---------------------------------------------------------------------
        // Collect unique locations to minimize Dijkstra executions
        Map<Long, Location> uniqueLocations = new HashMap<>();
        uniqueLocations.put(hubLocation.getId(), hubLocation);
        for (Delivery d : deliveries) {
            if (d.getDestinationLocation() == null) {
                throw new BadRequestException("Delivery " + d.getId() + " has null destination location");
            }
            uniqueLocations.put(d.getDestinationLocation().getId(), d.getDestinationLocation());
        }

        // Cache Dijkstra shortest paths between all pairs of unique locations
        Map<String, OptimizationResult> pathCache = new HashMap<>();
        for (Location locA : uniqueLocations.values()) {
            for (Location locB : uniqueLocations.values()) {
                if (!locA.getId().equals(locB.getId())) {
                    String key = locA.getId() + "->" + locB.getId();
                    OptimizationResult legResult = null;
                    try {
                        legResult = pathFinder.findPath(locA, locB, effectiveCriterion);
                    } catch (ResourceNotFoundException ignored) {
                        // Directed path does not exist in graph; costFn will assign penalty 1e9
                    }
                    if (legResult != null && legResult.isPathExists()) {
                        pathCache.put(key, legResult);
                    }
                }
            }
        }

        // Helper cost function between stop indices: index 0 is Hub, indices 1..N are deliveries[0..N-1]
        // cost(fromIndex, toIndex):
        CostFunction costFn = (fromIdx, toIdx) -> {
            if (fromIdx == toIdx) {
                return 0.0;
            }
            Location locA = (fromIdx == 0) ? hubLocation : deliveries.get(fromIdx - 1).getDestinationLocation();
            Location locB = (toIdx == 0) ? hubLocation : deliveries.get(toIdx - 1).getDestinationLocation();

            if (locA.getId().equals(locB.getId())) {
                return 0.0;
            }

            OptimizationResult leg = pathCache.get(locA.getId() + "->" + locB.getId());
            if (leg == null || !leg.isPathExists()) {
                return 1e9;
            }
            return isFastest ? leg.getTotalEstimatedTime() : leg.getTotalDistance();
        };

        // ---------------------------------------------------------------------
        // Stage B: Optimize Customer Stop Order (Nearest Neighbor + 2-Opt on Directed Graph)
        // ---------------------------------------------------------------------
        // 1. Nearest Neighbor Initial Tour: delivery indices 1..N
        List<Integer> tour = new ArrayList<>();
        Set<Integer> unvisited = new HashSet<>();
        for (int i = 1; i <= n; i++) {
            unvisited.add(i);
        }

        int current = 0; // Starts at Hub (index 0)
        while (!unvisited.isEmpty()) {
            int bestNext = -1;
            double bestCost = Double.MAX_VALUE;

            // Sort candidate list to ensure deterministic evaluation order
            List<Integer> candidates = new ArrayList<>(unvisited);
            candidates.sort(Comparator.comparingLong((Integer idx) -> deliveries.get(idx - 1).getDestinationLocation().getId())
                    .thenComparingLong(idx -> deliveries.get(idx - 1).getId()));

            for (int candidate : candidates) {
                double c = costFn.getCost(current, candidate);
                if (c < bestCost - 1e-6) {
                    bestCost = c;
                    bestNext = candidate;
                }
            }

            tour.add(bestNext);
            unvisited.remove(bestNext);
            current = bestNext;
        }

        // 2. 2-Opt Local Search for Directed Graph
        // Re-evaluates exact directed tour cost on any subsegment inversion
        boolean improved = true;
        int maxPasses = 500;
        int pass = 0;
        double currentTotalCost = evaluateTourCost(tour, costFn, returnToHub);

        while (improved && pass < maxPasses) {
            improved = false;
            pass++;

            for (int i = 0; i < tour.size() - 1; i++) {
                for (int j = i + 1; j < tour.size(); j++) {
                    List<Integer> candidateTour = reverseSubsegment(tour, i, j);
                    double candidateCost = evaluateTourCost(candidateTour, costFn, returnToHub);

                    if (candidateCost < currentTotalCost - 1e-6) {
                        tour = candidateTour;
                        currentTotalCost = candidateCost;
                        improved = true;
                        break;
                    } else if (Math.abs(candidateCost - currentTotalCost) <= 1e-6) {
                        // Stable tie-breaker: compare delivery IDs lexicographically
                        if (isLexicographicallySmaller(candidateTour, tour, deliveries)) {
                            tour = candidateTour;
                            improved = true;
                            break;
                        }
                    }
                }
                if (improved) {
                    break;
                }
            }
        }

        if (currentTotalCost >= 1e8) {
            throw new ResourceNotFoundException("No continuous road tour exists connecting all specified delivery destinations.");
        }

        // Ordered deliveries in optimized customer visitation sequence
        List<Delivery> orderedDeliveries = new ArrayList<>();
        for (int idx : tour) {
            orderedDeliveries.add(deliveries.get(idx - 1));
        }

        // ---------------------------------------------------------------------
        // Stage C & D: Reconstruct Real Road Route, ETAs, and Cumulative Metrics
        // ---------------------------------------------------------------------
        List<RouteStop> stopsList = new ArrayList<>();
        List<RouteEdge> edgesList = new ArrayList<>();
        List<CustomerStopResponse> customerResponses = new ArrayList<>();

        int stopOrderCounter = 1;
        int edgeOrderCounter = 1;
        double cumulativeDistance = 0.0;
        double cumulativeMinutes = 0.0;

        // 1. Hub Origin Stop
        RouteStop hubStartStop = RouteStop.builder()
                .stopOrder(stopOrderCounter++)
                .stopType(StopType.SOURCE)
                .location(hubLocation)
                .estimatedArrivalTime(effectiveDepartureTime)
                .delivery(null)
                .customerStopNumber(null)
                .build();
        stopsList.add(hubStartStop);

        // 2. Stitch Road Legs across successive Customer Destinations
        for (int k = 0; k < orderedDeliveries.size(); k++) {
            Delivery delivery = orderedDeliveries.get(k);
            Location fromLoc = (k == 0) ? hubLocation : orderedDeliveries.get(k - 1).getDestinationLocation();
            Location toLoc = delivery.getDestinationLocation();

            double legDistance = 0.0;
            double legDuration = 0.0;

            if (!fromLoc.getId().equals(toLoc.getId())) {
                OptimizationResult leg = pathCache.get(fromLoc.getId() + "->" + toLoc.getId());
                if (leg == null || !leg.isPathExists()) {
                    throw new ResourceNotFoundException("No road path exists between location '" +
                            fromLoc.getName() + "' and '" + toLoc.getName() + "'");
                }
                List<Location> legLocations = leg.getOrderedLocations();
                List<LocationEdge> legEdges = leg.getOrderedEdges();

                // Append road edges and compute cumulative leg metrics
                for (LocationEdge le : legEdges) {
                    RouteEdge re = RouteEdge.builder()
                            .edgeOrder(edgeOrderCounter++)
                            .sourceLocation(le.getSourceLocation())
                            .destinationLocation(le.getDestinationLocation())
                            .distance(le.getDistance())
                            .estimatedTime(le.getEstimatedTime())
                            .geometryGeojson(le.getGeometryGeojson())
                            .build();
                    edgesList.add(re);

                    legDistance += le.getDistance();
                    double durationMins = le.getEstimatedTime() != null ? le.getEstimatedTime() : (le.getDistance() / 40.0) * 60.0;
                    legDuration += durationMins;
                    cumulativeDistance += le.getDistance();
                    cumulativeMinutes += durationMins;
                }

                // Add intermediate graph waypoints (excluding start vertex and destination vertex)
                for (int w = 1; w < legLocations.size() - 1; w++) {
                    Location waypointLoc = legLocations.get(w);
                    OffsetDateTime wpEta = effectiveDepartureTime.plusSeconds(Math.round(cumulativeMinutes * 60.0));
                    RouteStop wpStop = RouteStop.builder()
                            .stopOrder(stopOrderCounter++)
                            .stopType(StopType.WAYPOINT)
                            .location(waypointLoc)
                            .estimatedArrivalTime(wpEta)
                            .delivery(null)
                            .customerStopNumber(null)
                            .build();
                    stopsList.add(wpStop);
                }
            }

            // Customer Stop Milestone
            int customerStopNumber = k + 1;
            OffsetDateTime customerEta = effectiveDepartureTime.plusSeconds(Math.round(cumulativeMinutes * 60.0));
            StopType stopType = StopType.CUSTOMER_STOP;

            RouteStop customerStop = RouteStop.builder()
                    .stopOrder(stopOrderCounter++)
                    .stopType(stopType)
                    .location(toLoc)
                    .estimatedArrivalTime(customerEta)
                    .delivery(delivery)
                    .customerStopNumber(customerStopNumber)
                    .build();
            stopsList.add(customerStop);

            customerResponses.add(CustomerStopResponse.builder()
                    .customerStopNumber(customerStopNumber)
                    .deliveryId(delivery.getId())
                    .packageId(delivery.getPackage() != null ? delivery.getPackage().getId() : null)
                    .trackingNumber(delivery.getPackage() != null ? delivery.getPackage().getTrackingNumber() : null)
                    .locationId(toLoc.getId())
                    .locationName(toLoc.getName())
                    .latitude(toLoc.getLatitude())
                    .longitude(toLoc.getLongitude())
                    .legDistance(legDistance)
                    .legDuration(legDuration)
                    .cumulativeDistance(cumulativeDistance)
                    .cumulativeDuration(cumulativeMinutes)
                    .estimatedArrivalTime(customerEta)
                    .packageWeight(delivery.getPackage() != null ? delivery.getPackage().getWeight() : null)
                    .build());
        }

        // 3. Return to Central Hub leg (if explicitly requested)
        Location finalLocation = orderedDeliveries.get(orderedDeliveries.size() - 1).getDestinationLocation();
        if (returnToHub) {
            Location lastCustomerLoc = finalLocation;
            if (!lastCustomerLoc.getId().equals(hubLocation.getId())) {
                OptimizationResult returnLeg = pathCache.get(lastCustomerLoc.getId() + "->" + hubLocation.getId());
                if (returnLeg == null || !returnLeg.isPathExists()) {
                    throw new ResourceNotFoundException("No road path exists returning from location '" +
                            lastCustomerLoc.getName() + "' to hub '" + hubLocation.getName() + "'");
                }
                List<Location> returnLocations = returnLeg.getOrderedLocations();
                List<LocationEdge> returnEdges = returnLeg.getOrderedEdges();

                for (LocationEdge le : returnEdges) {
                    RouteEdge re = RouteEdge.builder()
                            .edgeOrder(edgeOrderCounter++)
                            .sourceLocation(le.getSourceLocation())
                            .destinationLocation(le.getDestinationLocation())
                            .distance(le.getDistance())
                            .estimatedTime(le.getEstimatedTime())
                            .geometryGeojson(le.getGeometryGeojson())
                            .build();
                    edgesList.add(re);

                    cumulativeDistance += le.getDistance();
                    double durationMins = le.getEstimatedTime() != null ? le.getEstimatedTime() : (le.getDistance() / 40.0) * 60.0;
                    cumulativeMinutes += durationMins;
                }

                for (int w = 1; w < returnLocations.size() - 1; w++) {
                    Location waypointLoc = returnLocations.get(w);
                    OffsetDateTime wpEta = effectiveDepartureTime.plusSeconds(Math.round(cumulativeMinutes * 60.0));
                    RouteStop wpStop = RouteStop.builder()
                            .stopOrder(stopOrderCounter++)
                            .stopType(StopType.WAYPOINT)
                            .location(waypointLoc)
                            .estimatedArrivalTime(wpEta)
                            .delivery(null)
                            .customerStopNumber(null)
                            .build();
                    stopsList.add(wpStop);
                }
            }

            OffsetDateTime returnEta = effectiveDepartureTime.plusSeconds(Math.round(cumulativeMinutes * 60.0));
            RouteStop hubReturnStop = RouteStop.builder()
                    .stopOrder(stopOrderCounter++)
                    .stopType(StopType.HUB_RETURN)
                    .location(hubLocation)
                    .estimatedArrivalTime(returnEta)
                    .delivery(null)
                    .customerStopNumber(null)
                    .build();
            stopsList.add(hubReturnStop);
            finalLocation = hubLocation;
        }

        OffsetDateTime completionTime = effectiveDepartureTime.plusSeconds(Math.round(cumulativeMinutes * 60.0));
        String explanation = String.format("Multi-stop delivery route optimized via Nearest Neighbor + 2-Opt local search over Dijkstra road paths (%s). Total stops: %d customer deliveries%s.",
                isFastest ? "Fastest Travel Time" : "Shortest Road Distance",
                orderedDeliveries.size(),
                returnToHub ? " with Hub return" : " (open tour)");

        return MultiStopOptimizationResult.builder()
                .algorithm(AlgorithmType.MULTI_STOP_2OPT)
                .hubLocation(hubLocation)
                .finalLocation(finalLocation)
                .totalDistance(cumulativeDistance)
                .totalEstimatedTime(cumulativeMinutes)
                .departureTime(effectiveDepartureTime)
                .estimatedCompletionTime(completionTime)
                .returnToHub(returnToHub)
                .orderedDeliveries(orderedDeliveries)
                .customerStops(customerResponses)
                .stops(stopsList)
                .edges(edgesList)
                .criterion(effectiveCriterion)
                .explanation(explanation)
                .build();
    }

    private double evaluateTourCost(List<Integer> tour, CostFunction costFn, boolean returnToHub) {
        if (tour.isEmpty()) {
            return 0.0;
        }
        double total = costFn.getCost(0, tour.get(0)); // Hub to first delivery
        for (int i = 0; i < tour.size() - 1; i++) {
            total += costFn.getCost(tour.get(i), tour.get(i + 1));
        }
        if (returnToHub) {
            total += costFn.getCost(tour.get(tour.size() - 1), 0); // Last delivery back to Hub
        }
        return total;
    }

    private List<Integer> reverseSubsegment(List<Integer> tour, int i, int j) {
        List<Integer> result = new ArrayList<>(tour);
        while (i < j) {
            int temp = result.get(i);
            result.set(i, result.get(j));
            result.set(j, temp);
            i++;
            j--;
        }
        return result;
    }

    private boolean isLexicographicallySmaller(List<Integer> candidate, List<Integer> current, List<Delivery> deliveries) {
        for (int k = 0; k < candidate.size(); k++) {
            Long candDeliveryId = deliveries.get(candidate.get(k) - 1).getId();
            Long currDeliveryId = deliveries.get(current.get(k) - 1).getId();
            int cmp = candDeliveryId.compareTo(currDeliveryId);
            if (cmp != 0) {
                return cmp < 0;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface CostFunction {
        double getCost(int fromIdx, int toIdx);
    }
}
