package com.routeoptimizer.service;

import com.routeoptimizer.dto.LocationEdgeRequest;
import com.routeoptimizer.dto.LocationEdgeResponse;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LocationEdgeService {

    private final LocationEdgeRepository locationEdgeRepository;
    private final LocationRepository locationRepository;
    private final RoutingService routingService;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationEdgeResponse createEdge(LocationEdgeRequest request) {
        if (request.getSourceLocationId().equals(request.getDestinationLocationId())) {
            throw new BadRequestException("Source location and destination location must be distinct.");
        }

        if (request.getDistance() == null || request.getDistance() <= 0) {
            throw new BadRequestException("Distance must be greater than zero.");
        }

        if (request.getEstimatedTime() != null && request.getEstimatedTime() < 0) {
            throw new BadRequestException("Estimated time must be non-negative.");
        }

        Location source = locationRepository.findById(request.getSourceLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + request.getSourceLocationId()));
        if (!Boolean.TRUE.equals(source.getActive())) {
            throw new BadRequestException("Cannot create edge: source location is inactive.");
        }

        Location destination = locationRepository.findById(request.getDestinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));
        if (!Boolean.TRUE.equals(destination.getActive())) {
            throw new BadRequestException("Cannot create edge: destination location is inactive.");
        }

        Optional<LocationEdge> existingOpt = locationEdgeRepository
                .findBySourceLocationIdAndDestinationLocationId(source.getId(), destination.getId());

        if (existingOpt.isPresent()) {
            LocationEdge existing = existingOpt.get();
            if (Boolean.TRUE.equals(existing.getActive())) {
                throw new ConflictException("Directed edge from " + source.getName() + " to " + destination.getName() + " already exists.");
            }
            // Reactivate previously deactivated edge with new parameters
            existing.setActive(true);
            existing.setDistance(request.getDistance());
            existing.setEstimatedTime(request.getEstimatedTime() != null ? request.getEstimatedTime() : (request.getDistance() / 40.0 * 60.0));
            if (request.getGeometryGeojson() != null) {
                existing.setGeometryGeojson(request.getGeometryGeojson());
            }
            LocationEdge saved = locationEdgeRepository.save(existing);
            return LocationEdgeResponse.fromEntity(saved);
        }

        Double estimatedTime = request.getEstimatedTime() != null ? request.getEstimatedTime() : (request.getDistance() / 40.0) * 60.0;

        LocationEdge edge = LocationEdge.builder()
                .sourceLocation(source)
                .destinationLocation(destination)
                .distance(request.getDistance())
                .estimatedTime(estimatedTime)
                .geometryGeojson(request.getGeometryGeojson())
                .build();
        edge.setActive(true);
        edge.setVersion(0L);

        LocationEdge saved = locationEdgeRepository.save(edge);
        return LocationEdgeResponse.fromEntity(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    @Transactional(readOnly = true)
    public LocationEdgeResponse getEdgeById(Long id) {
        LocationEdge edge = locationEdgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Edge not found with id: " + id));
        return LocationEdgeResponse.fromEntity(edge);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    @Transactional(readOnly = true)
    public Page<LocationEdgeResponse> getAllEdges(Long sourceLocationId, Long destinationLocationId, Boolean active, Pageable pageable) {
        Page<LocationEdge> page;
        if (sourceLocationId != null) {
            page = locationEdgeRepository.findBySourceLocationId(sourceLocationId, pageable);
        } else if (destinationLocationId != null) {
            page = locationEdgeRepository.findByDestinationLocationId(destinationLocationId, pageable);
        } else if (active != null) {
            page = locationEdgeRepository.findByActive(active, pageable);
        } else {
            page = locationEdgeRepository.findAll(pageable);
        }

        return page.map(LocationEdgeResponse::fromEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationEdgeResponse updateEdge(Long id, LocationEdgeRequest request) {
        LocationEdge edge = locationEdgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Edge not found with id: " + id));

        if (request.getDistance() == null || request.getDistance() <= 0) {
            throw new BadRequestException("Distance must be greater than zero.");
        }

        if (request.getEstimatedTime() != null && request.getEstimatedTime() < 0) {
            throw new BadRequestException("Estimated time must be non-negative.");
        }

        if (request.getSourceLocationId() != null && request.getDestinationLocationId() != null) {
            if (request.getSourceLocationId().equals(request.getDestinationLocationId())) {
                throw new BadRequestException("Source location and destination location must be distinct.");
            }

            if (locationEdgeRepository.existsBySourceLocationIdAndDestinationLocationIdAndIdNot(
                    request.getSourceLocationId(), request.getDestinationLocationId(), id)) {
                throw new ConflictException("Another directed edge between these locations already exists.");
            }

            Location source = locationRepository.findById(request.getSourceLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + request.getSourceLocationId()));
            if (!Boolean.TRUE.equals(source.getActive())) {
                throw new BadRequestException("Cannot update edge: source location is inactive.");
            }

            Location destination = locationRepository.findById(request.getDestinationLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));
            if (!Boolean.TRUE.equals(destination.getActive())) {
                throw new BadRequestException("Cannot update edge: destination location is inactive.");
            }

            edge.setSourceLocation(source);
            edge.setDestinationLocation(destination);
        }

        edge.setDistance(request.getDistance());
        edge.setEstimatedTime(request.getEstimatedTime() != null ? request.getEstimatedTime() : (request.getDistance() / 40.0) * 60.0);
        if (request.getGeometryGeojson() != null) {
            edge.setGeometryGeojson(request.getGeometryGeojson());
        }

        LocationEdge saved = locationEdgeRepository.save(edge);
        return LocationEdgeResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationEdgeResponse deactivateEdge(Long id) {
        LocationEdge edge = locationEdgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Edge not found with id: " + id));

        edge.setActive(false);
        LocationEdge saved = locationEdgeRepository.save(edge);
        return LocationEdgeResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public com.routeoptimizer.dto.RoutingResponse calculateRoute(Long sourceId, Long destinationId) {
        Location source = locationRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found"));
        Location destination = locationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found"));
        
        return routingService.calculateRoute(
            source.getLatitude(), source.getLongitude(),
            destination.getLatitude(), destination.getLongitude()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<LocationEdgeResponse> syncRoadEdges(Long sourceId, Long destinationId, boolean bidirectional) {
        if (sourceId == null || destinationId == null) {
            throw new BadRequestException("Source and destination location IDs must not be null.");
        }

        if (sourceId.equals(destinationId)) {
            throw new BadRequestException("Source location and destination location must be distinct.");
        }

        Location source = locationRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + sourceId));
        if (!Boolean.TRUE.equals(source.getActive())) {
            throw new BadRequestException("Cannot sync edge: source location is inactive.");
        }

        Location destination = locationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + destinationId));
        if (!Boolean.TRUE.equals(destination.getActive())) {
            throw new BadRequestException("Cannot sync edge: destination location is inactive.");
        }

        List<LocationEdgeResponse> results = new java.util.ArrayList<>();

        // Forward edge: source -> destination
        results.add(syncSingleDirectedEdge(source, destination));

        // Reverse edge: destination -> source if bidirectional requested
        if (bidirectional) {
            results.add(syncSingleDirectedEdge(destination, source));
        }

        return results;
    }

    private LocationEdgeResponse syncSingleDirectedEdge(Location from, Location to) {
        com.routeoptimizer.dto.RoutingResponse route = routingService.calculateRoute(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude()
        );

        Optional<LocationEdge> existingOpt = locationEdgeRepository
                .findBySourceLocationIdAndDestinationLocationId(from.getId(), to.getId());

        LocationEdge edge;
        if (existingOpt.isPresent()) {
            edge = existingOpt.get();
            edge.setActive(true);
            edge.setDistance(route.getDistanceKm());
            edge.setEstimatedTime(route.getDurationMin());
            edge.setGeometryGeojson(route.getGeometryGeoJson());
        } else {
            edge = LocationEdge.builder()
                    .sourceLocation(from)
                    .destinationLocation(to)
                    .distance(route.getDistanceKm())
                    .estimatedTime(route.getDurationMin())
                    .geometryGeojson(route.getGeometryGeoJson())
                    .build();
            edge.setActive(true);
            edge.setVersion(0L);
        }

        LocationEdge saved = locationEdgeRepository.save(edge);
        return LocationEdgeResponse.fromEntity(saved);
    }
}
