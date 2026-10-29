package com.routeoptimizer.service;

import com.routeoptimizer.dto.LocationRequest;
import com.routeoptimizer.dto.LocationResponse;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final LocationEdgeRepository locationEdgeRepository;
    private final PackageRepository packageRepository;
    private final DeliveryRepository deliveryRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationResponse createLocation(LocationRequest request) {
        String trimmedName = request.getName().trim();
        if (locationRepository.existsByName(trimmedName)) {
            throw new ConflictException("Location with name '" + trimmedName + "' already exists.");
        }

        Location location = Location.builder()
                .name(trimmedName)
                .city(request.getCity())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .locationType(request.getLocationType())
                .build();
        location.setActive(true);
        location.setVersion(0L);

        Location saved = locationRepository.save(location);
        return LocationResponse.fromEntity(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    @Transactional(readOnly = true)
    public LocationResponse getLocationById(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));
        return LocationResponse.fromEntity(location);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    @Transactional(readOnly = true)
    public Page<LocationResponse> getAllLocations(String name, Boolean active, Pageable pageable) {
        Page<Location> page;
        boolean hasName = StringUtils.hasText(name);

        if (active != null && hasName) {
            page = locationRepository.findByActiveAndNameContainingIgnoreCase(active, name.trim(), pageable);
        } else if (active != null) {
            page = locationRepository.findByActive(active, pageable);
        } else if (hasName) {
            page = locationRepository.findByNameContainingIgnoreCase(name.trim(), pageable);
        } else {
            page = locationRepository.findAll(pageable);
        }

        return page.map(LocationResponse::fromEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationResponse updateLocation(Long id, LocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));

        String trimmedName = request.getName().trim();
        if (locationRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new ConflictException("Location with name '" + trimmedName + "' already exists.");
        }

        location.setName(trimmedName);
        location.setCity(request.getCity());
        location.setAddress(request.getAddress());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setLocationType(request.getLocationType());

        Location saved = locationRepository.save(location);
        return LocationResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationResponse deactivateLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));

        // Prevent deactivation if referenced by active packages
        if (packageRepository.existsActiveByLocationIdAndStatuses(id, List.of(PackageStatus.PENDING, PackageStatus.IN_DELIVERY))) {
            throw new ConflictException("Cannot deactivate location: referenced by active packages.");
        }

        // Prevent deactivation if referenced by active deliveries
        if (deliveryRepository.existsActiveByLocationIdAndStatuses(id, List.of(DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS))) {
            throw new ConflictException("Cannot deactivate location: referenced by active deliveries.");
        }

        location.setActive(false);
        Location saved = locationRepository.save(location);

        // Deactivate connected edges to maintain route topology integrity
        List<LocationEdge> connectedEdges = locationEdgeRepository.findBySourceLocationIdOrDestinationLocationId(id, id);
        for (LocationEdge edge : connectedEdges) {
            if (Boolean.TRUE.equals(edge.getActive())) {
                edge.setActive(false);
                locationEdgeRepository.save(edge);
            }
        }

        return LocationResponse.fromEntity(saved);
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationResponse activateLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));

        location.setActive(true);
        Location saved = locationRepository.save(location);
        return LocationResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LocationResponse approveLocation(Long id, com.routeoptimizer.dto.ApproveLocationRequest request) {
        Location pendingLocation = locationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + id));

        if (Boolean.TRUE.equals(pendingLocation.getActive())) {
            throw new ConflictException("Location is already active.");
        }

        if (request.getConnectToLocationId() == null) {
            throw new com.routeoptimizer.exception.BadRequestException("Must provide a location to connect to.");
        }
        
        Location existingLocation = locationRepository.findById(request.getConnectToLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Existing location not found with id: " + request.getConnectToLocationId()));

        if (!Boolean.TRUE.equals(existingLocation.getActive())) {
            throw new com.routeoptimizer.exception.BadRequestException("Cannot connect to an inactive location.");
        }

        boolean incoming = Boolean.TRUE.equals(request.getCreateIncomingEdge());
        boolean outgoing = Boolean.TRUE.equals(request.getCreateOutgoingEdge());

        if (!incoming && !outgoing) {
            throw new com.routeoptimizer.exception.BadRequestException("Must create at least one connection (incoming or outgoing).");
        }

        // Activate first so edges can be created (though it's in the same transaction)
        pendingLocation.setActive(true);
        Location saved = locationRepository.save(pendingLocation);

        if (incoming) {
            if (request.getIncomingDistance() == null || request.getIncomingDistance() <= 0) {
                throw new com.routeoptimizer.exception.BadRequestException("Invalid incoming distance.");
            }
            LocationEdge inEdge = LocationEdge.builder()
                    .sourceLocation(existingLocation)
                    .destinationLocation(saved)
                    .distance(request.getIncomingDistance())
                    .estimatedTime(request.getIncomingEstimatedTime())
                    .geometryGeojson(request.getIncomingGeometryGeojson())
                    .build();
            inEdge.setActive(true);
            
            // Recalculate time if missing (same logic as edge service)
            if (inEdge.getEstimatedTime() == null) {
                double speedKmh = 40.0;
                double timeHours = inEdge.getDistance() / speedKmh;
                inEdge.setEstimatedTime(timeHours * 60.0);
            }
            locationEdgeRepository.save(inEdge);
        }

        if (outgoing) {
            if (request.getOutgoingDistance() == null || request.getOutgoingDistance() <= 0) {
                throw new com.routeoptimizer.exception.BadRequestException("Invalid outgoing distance.");
            }
            LocationEdge outEdge = LocationEdge.builder()
                    .sourceLocation(saved)
                    .destinationLocation(existingLocation)
                    .distance(request.getOutgoingDistance())
                    .estimatedTime(request.getOutgoingEstimatedTime())
                    .geometryGeojson(request.getOutgoingGeometryGeojson())
                    .build();
            outEdge.setActive(true);
            
            if (outEdge.getEstimatedTime() == null) {
                double speedKmh = 40.0;
                double timeHours = outEdge.getDistance() / speedKmh;
                outEdge.setEstimatedTime(timeHours * 60.0);
            }
            locationEdgeRepository.save(outEdge);
        }

        return LocationResponse.fromEntity(saved);
    }
}
