package com.routeoptimizer.optimizer;

import com.routeoptimizer.dto.OptimizationRequest;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.enums.AlgorithmType;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DijkstraRouteOptimizationStrategy implements RouteOptimizationStrategy {

    private final LocationRepository locationRepository;
    private final LocationEdgeRepository locationEdgeRepository;

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.DIJKSTRA;
    }

    @Override
    public OptimizationResult optimize(OptimizationRequest request) {
        if (request.getStartLocationId() == null || request.getDestinationLocationId() == null) {
            throw new BadRequestException("Start and destination location IDs must not be null");
        }

        if (request.getStartLocationId().equals(request.getDestinationLocationId())) {
            throw new BadRequestException("Start location and destination location must be distinct");
        }

        Location start = locationRepository.findById(request.getStartLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Start location not found with id: " + request.getStartLocationId()));

        Location destination = locationRepository.findById(request.getDestinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));

        if (!Boolean.TRUE.equals(start.getActive())) {
            throw new BadRequestException("Start location is inactive: " + start.getName());
        }

        if (!Boolean.TRUE.equals(destination.getActive())) {
            throw new BadRequestException("Destination location is inactive: " + destination.getName());
        }

        String criterion = request.getCriterion() != null ? request.getCriterion().toUpperCase() : "SHORTEST_DISTANCE";
        boolean isFastest = "FASTEST_ROUTE".equals(criterion);

        // Fetch all active edges to construct the directed graph
        List<LocationEdge> activeEdges = locationEdgeRepository.findByActiveTrue();

        Map<Long, List<LocationEdge>> adjacencyList = new HashMap<>();
        for (LocationEdge edge : activeEdges) {
            if (Boolean.TRUE.equals(edge.getSourceLocation().getActive()) &&
                    Boolean.TRUE.equals(edge.getDestinationLocation().getActive())) {
                adjacencyList.computeIfAbsent(edge.getSourceLocation().getId(), k -> new ArrayList<>()).add(edge);
            }
        }

        // Dijkstra shortest-path algorithm
        Map<Long, Double> distances = new HashMap<>();
        Map<Long, LocationEdge> predecessors = new HashMap<>();
        PriorityQueue<NodeDistance> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.distance));
        Set<Long> visited = new HashSet<>();

        distances.put(start.getId(), 0.0);
        pq.add(new NodeDistance(start.getId(), 0.0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            Long currentNodeId = current.nodeId;

            if (visited.contains(currentNodeId)) {
                continue;
            }
            visited.add(currentNodeId);

            if (currentNodeId.equals(destination.getId())) {
                break;
            }

            List<LocationEdge> outgoingEdges = adjacencyList.getOrDefault(currentNodeId, Collections.emptyList());
            for (LocationEdge edge : outgoingEdges) {
                Long neighborId = edge.getDestinationLocation().getId();
                if (visited.contains(neighborId)) {
                    continue;
                }

                Double edgeWeight;
                if (isFastest) {
                    edgeWeight = edge.getEstimatedTime();
                } else {
                    edgeWeight = edge.getDistance();
                }

                if (edgeWeight == null) {
                    continue; // Skip if metric is missing
                }

                double newDist = distances.get(currentNodeId) + edgeWeight;
                if (newDist < distances.getOrDefault(neighborId, Double.POSITIVE_INFINITY)) {
                    distances.put(neighborId, newDist);
                    predecessors.put(neighborId, edge);
                    pq.add(new NodeDistance(neighborId, newDist));
                }
            }
        }

        if (!predecessors.containsKey(destination.getId())) {
            throw new ResourceNotFoundException("No path exists between location '" + start.getName() + "' and '" + destination.getName() + "'");
        }

        // Reconstruct path
        LinkedList<LocationEdge> pathEdges = new LinkedList<>();
        Long curr = destination.getId();
        while (!curr.equals(start.getId())) {
            LocationEdge edge = predecessors.get(curr);
            if (edge == null) {
                break;
            }
            pathEdges.addFirst(edge);
            curr = edge.getSourceLocation().getId();
        }

        List<Location> orderedLocations = new ArrayList<>();
        orderedLocations.add(start);
        double totalDistance = 0.0;
        double totalEstimatedTime = 0.0;

        for (LocationEdge edge : pathEdges) {
            orderedLocations.add(edge.getDestinationLocation());
            totalDistance += edge.getDistance();
            if (edge.getEstimatedTime() != null) {
                totalEstimatedTime += edge.getEstimatedTime();
            }
        }

        String explanation = isFastest ? "Fastest Route optimized for minimum travel time." : "Shortest Distance route optimized for minimum kilometres.";

        return OptimizationResult.builder()
                .algorithm(AlgorithmType.DIJKSTRA)
                .source(start)
                .destination(destination)
                .orderedLocations(orderedLocations)
                .orderedEdges(pathEdges)
                .totalDistance(totalDistance)
                .totalEstimatedTime(totalEstimatedTime)
                .pathExists(true)
                .criterion(isFastest ? "FASTEST_ROUTE" : "SHORTEST_DISTANCE")
                .explanation(explanation)
                .build();
    }

    private static class NodeDistance {
        final Long nodeId;
        final double distance;

        NodeDistance(Long nodeId, double distance) {
            this.nodeId = nodeId;
            this.distance = distance;
        }
    }
}
