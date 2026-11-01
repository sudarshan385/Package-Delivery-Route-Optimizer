package com.routeoptimizer.service;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final PackageRepository packageRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteEdgeRepository routeEdgeRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public DeliveryResponse createDelivery(CreateDeliveryRequest request, String currentUsername) {
        Package pkg = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + request.getPackageId()));

        if (!Boolean.TRUE.equals(pkg.getActive())) {
            throw new BadRequestException("Package is inactive: " + pkg.getTrackingNumber());
        }

        if (pkg.getStatus() != PackageStatus.PENDING) {
            throw new BadRequestException("Package must be in status PENDING to schedule a delivery (current status: " + pkg.getStatus() + ")");
        }

        // Active delivery statuses: CREATED, ASSIGNED, IN_PROGRESS
        boolean hasActiveDelivery = deliveryRepository.existsByPackageIdAndStatusInAndActiveTrue(
                pkg.getId(),
                List.of(DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS)
        );

        if (hasActiveDelivery) {
            throw new ConflictException("Package already has an active delivery (tracking number: " + pkg.getTrackingNumber() + ")");
        }

        Location sourceLocation;
        if (request.getSourceLocationId() != null) {
            sourceLocation = locationRepository.findById(request.getSourceLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + request.getSourceLocationId()));
            if (!Boolean.TRUE.equals(sourceLocation.getActive())) {
                throw new BadRequestException("Cannot create delivery: source location is inactive: " + sourceLocation.getName());
            }
        } else {
            sourceLocation = pkg.getSourceLocation();
        }

        if (sourceLocation == null) {
            throw new BadRequestException("Source location cannot be resolved for delivery creation.");
        }
        if (!Boolean.TRUE.equals(sourceLocation.getActive())) {
            throw new BadRequestException("Cannot create delivery: source location is inactive: " + sourceLocation.getName());
        }
        if (pkg.getDestinationLocation() == null) {
            throw new BadRequestException("Destination location cannot be resolved for delivery creation.");
        }
        if (sourceLocation.getId().equals(pkg.getDestinationLocation().getId())) {
            throw new BadRequestException("Source location and destination location must be distinct.");
        }

        Delivery delivery = Delivery.builder()
                .pkg(pkg)
                .sourceLocation(sourceLocation)
                .destinationLocation(pkg.getDestinationLocation())
                .status(DeliveryStatus.CREATED)
                .notes(request.getNotes())
                .build();

        // Ensure package status is PENDING
        pkg.setStatus(PackageStatus.PENDING);
        packageRepository.save(pkg);

        Delivery saved = deliveryRepository.save(delivery);

        // Record initial status history
        User currentUser = getCurrentUser(currentUsername);
        DeliveryStatusHistory history = DeliveryStatusHistory.builder()
                .delivery(saved)
                .previousStatus(null)
                .newStatus(DeliveryStatus.CREATED)
                .changedByUser(currentUser)
                .reason(request.getNotes() != null ? request.getNotes() : "Initial delivery creation")
                .build();
        deliveryStatusHistoryRepository.save(history);

        return mapToDeliveryResponse(saved);
    }

    @Transactional
    public DeliveryResponse assignDelivery(Long deliveryId, AssignDeliveryRequest request, String currentUsername) {
        // Concurrency-safe pessimistic locking on delivery, driver, and vehicle
        Delivery delivery = deliveryRepository.findByIdWithLock(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (!Boolean.TRUE.equals(delivery.getActive())) {
            throw new BadRequestException("Delivery is inactive");
        }

        if (delivery.getStatus() != DeliveryStatus.CREATED) {
            throw new ConflictException("Delivery is not in CREATED status; current status: " + delivery.getStatus());
        }

        Driver driver = driverRepository.findByIdWithLock(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));

        if (!Boolean.TRUE.equals(driver.getActive())) {
            throw new BadRequestException("Driver is inactive: " + driver.getName());
        }

        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new ConflictException("Driver is not available; current status: " + driver.getStatus());
        }

        Vehicle vehicle = vehicleRepository.findByIdWithLock(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));

        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new BadRequestException("Vehicle is inactive: " + vehicle.getRegistrationNumber());
        }

        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not available; current status: " + vehicle.getStatus());
        }

        // Apply state transitions
        delivery.setDriver(driver);
        delivery.setVehicle(vehicle);
        delivery.setStatus(DeliveryStatus.ASSIGNED);

        driver.setStatus(DriverStatus.ON_DELIVERY);
        vehicle.setStatus(VehicleStatus.IN_USE);

        Package pkg = delivery.getPackage();
        pkg.setStatus(PackageStatus.IN_DELIVERY);

        if (request.getNotes() != null) {
            delivery.setNotes(request.getNotes());
        }

        driverRepository.save(driver);
        vehicleRepository.save(vehicle);
        packageRepository.save(pkg);
        Delivery saved = deliveryRepository.save(delivery);

        User currentUser = getCurrentUser(currentUsername);
        DeliveryStatusHistory history = DeliveryStatusHistory.builder()
                .delivery(saved)
                .previousStatus(DeliveryStatus.CREATED)
                .newStatus(DeliveryStatus.ASSIGNED)
                .changedByUser(currentUser)
                .reason(request.getNotes() != null ? request.getNotes() : "Driver and vehicle assigned")
                .build();
        deliveryStatusHistoryRepository.save(history);

        return mapToDeliveryResponse(saved);
    }

    @Transactional
    public DeliveryResponse updateDeliveryStatus(Long deliveryId, DeliveryStatusUpdateRequest request, String currentUsername) {
        Delivery delivery = deliveryRepository.findByIdWithLock(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (!Boolean.TRUE.equals(delivery.getActive())) {
            throw new BadRequestException("Delivery is inactive");
        }

        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() == Role.DRIVER) {
            // Driver can only update deliveries assigned to them
            if (delivery.getDriver() == null || delivery.getDriver().getUser() == null ||
                    !delivery.getDriver().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not assigned to this delivery");
            }

            // Driver cannot cancel
            if (request.getStatus() == DeliveryStatus.CANCELLED) {
                throw new AccessDeniedException("Access denied: Drivers cannot cancel deliveries");
            }
        }

        DeliveryStatus currentStatus = delivery.getStatus();
        DeliveryStatus targetStatus = request.getStatus();

        // Enforce state machine transitions
        validateTransition(currentStatus, targetStatus);

        // Package synchronization & resource release
        Package pkg = delivery.getPackage();

        switch (targetStatus) {
            case IN_PROGRESS -> pkg.setStatus(PackageStatus.IN_DELIVERY);
            case COMPLETED -> {
                pkg.setStatus(PackageStatus.DELIVERED);
                delivery.setCompletedAt(OffsetDateTime.now());
                if (delivery.getRoute() != null) {
                    Route route = delivery.getRoute();
                    boolean hasOtherNonTerminal = deliveryRepository.existsActiveNonTerminalByRouteIdExcluding(
                            route.getId(),
                            delivery.getId(),
                            List.of(DeliveryStatus.COMPLETED, DeliveryStatus.FAILED, DeliveryStatus.CANCELLED)
                    );
                    if (!hasOtherNonTerminal) {
                        route.setStatus("RETURNING_TO_HUB");
                        routeRepository.save(route);
                        if (delivery.getDriver() != null && delivery.getDriver().getStatus() == DriverStatus.ON_DELIVERY) {
                            Driver d = delivery.getDriver();
                            d.setStatus(DriverStatus.AVAILABLE);
                            driverRepository.save(d);
                        }
                        if (delivery.getVehicle() != null && delivery.getVehicle().getStatus() == VehicleStatus.IN_USE) {
                            Vehicle v = delivery.getVehicle();
                            v.setStatus(VehicleStatus.AVAILABLE);
                            vehicleRepository.save(v);
                        }
                    }
                } else {
                    if (delivery.getDriver() != null && delivery.getDriver().getStatus() == DriverStatus.ON_DELIVERY) {
                        Driver d = delivery.getDriver();
                        d.setStatus(DriverStatus.AVAILABLE);
                        driverRepository.save(d);
                    }
                    if (delivery.getVehicle() != null && delivery.getVehicle().getStatus() == VehicleStatus.IN_USE) {
                        Vehicle v = delivery.getVehicle();
                        v.setStatus(VehicleStatus.AVAILABLE);
                        vehicleRepository.save(v);
                    }
                }
                // Resources remain occupied (Driver ON_DELIVERY, Vehicle IN_USE) until Return to Hub is confirmed.
            }
            case FAILED -> {
                pkg.setStatus(PackageStatus.FAILED);
                releaseResourcesIfNoCompletedDeliveries(delivery);
            }
            case CANCELLED -> {
                pkg.setStatus(PackageStatus.PENDING);
                releaseResourcesIfNoCompletedDeliveries(delivery);
            }
            default -> { /* No-op */ }
        }

        delivery.setStatus(targetStatus);
        packageRepository.save(pkg);
        Delivery saved = deliveryRepository.save(delivery);

        DeliveryStatusHistory history = DeliveryStatusHistory.builder()
                .delivery(saved)
                .previousStatus(currentStatus)
                .newStatus(targetStatus)
                .changedByUser(currentUser)
                .reason(request.getReason())
                .build();
        deliveryStatusHistoryRepository.save(history);

        return mapToDeliveryResponse(saved);
    }

    private void validateTransition(DeliveryStatus current, DeliveryStatus target) {
        if (current == target) {
            throw new ConflictException("Delivery is already in status: " + current);
        }

        boolean valid = switch (current) {
            case CREATED -> (target == DeliveryStatus.ASSIGNED || target == DeliveryStatus.CANCELLED);
            case ASSIGNED -> (target == DeliveryStatus.IN_PROGRESS || target == DeliveryStatus.CANCELLED);
            case IN_PROGRESS -> (target == DeliveryStatus.COMPLETED || target == DeliveryStatus.FAILED);
            case COMPLETED, CANCELLED, FAILED -> false; // Terminal states
        };

        if (!valid) {
            throw new ConflictException("Invalid delivery status transition from " + current + " to " + target);
        }
    }

    private void releaseResourcesIfNoCompletedDeliveries(Delivery delivery) {
        if (delivery.getRoute() != null) {
            boolean hasOtherNonTerminal = deliveryRepository.existsActiveNonTerminalByRouteIdExcluding(
                    delivery.getRoute().getId(),
                    delivery.getId(),
                    List.of(DeliveryStatus.COMPLETED, DeliveryStatus.FAILED, DeliveryStatus.CANCELLED)
            );
            if (hasOtherNonTerminal) {
                return;
            }

            // Check if any delivery on this route was COMPLETED
            List<Delivery> routeDeliveries = deliveryRepository.findByRouteIdAndActiveTrue(delivery.getRoute().getId());
            boolean anyCompleted = routeDeliveries.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.COMPLETED);
            if (anyCompleted) {
                // Since at least one parcel was delivered, the driver is returning to hub.
                delivery.getRoute().setStatus("RETURNING_TO_HUB");
                routeRepository.save(delivery.getRoute());
                return;
            }
        }

        // Only release driver and vehicle if tour was completely aborted/cancelled with 0 deliveries
        if (delivery.getDriver() != null && delivery.getDriver().getStatus() == DriverStatus.ON_DELIVERY) {
            Driver driver = delivery.getDriver();
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
        }
        if (delivery.getVehicle() != null && delivery.getVehicle().getStatus() == VehicleStatus.IN_USE) {
            Vehicle vehicle = delivery.getVehicle();
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
        }
    }

    @Transactional
    public MultiStopManifestResponse confirmRouteReturnToHub(Long routeId, String currentUsername) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));

        if ("COMPLETED".equalsIgnoreCase(route.getStatus()) && route.getReturnedToHubAt() != null) {
            return buildManifestResponse(route);
        }

        route.setStatus("COMPLETED");
        route.setReturnedToHubAt(OffsetDateTime.now());
        route.setReturnConfirmedBy(currentUsername != null ? currentUsername : "SYSTEM");
        routeRepository.save(route);

        List<Delivery> routeDeliveries = deliveryRepository.findByRouteIdAndActiveTrue(route.getId());
        for (Delivery d : routeDeliveries) {
            if (d.getReturnConfirmedAt() == null) {
                d.setReturnConfirmedAt(OffsetDateTime.now());
                deliveryRepository.save(d);
            }
            if (d.getDriver() != null && d.getDriver().getStatus() == DriverStatus.ON_DELIVERY) {
                Driver driver = d.getDriver();
                driver.setStatus(DriverStatus.AVAILABLE);
                driverRepository.save(driver);
            }
            if (d.getVehicle() != null && d.getVehicle().getStatus() == VehicleStatus.IN_USE) {
                Vehicle vehicle = d.getVehicle();
                vehicle.setStatus(VehicleStatus.AVAILABLE);
                vehicleRepository.save(vehicle);
            }
        }

        return buildManifestResponse(route);
    }

    @Transactional
    public DeliveryResponse confirmDeliveryReturnToHub(Long deliveryId, String currentUsername) {
        Delivery delivery = deliveryRepository.findByIdWithLock(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        if (delivery.getRoute() != null) {
            confirmRouteReturnToHub(delivery.getRoute().getId(), currentUsername);
            return mapToDeliveryResponse(delivery);
        }

        if (delivery.getReturnConfirmedAt() != null) {
            return mapToDeliveryResponse(delivery);
        }

        delivery.setReturnConfirmedAt(OffsetDateTime.now());
        if (delivery.getDriver() != null && delivery.getDriver().getStatus() == DriverStatus.ON_DELIVERY) {
            Driver driver = delivery.getDriver();
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
        }
        if (delivery.getVehicle() != null && delivery.getVehicle().getStatus() == VehicleStatus.IN_USE) {
            Vehicle vehicle = delivery.getVehicle();
            vehicle.setStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
        }

        Delivery saved = deliveryRepository.save(delivery);
        return mapToDeliveryResponse(saved);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse getDeliveryById(Long id, String currentUsername) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() == Role.DRIVER) {
            if (delivery.getDriver() == null || delivery.getDriver().getUser() == null ||
                    !delivery.getDriver().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not assigned to this delivery");
            }
        } else if (currentUser.getRole() == Role.USER) {
            if (delivery.getPackage() == null || delivery.getPackage().getCustomer() == null ||
                    !delivery.getPackage().getCustomer().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not the owner of this delivery");
            }
        }

        return mapToDeliveryResponse(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getAllDeliveries(String currentUsername) {
        User currentUser = getCurrentUser(currentUsername);
        List<Delivery> deliveries;

        if (currentUser.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + currentUsername));
            deliveries = deliveryRepository.findByDriverIdAndActiveTrue(driver.getId());
        } else if (currentUser.getRole() == Role.USER) {
            deliveries = deliveryRepository.findByPkgCustomerIdAndActiveTrue(currentUser.getId());
        } else {
            deliveries = deliveryRepository.findByActiveTrue();
        }

        return deliveries.stream().map(this::mapToDeliveryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryStatusHistoryResponse> getDeliveryHistory(Long deliveryId, String currentUsername) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() == Role.DRIVER) {
            if (delivery.getDriver() == null || delivery.getDriver().getUser() == null ||
                    !delivery.getDriver().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not assigned to this delivery");
            }
        } else if (currentUser.getRole() == Role.USER) {
            if (delivery.getPackage() == null || delivery.getPackage().getCustomer() == null ||
                    !delivery.getPackage().getCustomer().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not the owner of this delivery");
            }
        }

        List<DeliveryStatusHistory> history = deliveryStatusHistoryRepository.findByDeliveryIdOrderByCreatedAtDesc(deliveryId);
        return history.stream().map(h -> DeliveryStatusHistoryResponse.builder()
                .id(h.getId())
                .deliveryId(h.getDelivery().getId())
                .previousStatus(h.getPreviousStatus())
                .newStatus(h.getNewStatus())
                .changedByUserId(h.getChangedByUser() != null ? h.getChangedByUser().getId() : null)
                .changedByUsername(h.getChangedByUser() != null ? h.getChangedByUser().getUsername() : null)
                .reason(h.getReason())
                .createdAt(h.getCreatedAt())
                .build()).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DeliveryResponse deactivateDelivery(Long id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + id));

        if (delivery.getStatus() == DeliveryStatus.ASSIGNED || delivery.getStatus() == DeliveryStatus.IN_PROGRESS) {
            throw new ConflictException("Cannot deactivate an active delivery. Please cancel or complete it first.");
        }

        delivery.setActive(false);
        Delivery saved = deliveryRepository.save(delivery);
        return mapToDeliveryResponse(saved);
    }

    private User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Transactional
    public BatchAssignmentResponse batchAssignDeliveries(BatchAssignmentRequest request, String currentUsername) {
        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Access denied: Only administrators can dispatch and assign multi-stop manifests");
        }

        if (request.getRouteId() == null) {
            throw new BadRequestException("Route ID must not be null");
        }
        if (request.getDriverId() == null) {
            throw new BadRequestException("Driver ID must not be null");
        }
        if (request.getVehicleId() == null) {
            throw new BadRequestException("Vehicle ID must not be null");
        }
        if (request.getDeliveryIds() == null || request.getDeliveryIds().isEmpty()) {
            throw new BadRequestException("Delivery IDs list must not be empty");
        }

        // Validate uniqueness of requested delivery IDs
        Set<Long> uniqueIds = new HashSet<>(request.getDeliveryIds());
        if (uniqueIds.size() != request.getDeliveryIds().size()) {
            throw new BadRequestException("Duplicate delivery IDs provided in batch assignment request");
        }

        // Lock route
        Route route = routeRepository.findByIdWithLock(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + request.getRouteId()));
        if (!Boolean.TRUE.equals(route.getActive()) || !Boolean.TRUE.equals(route.getIsCurrent())) {
            throw new BadRequestException("Route is not active or is no longer current");
        }

        // Lock driver
        Driver driver = driverRepository.findByIdWithLock(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));
        if (!Boolean.TRUE.equals(driver.getActive())) {
            throw new BadRequestException("Driver is inactive: " + driver.getName());
        }
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new ConflictException("Driver is not available; current status: " + driver.getStatus());
        }

        // Lock vehicle
        Vehicle vehicle = vehicleRepository.findByIdWithLock(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new BadRequestException("Vehicle is inactive: " + vehicle.getRegistrationNumber());
        }
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not available; current status: " + vehicle.getStatus());
        }

        // Lock and validate each delivery
        List<Delivery> deliveries = new ArrayList<>();
        double totalWeightKg = 0.0;
        for (Long dId : request.getDeliveryIds()) {
            Delivery delivery = deliveryRepository.findByIdWithLock(dId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + dId));

            if (!Boolean.TRUE.equals(delivery.getActive())) {
                throw new BadRequestException("Delivery is inactive: " + dId);
            }
            if (delivery.getPackage() == null || !Boolean.TRUE.equals(delivery.getPackage().getActive())) {
                throw new BadRequestException("Delivery has no valid active package: " + dId);
            }
            if (delivery.getRoute() == null || !delivery.getRoute().getId().equals(route.getId())) {
                throw new BadRequestException("Delivery " + dId + " is not associated with route " + route.getId());
            }
            if (delivery.getSourceLocation() != null && route.getSourceLocation() != null &&
                    !delivery.getSourceLocation().getId().equals(route.getSourceLocation().getId())) {
                delivery.setSourceLocation(route.getSourceLocation());
                delivery.setHub(route.getSourceLocation());
            }
            // Allow assignment only for CREATED or ASSIGNED (reassignment of same manifest)
            if (delivery.getStatus() != DeliveryStatus.CREATED && delivery.getStatus() != DeliveryStatus.ASSIGNED) {
                throw new ConflictException("Cannot assign delivery in status: " + delivery.getStatus());
            }

            totalWeightKg += (delivery.getPackage().getWeight() != null ? delivery.getPackage().getWeight() : 0.0);
            deliveries.add(delivery);
        }

        // Validate total payload against vehicle capacity
        if (vehicle.getCapacity() != null && totalWeightKg > vehicle.getCapacity()) {
            throw new BadRequestException(String.format(
                    "Total manifest package payload (%.2f kg) exceeds vehicle payload capacity (%.2f kg)",
                    totalWeightKg, vehicle.getCapacity()));
        }

        // Update driver and vehicle states atomically
        driver.setStatus(DriverStatus.ON_DELIVERY);
        driverRepository.save(driver);

        vehicle.setStatus(VehicleStatus.IN_USE);
        vehicleRepository.save(vehicle);

        // Update deliveries atomically
        for (Delivery d : deliveries) {
            DeliveryStatus prevStatus = d.getStatus();
            d.setDriver(driver);
            d.setVehicle(vehicle);
            d.setStatus(DeliveryStatus.ASSIGNED);

            Package pkg = d.getPackage();
            pkg.setStatus(PackageStatus.IN_DELIVERY);
            packageRepository.save(pkg);

            if (request.getNotes() != null) {
                d.setNotes(request.getNotes());
            }
            deliveryRepository.save(d);

            DeliveryStatusHistory history = DeliveryStatusHistory.builder()
                    .delivery(d)
                    .previousStatus(prevStatus)
                    .newStatus(DeliveryStatus.ASSIGNED)
                    .changedByUser(currentUser)
                    .reason(request.getNotes() != null ? request.getNotes() : "Batch assigned to manifest on route " + route.getId())
                    .build();
            deliveryStatusHistoryRepository.save(history);
        }

        return BatchAssignmentResponse.builder()
                .routeId(route.getId())
                .driverId(driver.getId())
                .driverName(driver.getName())
                .driverStatus(driver.getStatus())
                .vehicleId(vehicle.getId())
                .vehicleRegistrationNumber(vehicle.getRegistrationNumber())
                .vehicleCapacityKg(vehicle.getCapacity())
                .vehicleStatus(vehicle.getStatus())
                .totalWeightKg(totalWeightKg)
                .assignedDeliveryIds(deliveries.stream().map(Delivery::getId).toList())
                .status("ASSIGNED")
                .assignedAt(OffsetDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getUnassignedDeliveriesByHub(Long hubId) {
        List<Delivery> allCreated = deliveryRepository.findByStatusAndActiveTrue(DeliveryStatus.CREATED);

        // Filter unassigned candidate deliveries:
        // Must have no driver, no vehicle, active package, active destination location with valid coordinates
        List<Delivery> unassignedCandidates = allCreated.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActive()))
                .filter(d -> d.getDriver() == null && d.getVehicle() == null)
                .filter(d -> d.getPackage() != null && Boolean.TRUE.equals(d.getPackage().getActive()))
                .filter(d -> d.getDestinationLocation() != null && Boolean.TRUE.equals(d.getDestinationLocation().getActive()))
                .filter(d -> d.getDestinationLocation().getLatitude() != null && d.getDestinationLocation().getLongitude() != null)
                .toList();

        List<Delivery> eligibleList;
        if (hubId != null) {
            Location hub = locationRepository.findById(hubId)
                    .orElseThrow(() -> new ResourceNotFoundException("Central Hub not found with id: " + hubId));
            Double hubLat = hub.getLatitude();
            Double hubLon = hub.getLongitude();

            if (hubLat == null || hubLon == null) {
                throw new BadRequestException("Selected hub " + hub.getName() + " has invalid coordinates");
            }

            // Radius eligibility: destination within configured 10.0 km radius from selected hub
            final double maxRadiusKm = 10.0;
            eligibleList = unassignedCandidates.stream()
                    .filter(d -> {
                        double distKm = RoutingService.calculateHaversineKm(
                                hubLat, hubLon,
                                d.getDestinationLocation().getLatitude(), d.getDestinationLocation().getLongitude()
                        );
                        return distKm <= maxRadiusKm;
                    })
                    .sorted(java.util.Comparator.comparing(Delivery::getId))
                    .toList();
        } else {
            eligibleList = unassignedCandidates.stream()
                    .sorted(java.util.Comparator.comparing(Delivery::getId))
                    .toList();
        }

        return eligibleList.stream().map(this::mapToDeliveryResponse).toList();
    }

    @Transactional(readOnly = true)
    public MultiStopManifestResponse getRouteManifest(Long routeId, String currentUsername) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + routeId));

        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Access denied: No driver profile found"));
            boolean assigned = deliveryRepository.findByRouteIdAndActiveTrue(routeId).stream()
                    .anyMatch(d -> d.getDriver() != null && d.getDriver().getId().equals(driver.getId()));
            if (!assigned) {
                throw new AccessDeniedException("Access denied: You are not assigned to this route manifest");
            }
        }

        return buildManifestResponse(route);
    }

    @Transactional(readOnly = true)
    public MultiStopManifestResponse getDriverActiveManifest(String driverUsername) {
        User currentUser = getCurrentUser(driverUsername);
        Driver driver = driverRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No driver profile found for user: " + driverUsername));

        List<Delivery> activeDeliveries = deliveryRepository.findActiveDeliveriesByDriverId(
                driver.getId(),
                List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS)
        );

        if (activeDeliveries.isEmpty()) {
            throw new ResourceNotFoundException("No active assigned manifest found for driver: " + driver.getName());
        }

        Route route = activeDeliveries.get(0).getRoute();
        if (route == null) {
            throw new ResourceNotFoundException("Active delivery has no multi-stop route manifest");
        }

        return buildManifestResponse(route);
    }

    private MultiStopManifestResponse buildManifestResponse(Route route) {
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId());
        List<RouteEdge> edges = routeEdgeRepository.findByRouteIdOrderByEdgeOrderAsc(route.getId());
        List<Delivery> routeDeliveries = deliveryRepository.findByRouteIdAndActiveTrue(route.getId());

        Driver driver = routeDeliveries.stream()
                .map(Delivery::getDriver)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Vehicle vehicle = routeDeliveries.stream()
                .map(Delivery::getVehicle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        double totalWeightKg = routeDeliveries.stream()
                .map(Delivery::getPackage)
                .filter(Objects::nonNull)
                .mapToDouble(p -> p.getWeight() != null ? p.getWeight() : 0.0)
                .sum();

        boolean allTerminal = !routeDeliveries.isEmpty() && routeDeliveries.stream().allMatch(d ->
                d.getStatus() == DeliveryStatus.COMPLETED ||
                d.getStatus() == DeliveryStatus.FAILED ||
                d.getStatus() == DeliveryStatus.CANCELLED
        );

        boolean returnToHub = stops.stream().anyMatch(s -> s.getStopType() == StopType.HUB_RETURN);

        List<ManifestStopResponse> stopResponses = stops.stream().map(s -> {
            Delivery del = s.getDelivery();
            Package pkg = del != null ? del.getPackage() : null;

            // Leg distance/duration from incoming edge if available
            Double legDist = null;
            Double legDur = null;
            if (s.getStopOrder() > 1 && s.getStopOrder() - 2 < edges.size()) {
                RouteEdge incomingEdge = edges.get(s.getStopOrder() - 2);
                legDist = incomingEdge.getDistance();
                legDur = incomingEdge.getEstimatedTime();
            }

            return ManifestStopResponse.builder()
                    .stopId(s.getId())
                    .stopOrder(s.getStopOrder())
                    .stopType(s.getStopType())
                    .customerStopNumber(s.getCustomerStopNumber())
                    .locationId(s.getLocation() != null ? s.getLocation().getId() : null)
                    .locationName(s.getLocation() != null ? s.getLocation().getName() : null)
                    .address(s.getLocation() != null ? s.getLocation().getAddress() : null)
                    .latitude(s.getLocation() != null ? s.getLocation().getLatitude() : null)
                    .longitude(s.getLocation() != null ? s.getLocation().getLongitude() : null)
                    .deliveryId(del != null ? del.getId() : null)
                    .trackingNumber(pkg != null ? pkg.getTrackingNumber() : null)
                    .receiverName(pkg != null ? pkg.getReceiverName() : null)
                    .packageWeightKg(pkg != null ? pkg.getWeight() : null)
                    .deliveryStatus(del != null ? del.getStatus() : null)
                    .estimatedArrivalTime(s.getEstimatedArrivalTime())
                    .legDistance(legDist)
                    .legDuration(legDur)
                    .build();
        }).toList();

        // Stitch road polyline coordinates from edge GeoJSON
        List<List<Double>> geometryCoordinates = new ArrayList<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (RouteEdge edge : edges) {
            if (edge.getGeometryGeojson() != null && !edge.getGeometryGeojson().isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(edge.getGeometryGeojson());
                    com.fasterxml.jackson.databind.JsonNode coords = root.get("coordinates");
                    if (coords != null && coords.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode pt : coords) {
                            if (pt.isArray() && pt.size() >= 2) {
                                double lon = pt.get(0).asDouble();
                                double lat = pt.get(1).asDouble();
                                geometryCoordinates.add(List.of(lat, lon));
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (geometryCoordinates.isEmpty()) {
            for (RouteStop s : stops) {
                if (s.getLocation() != null && s.getLocation().getLatitude() != null && s.getLocation().getLongitude() != null) {
                    geometryCoordinates.add(List.of(s.getLocation().getLatitude(), s.getLocation().getLongitude()));
                }
            }
        }

        Location hub = route.getSourceLocation();

        String routeStatus = route.getStatus() != null ? route.getStatus() : "ACTIVE";
        boolean canConfirmReturn = false;
        if (allTerminal) {
            if ("COMPLETED".equalsIgnoreCase(routeStatus)) {
                canConfirmReturn = false;
            } else {
                routeStatus = "RETURNING_TO_HUB";
                canConfirmReturn = true;
            }
        }

        Double returnLegDist = route.getReturnDistance();
        Double returnLegDur = route.getReturnEstimatedTime();
        if (returnLegDist == null) {
            RouteStop returnStop = stops.stream().filter(s -> s.getStopType() == StopType.HUB_RETURN).findFirst().orElse(null);
            if (returnStop != null && returnStop.getStopOrder() > 1 && returnStop.getStopOrder() - 2 < edges.size()) {
                RouteEdge incomingEdge = edges.get(returnStop.getStopOrder() - 2);
                returnLegDist = incomingEdge.getDistance();
                returnLegDur = incomingEdge.getEstimatedTime();
            }
        }

        return MultiStopManifestResponse.builder()
                .routeId(route.getId())
                .hubId(hub != null ? hub.getId() : null)
                .hubName(hub != null ? hub.getName() : null)
                .hubAddress(hub != null ? hub.getAddress() : null)
                .hubLatitude(hub != null ? hub.getLatitude() : null)
                .hubLongitude(hub != null ? hub.getLongitude() : null)
                .returnToHub(returnToHub)
                .routeStatus(routeStatus)
                .canConfirmReturn(canConfirmReturn)
                .returnedToHubAt(route.getReturnedToHubAt())
                .returnConfirmedBy(route.getReturnConfirmedBy())
                .returnLegDistance(returnLegDist)
                .returnLegDuration(returnLegDur)
                .totalDistance(route.getTotalDistance())
                .totalEstimatedTime(route.getEstimatedTime())
                .algorithmUsed(route.getAlgorithmUsed())
                .driverId(driver != null ? driver.getId() : null)
                .driverName(driver != null ? driver.getName() : null)
                .driverPhone(driver != null ? driver.getPhone() : null)
                .vehicleId(vehicle != null ? vehicle.getId() : null)
                .vehicleRegistration(vehicle != null ? vehicle.getRegistrationNumber() : null)
                .vehicleCapacityKg(vehicle != null ? vehicle.getCapacity() : null)
                .totalWeightKg(totalWeightKg)
                .isCompleted(allTerminal)
                .stops(stopResponses)
                .geometryCoordinates(geometryCoordinates)
                .build();
    }

    public DeliveryResponse mapToDeliveryResponse(Delivery d) {
        String rStatus = "ACTIVE";
        boolean canConfirm = false;
        if (d.getRoute() != null && d.getRoute().getStatus() != null) {
            rStatus = d.getRoute().getStatus();
            canConfirm = "RETURNING_TO_HUB".equalsIgnoreCase(rStatus);
        } else if (d.getStatus() == DeliveryStatus.COMPLETED) {
            if (d.getReturnConfirmedAt() != null) {
                rStatus = "COMPLETED";
                canConfirm = false;
            } else {
                rStatus = "RETURNING_TO_HUB";
                canConfirm = true;
            }
        }

        return DeliveryResponse.builder()
                .id(d.getId())
                .packageId(d.getPackage() != null ? d.getPackage().getId() : null)
                .trackingNumber(d.getPackage() != null ? d.getPackage().getTrackingNumber() : null)
                .sourceLocationId(d.getSourceLocation() != null ? d.getSourceLocation().getId() : null)
                .sourceLocationName(d.getSourceLocation() != null ? d.getSourceLocation().getName() : null)
                .destinationLocationId(d.getDestinationLocation() != null ? d.getDestinationLocation().getId() : null)
                .destinationLocationName(d.getDestinationLocation() != null ? d.getDestinationLocation().getName() : null)
                .driverId(d.getDriver() != null ? d.getDriver().getId() : null)
                .driverName(d.getDriver() != null ? d.getDriver().getName() : null)
                .vehicleId(d.getVehicle() != null ? d.getVehicle().getId() : null)
                .vehicleRegistrationNumber(d.getVehicle() != null ? d.getVehicle().getRegistrationNumber() : null)
                .status(d.getStatus())
                .routeStatus(rStatus)
                .canConfirmReturn(canConfirm)
                .returnConfirmedAt(d.getReturnConfirmedAt())
                .estimatedDistance(d.getEstimatedDistance())
                .estimatedTime(d.getEstimatedTime())
                .notes(d.getNotes())
                .completedAt(d.getCompletedAt())
                .active(d.getActive())
                .version(d.getVersion())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .hubId(d.getHub() != null ? d.getHub().getId() : null)
                .hubName(d.getHub() != null ? d.getHub().getName() : null)
                .hubAddress(d.getHub() != null ? d.getHub().getAddress() : null)
                .hubLatitude(d.getHub() != null ? d.getHub().getLatitude() : null)
                .hubLongitude(d.getHub() != null ? d.getHub().getLongitude() : null)
                .hubSelectionBasis(d.getHubSelectionBasis())
                .currentStage(d.getCurrentStage())
                .packageWeight(d.getPackage() != null ? d.getPackage().getWeight() : null)
                .weight(d.getPackage() != null ? d.getPackage().getWeight() : null)
                .build();
    }
}
