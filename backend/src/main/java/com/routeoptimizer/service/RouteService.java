package com.routeoptimizer.service;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.enums.StopType;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.optimizer.MultiStopOptimizationResult;
import com.routeoptimizer.optimizer.MultiStopRouteOptimizationStrategy;
import com.routeoptimizer.optimizer.OptimizationResult;
import com.routeoptimizer.optimizer.RouteOptimizationStrategy;
import com.routeoptimizer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteOptimizationStrategy routeOptimizationStrategy;
    private final MultiStopRouteOptimizationStrategy multiStopRouteOptimizationStrategy;
    private final DeliveryRepository deliveryRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteEdgeRepository routeEdgeRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final VehicleRepository vehicleRepository;

    @Transactional
    public OptimizationResponse optimizeAndPersistRoute(OptimizationRequest request, String currentUsername) {
        Delivery delivery = null;

        if (request.getDeliveryId() != null) {
            delivery = deliveryRepository.findById(request.getDeliveryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + request.getDeliveryId()));

            if (!Boolean.TRUE.equals(delivery.getActive())) {
                throw new BadRequestException("Delivery is inactive: " + request.getDeliveryId());
            }

            if (delivery.getPackage() == null || !Boolean.TRUE.equals(delivery.getPackage().getActive())) {
                throw new BadRequestException("Delivery has no valid active package associated");
            }

            // Only allow optimization before delivery reaches IN_PROGRESS (CREATED and ASSIGNED allowed)
            if (delivery.getStatus() == DeliveryStatus.IN_PROGRESS ||
                    delivery.getStatus() == DeliveryStatus.COMPLETED ||
                    delivery.getStatus() == DeliveryStatus.FAILED ||
                    delivery.getStatus() == DeliveryStatus.CANCELLED) {
                throw new ConflictException("Cannot optimize or re-optimize route for delivery in status: " + delivery.getStatus());
            }

            // Check consistency if startLocationId was explicitly provided
            if (request.getStartLocationId() != null && delivery.getSourceLocation() != null &&
                    !request.getStartLocationId().equals(delivery.getSourceLocation().getId())) {
                throw new BadRequestException("Start location ID " + request.getStartLocationId() +
                        " does not match delivery source location ID " + delivery.getSourceLocation().getId());
            }

            // Check consistency if destinationLocationId was explicitly provided
            if (request.getDestinationLocationId() != null && delivery.getDestinationLocation() != null &&
                    !request.getDestinationLocationId().equals(delivery.getDestinationLocation().getId())) {
                throw new BadRequestException("Destination location ID " + request.getDestinationLocationId() +
                        " does not match delivery destination location ID " + delivery.getDestinationLocation().getId());
            }

            // Derive start and destination from delivery if not explicitly provided
            if (request.getStartLocationId() == null && delivery.getSourceLocation() != null) {
                request.setStartLocationId(delivery.getSourceLocation().getId());
            }
            if (request.getDestinationLocationId() == null && delivery.getDestinationLocation() != null) {
                request.setDestinationLocationId(delivery.getDestinationLocation().getId());
            }
        } else {
            if (request.getStartLocationId() == null || request.getDestinationLocationId() == null) {
                throw new BadRequestException("startLocationId and destinationLocationId must be provided for standalone optimization");
            }
        }

        // Execute Dijkstra optimization
        OptimizationResult result = routeOptimizationStrategy.optimize(request);

        Long routeId = null;
        Integer versionNumber = null;

        if (delivery != null) {
            // Manage route versioning
            List<Route> existingRoutes = routeRepository.findByDeliveryIdOrderByVersionNumberDesc(delivery.getId());
            int nextVersion = 1;

            for (Route r : existingRoutes) {
                if (Boolean.TRUE.equals(r.getIsCurrent())) {
                    r.setIsCurrent(false);
                    routeRepository.save(r);
                }
            }

            if (!existingRoutes.isEmpty()) {
                nextVersion = existingRoutes.get(0).getVersionNumber() + 1;
            }

            versionNumber = nextVersion;

            Route route = Route.builder()
                    .delivery(delivery)
                    .sourceLocation(result.getSource())
                    .destinationLocation(result.getDestination())
                    .versionNumber(versionNumber)
                    .isCurrent(true)
                    .totalDistance(result.getTotalDistance())
                    .estimatedTime(result.getTotalEstimatedTime())
                    .algorithmUsed("DIJKSTRA")
                    .build();

            Route savedRoute = routeRepository.save(route);
            routeId = savedRoute.getId();

            // Persist ordered stops
            List<Location> orderedLocations = result.getOrderedLocations();
            for (int i = 0; i < orderedLocations.size(); i++) {
                StopType type = (i == 0) ? StopType.SOURCE :
                        (i == orderedLocations.size() - 1) ? StopType.DESTINATION : StopType.WAYPOINT;

                RouteStop stop = RouteStop.builder()
                        .route(savedRoute)
                        .location(orderedLocations.get(i))
                        .stopOrder(i + 1)
                        .stopType(type)
                        .build();
                routeStopRepository.save(stop);
            }

            // Persist ordered edge snapshots
            List<LocationEdge> orderedEdges = result.getOrderedEdges();
            for (int i = 0; i < orderedEdges.size(); i++) {
                RouteEdge routeEdge = RouteEdge.fromLocationEdge(savedRoute, i + 1, orderedEdges.get(i));
                routeEdgeRepository.save(routeEdge);
            }

            // Synchronize estimated metrics onto delivery
            delivery.setEstimatedDistance(result.getTotalDistance());
            delivery.setEstimatedTime(result.getTotalEstimatedTime());
            deliveryRepository.save(delivery);
        }

        return OptimizationResponse.builder()
                .algorithm(result.getAlgorithm())
                .sourceLocationId(result.getSource().getId())
                .sourceLocationName(result.getSource().getName())
                .destinationLocationId(result.getDestination().getId())
                .destinationLocationName(result.getDestination().getName())
                .totalDistance(result.getTotalDistance())
                .totalEstimatedTime(result.getTotalEstimatedTime())
                .pathExists(result.isPathExists())
                .orderedLocationIds(result.getOrderedLocations().stream().map(Location::getId).toList())
                .orderedLocationNames(result.getOrderedLocations().stream().map(Location::getName).toList())
                .orderedEdges(result.getOrderedEdges().stream().map(e -> RouteEdgeResponse.builder()
                        .sourceLocationId(e.getSourceLocation().getId())
                        .sourceLocationName(e.getSourceLocation().getName())
                        .destinationLocationId(e.getDestinationLocation().getId())
                        .destinationLocationName(e.getDestinationLocation().getName())
                        .distance(e.getDistance())
                        .estimatedTime(e.getEstimatedTime())
                        .geometryGeojson(e.getGeometryGeojson())
                        .build()).toList())
                .routeId(routeId)
                .versionNumber(versionNumber)
                .criterion(result.getCriterion())
                .explanation(result.getExplanation())
                .build();
    }

    @Transactional(readOnly = true)
    public RouteResponse getRouteById(Long routeId, String currentUsername) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));

        validateRouteAccess(route, currentUsername);
        return mapToRouteResponse(route);
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getRoutesByDeliveryId(Long deliveryId, String currentUsername) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        validateDeliveryAccess(delivery, currentUsername);

        List<Route> routes = routeRepository.findByDeliveryIdOrderByVersionNumberDesc(deliveryId);
        return routes.stream().map(this::mapToRouteResponse).toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse getCurrentRouteByDeliveryId(Long deliveryId, String currentUsername) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        validateDeliveryAccess(delivery, currentUsername);

        Route route = routeRepository.findByDeliveryIdAndIsCurrentTrue(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("No current active route found for delivery: " + deliveryId));

        return mapToRouteResponse(route);
    }

    private void validateRouteAccess(Route route, String currentUsername) {
        if (route.getDelivery() != null) {
            validateDeliveryAccess(route.getDelivery(), currentUsername);
        }
    }

    private void validateDeliveryAccess(Delivery delivery, String currentUsername) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        if (user.getRole() == Role.DRIVER) {
            if (delivery.getDriver() == null || delivery.getDriver().getUser() == null ||
                    !delivery.getDriver().getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied: You are not assigned to this delivery");
            }
        } else if (user.getRole() == Role.USER) {
            if (delivery.getPackage() == null || delivery.getPackage().getCustomer() == null ||
                    !delivery.getPackage().getCustomer().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied: You are not the owner of this delivery");
            }
        }
    }

    public RouteResponse mapToRouteResponse(Route r) {
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(r.getId());
        List<RouteEdge> edges = routeEdgeRepository.findByRouteIdOrderByEdgeOrderAsc(r.getId());

        return RouteResponse.builder()
                .id(r.getId())
                .deliveryId(r.getDelivery() != null ? r.getDelivery().getId() : null)
                .sourceLocationId(r.getSourceLocation().getId())
                .sourceLocationName(r.getSourceLocation().getName())
                .destinationLocationId(r.getDestinationLocation().getId())
                .destinationLocationName(r.getDestinationLocation().getName())
                .versionNumber(r.getVersionNumber())
                .isCurrent(r.getIsCurrent())
                .totalDistance(r.getTotalDistance())
                .estimatedTime(r.getEstimatedTime())
                .algorithmUsed(r.getAlgorithmUsed())
                .active(r.getActive())
                .version(r.getVersion())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .stops(stops.stream().map(s -> RouteStopResponse.builder()
                        .id(s.getId())
                        .stopOrder(s.getStopOrder())
                        .stopType(s.getStopType())
                        .locationId(s.getLocation().getId())
                        .locationName(s.getLocation().getName())
                        .latitude(s.getLocation().getLatitude())
                        .longitude(s.getLocation().getLongitude())
                        .estimatedArrivalTime(s.getEstimatedArrivalTime())
                        .build()).toList())
                .edges(edges.stream().map(e -> RouteEdgeResponse.builder()
                        .id(e.getId())
                        .edgeOrder(e.getEdgeOrder())
                        .sourceLocationId(e.getSourceLocation().getId())
                        .sourceLocationName(e.getSourceLocation().getName())
                        .destinationLocationId(e.getDestinationLocation().getId())
                        .destinationLocationName(e.getDestinationLocation().getName())
                        .distance(e.getDistance())
                        .estimatedTime(e.getEstimatedTime())
                        .geometryGeojson(e.getGeometryGeojson())
                        .build()).toList())
                .build();
    }

    @Transactional
    public MultiStopOptimizationResponse optimizeAndPersistMultiDeliveryRoute(
            MultiStopOptimizationRequest request,
            String currentUsername) {

        if (request.getHubLocationId() == null) {
            throw new BadRequestException("Central hub location ID must not be null");
        }
        if (request.getDeliveryIds() == null || request.getDeliveryIds().isEmpty()) {
            throw new BadRequestException("Delivery IDs list must not be empty");
        }
        if (request.getReturnToHub() == null) {
            throw new BadRequestException("Explicit returnToHub selection must be specified (true or false)");
        }

        // Validate uniqueness of requested delivery IDs
        Set<Long> uniqueDeliveryIds = new HashSet<>(request.getDeliveryIds());
        if (uniqueDeliveryIds.size() != request.getDeliveryIds().size()) {
            throw new BadRequestException("Duplicate delivery IDs provided in multi-stop route request");
        }

        // Validate hub location
        Location hub = locationRepository.findById(request.getHubLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Central hub location not found with id: " + request.getHubLocationId()));
        if (!Boolean.TRUE.equals(hub.getActive())) {
            throw new BadRequestException("Central hub location is inactive: " + hub.getName());
        }

        // Validate each delivery
        List<Delivery> deliveries = new ArrayList<>();
        double totalWeightKg = 0.0;
        for (Long dId : request.getDeliveryIds()) {
            Delivery delivery = deliveryRepository.findById(dId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + dId));

            if (!Boolean.TRUE.equals(delivery.getActive())) {
                throw new BadRequestException("Delivery is inactive: " + dId);
            }

            if (delivery.getPackage() == null || !Boolean.TRUE.equals(delivery.getPackage().getActive())) {
                throw new BadRequestException("Delivery " + dId + " has no valid active package associated");
            }

            if (delivery.getStatus() == DeliveryStatus.IN_PROGRESS ||
                    delivery.getStatus() == DeliveryStatus.COMPLETED ||
                    delivery.getStatus() == DeliveryStatus.FAILED ||
                    delivery.getStatus() == DeliveryStatus.CANCELLED) {
                throw new ConflictException("Cannot optimize route for delivery " + dId + " in status: " + delivery.getStatus());
            }

            if (delivery.getSourceLocation() == null || !hub.getId().equals(delivery.getSourceLocation().getId())) {
                throw new BadRequestException("Delivery " + dId + " source location (" +
                        (delivery.getSourceLocation() != null ? delivery.getSourceLocation().getId() : "null") +
                        ") does not match central hub location ID " + hub.getId());
            }

            if (delivery.getDestinationLocation() == null || !Boolean.TRUE.equals(delivery.getDestinationLocation().getActive())) {
                throw new BadRequestException("Delivery " + dId + " destination location is null or inactive");
            }

            totalWeightKg += delivery.getPackage().getWeight();
            deliveries.add(delivery);
        }

        // Validate vehicle capacity if vehicleId is provided
        Double vehicleCapacityKg = null;
        if (request.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

            if (!Boolean.TRUE.equals(vehicle.getActive())) {
                throw new BadRequestException("Vehicle is inactive: " + vehicle.getRegistrationNumber());
            }

            vehicleCapacityKg = vehicle.getCapacity();
            if (totalWeightKg > vehicleCapacityKg) {
                throw new BadRequestException(String.format(
                        "Combined package weight (%.2f kg) exceeds vehicle payload capacity (%.2f kg) for vehicle %s",
                        totalWeightKg, vehicleCapacityKg, vehicle.getRegistrationNumber()));
            }
        }

        // Run multi-stop optimization
        OffsetDateTime departureTime = request.getDepartureTime() != null ? request.getDepartureTime() : OffsetDateTime.now();
        MultiStopOptimizationResult result = multiStopRouteOptimizationStrategy.optimizeMultiStop(
                hub,
                deliveries,
                request.getCriterion(),
                request.getReturnToHub(),
                departureTime
        );

        // Versioning: Mark any current routes associated with these deliveries as inactive
        int nextVersion = 1;
        for (Delivery d : deliveries) {
            List<Route> existingRoutes = routeRepository.findByDeliveryIdOrderByVersionNumberDesc(d.getId());
            for (Route r : existingRoutes) {
                if (Boolean.TRUE.equals(r.getIsCurrent())) {
                    r.setIsCurrent(false);
                    routeRepository.save(r);
                }
                if (r.getVersionNumber() >= nextVersion) {
                    nextVersion = r.getVersionNumber() + 1;
                }
            }
        }

        // Persist Route
        // routes.delivery_id retained for backward compatibility (set to primary / first delivery)
        Route route = Route.builder()
                .delivery(deliveries.get(0))
                .sourceLocation(hub)
                .destinationLocation(result.getFinalLocation())
                .versionNumber(nextVersion)
                .isCurrent(true)
                .totalDistance(result.getTotalDistance())
                .estimatedTime(result.getTotalEstimatedTime())
                .algorithmUsed("MULTI_STOP_2OPT")
                .build();

        Route savedRoute = routeRepository.save(route);

        // Persist Stops
        List<RouteStopResponse> allStopResponses = new ArrayList<>();
        for (RouteStop s : result.getStops()) {
            s.setRoute(savedRoute);
            RouteStop savedStop = routeStopRepository.save(s);
            allStopResponses.add(RouteStopResponse.builder()
                    .id(savedStop.getId())
                    .stopOrder(savedStop.getStopOrder())
                    .stopType(savedStop.getStopType())
                    .locationId(savedStop.getLocation().getId())
                    .locationName(savedStop.getLocation().getName())
                    .latitude(savedStop.getLocation().getLatitude())
                    .longitude(savedStop.getLocation().getLongitude())
                    .estimatedArrivalTime(savedStop.getEstimatedArrivalTime())
                    .build());
        }

        // Persist Edges
        List<RouteEdgeResponse> orderedEdgeResponses = new ArrayList<>();
        for (RouteEdge e : result.getEdges()) {
            e.setRoute(savedRoute);
            RouteEdge savedEdge = routeEdgeRepository.save(e);
            orderedEdgeResponses.add(RouteEdgeResponse.builder()
                    .id(savedEdge.getId())
                    .edgeOrder(savedEdge.getEdgeOrder())
                    .sourceLocationId(savedEdge.getSourceLocation().getId())
                    .sourceLocationName(savedEdge.getSourceLocation().getName())
                    .destinationLocationId(savedEdge.getDestinationLocation().getId())
                    .destinationLocationName(savedEdge.getDestinationLocation().getName())
                    .distance(savedEdge.getDistance())
                    .estimatedTime(savedEdge.getEstimatedTime())
                    .geometryGeojson(savedEdge.getGeometryGeojson())
                    .build());
        }

        // Associate all deliveries to the persisted route manifest
        for (Delivery d : deliveries) {
            d.setRoute(savedRoute);
            deliveryRepository.save(d);
        }

        List<Long> orderedDeliveryIds = result.getOrderedDeliveries().stream().map(Delivery::getId).toList();

        return MultiStopOptimizationResponse.builder()
                .routeId(savedRoute.getId())
                .versionNumber(nextVersion)
                .algorithm(result.getAlgorithm())
                .hubLocationId(hub.getId())
                .hubLocationName(hub.getName())
                .totalDistance(result.getTotalDistance())
                .totalEstimatedTime(result.getTotalEstimatedTime())
                .departureTime(result.getDepartureTime())
                .estimatedCompletionTime(result.getEstimatedCompletionTime())
                .returnToHub(result.isReturnToHub())
                .orderedDeliveryIds(orderedDeliveryIds)
                .customerStops(result.getCustomerStops())
                .allStops(allStopResponses)
                .orderedEdges(orderedEdgeResponses)
                .totalWeightKg(totalWeightKg)
                .vehicleCapacityKg(vehicleCapacityKg)
                .criterion(result.getCriterion())
                .explanation(result.getExplanation())
                .build();
    }
}
