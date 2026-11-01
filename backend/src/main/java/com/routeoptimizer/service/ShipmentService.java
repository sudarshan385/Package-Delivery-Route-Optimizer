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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLegRepository deliveryLegRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final PackageRepository packageRepository;
    private final LocationRepository locationRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final RoutingService routingService;
    private final LocationEdgeRepository locationEdgeRepository;
    private final PackageTrackingNumberGenerator trackingNumberGenerator;

    private User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + username));
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Phase 2 & 4: Customer Shipment Request with Automatic Nearest-Hub Selection
     */
    @Transactional
    public DeliveryResponse createCustomerShipment(CustomerShipmentRequest request, String currentUsername) {
        User customer = getCurrentUser(currentUsername);

        // Validation of Bengaluru geographical boundary
        if (request.getPickupLatitude() < 12.5 || request.getPickupLatitude() > 13.5 ||
            request.getPickupLongitude() < 77.2 || request.getPickupLongitude() > 78.0) {
            throw new BadRequestException("Pickup coordinates must be within the Bengaluru metropolitan service area (lat: 12.5-13.5, lon: 77.2-78.0).");
        }
        if (request.getDeliveryLatitude() < 12.5 || request.getDeliveryLatitude() > 13.5 ||
            request.getDeliveryLongitude() < 77.2 || request.getDeliveryLongitude() > 78.0) {
            throw new BadRequestException("Delivery destination coordinates must be within the Bengaluru metropolitan service area.");
        }

        // Verify distinct locations
        double distBetweenEnds = calculateHaversineKm(
                request.getPickupLatitude(), request.getPickupLongitude(),
                request.getDeliveryLatitude(), request.getDeliveryLongitude()
        );
        if (distBetweenEnds < 0.05) { // less than 50 meters
            throw new BadRequestException("Pickup location and destination location must be distinct.");
        }

        // 1. Automatic Nearest-Hub Selection
        List<Location> activeHubs = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getActive()) &&
                        ("HUB".equalsIgnoreCase(l.getLocationType()) || "DEPOT".equalsIgnoreCase(l.getLocationType())))
                .sorted(Comparator.comparingDouble(h -> calculateHaversineKm(
                        request.getPickupLatitude(), request.getPickupLongitude(),
                        h.getLatitude(), h.getLongitude())))
                .toList();

        if (activeHubs.isEmpty()) {
            throw new BadRequestException("No active service hubs available in the network. Please contact dispatch administration.");
        }

        Location selectedHub = null;
        String hubSelectionBasis = null;
        Double leg1DistanceKm = null;
        Double leg1DurationMin = null;

        // Efficient bounded evaluation: evaluate top 3 nearest candidates by road routing
        int evalLimit = Math.min(3, activeHubs.size());
        for (int i = 0; i < evalLimit; i++) {
            Location candidate = activeHubs.get(i);
            try {
                RoutingResponse route = routingService.calculateRoute(
                        request.getPickupLatitude(), request.getPickupLongitude(),
                        candidate.getLatitude(), candidate.getLongitude()
                );
                if (route != null && route.getDistanceKm() != null && route.getDistanceKm() > 0) {
                    selectedHub = candidate;
                    leg1DistanceKm = route.getDistanceKm();
                    leg1DurationMin = route.getDurationMin();
                    hubSelectionBasis = String.format("Nearest reachable service hub by road: %.1f km (%.1f min)",
                            route.getDistanceKm(), route.getDurationMin());
                    break;
                }
            } catch (Exception ex) {
                log.warn("Road routing check failed for candidate hub {}: {}", candidate.getName(), ex.getMessage());
            }
        }

        // Fallback to closest hub by geographic proximity if road routing failed for all evaluated candidates
        if (selectedHub == null) {
            selectedHub = activeHubs.get(0);
            double approxKm = calculateHaversineKm(
                    request.getPickupLatitude(), request.getPickupLongitude(),
                    selectedHub.getLatitude(), selectedHub.getLongitude()
            );
            leg1DistanceKm = approxKm;
            leg1DurationMin = approxKm * 2.5; // ~25 km/h urban estimate
            hubSelectionBasis = String.format("Nearest candidate hub by geographic proximity: %.1f km", approxKm);
        }

        // 2. Compute Leg 2 road metrics (Hub -> Destination)
        Double leg2DistanceKm = null;
        Double leg2DurationMin = null;
        try {
            RoutingResponse route2 = routingService.calculateRoute(
                    selectedHub.getLatitude(), selectedHub.getLongitude(),
                    request.getDeliveryLatitude(), request.getDeliveryLongitude()
            );
            if (route2 != null) {
                leg2DistanceKm = route2.getDistanceKm();
                leg2DurationMin = route2.getDurationMin();
            }
        } catch (Exception ex) {
            log.warn("Leg 2 road routing failed: {}", ex.getMessage());
            double approxKm = calculateHaversineKm(
                    selectedHub.getLatitude(), selectedHub.getLongitude(),
                    request.getDeliveryLatitude(), request.getDeliveryLongitude()
            );
            leg2DistanceKm = approxKm;
            leg2DurationMin = approxKm * 2.5;
        }

        String trackingNumber = trackingNumberGenerator.nextTrackingNumber();

        // 3. Persist Location vertices for Pickup and Destination
        Location pickupLoc = Location.builder()
                .name(trackingNumber + "-PICKUP")
                .address(request.getPickupAddress().trim())
                .city(StringUtils.hasText(request.getPickupCity()) ? request.getPickupCity().trim() : "Bengaluru")
                .latitude(request.getPickupLatitude())
                .longitude(request.getPickupLongitude())
                .locationType("CUSTOMER")
                .build();
        pickupLoc.setActive(true);
        pickupLoc.setVersion(0L);
        pickupLoc = locationRepository.save(pickupLoc);

        Location destLoc = Location.builder()
                .name(trackingNumber + "-DEST")
                .address(request.getDeliveryAddress().trim())
                .city(StringUtils.hasText(request.getDeliveryCity()) ? request.getDeliveryCity().trim() : "Bengaluru")
                .latitude(request.getDeliveryLatitude())
                .longitude(request.getDeliveryLongitude())
                .locationType("CUSTOMER")
                .build();
        destLoc.setActive(true);
        destLoc.setVersion(0L);
        destLoc = locationRepository.save(destLoc);

        // 4. Persist Package Entity
        Package pkg = Package.builder()
                .trackingNumber(trackingNumber)
                .customer(customer)
                .senderName(request.getSenderName().trim())
                .senderPhone(request.getSenderPhone().trim())
                .senderEmail(request.getSenderEmail() != null ? request.getSenderEmail().trim() : customer.getEmail())
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone().trim())
                .receiverEmail(request.getReceiverEmail() != null ? request.getReceiverEmail().trim() : null)
                .city(pickupLoc.getCity())
                .pickupLocation(request.getPickupAddress().trim())
                .deliveryLocation(request.getDeliveryAddress().trim())
                .sourceLocation(pickupLoc)
                .destinationLocation(destLoc)
                .weight(request.getWeight())
                .priority(Priority.valueOf(request.getPriority() != null ? request.getPriority().toUpperCase() : "MEDIUM"))
                .status(PackageStatus.PENDING)
                .description(request.getDescription())
                .itemName(request.getItemName().trim())
                .itemCategory(request.getItemCategory() != null ? request.getItemCategory().trim() : "General Parcel")
                .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                .fragile(Boolean.TRUE.equals(request.getFragile()))
                .declaredValue(request.getDeclaredValue())
                .lengthCm(request.getLengthCm())
                .widthCm(request.getWidthCm())
                .heightCm(request.getHeightCm())
                .pickupInstructions(request.getPickupInstructions())
                .deliveryInstructions(request.getDeliveryInstructions())
                .build();
        pkg = packageRepository.save(pkg);

        // 5. Persist Delivery Entity
        double totalEstDist = (leg1DistanceKm != null ? leg1DistanceKm : 0.0) + (leg2DistanceKm != null ? leg2DistanceKm : 0.0);
        double totalEstTime = (leg1DurationMin != null ? leg1DurationMin : 0.0) + (leg2DurationMin != null ? leg2DurationMin : 0.0);

        Delivery delivery = Delivery.builder()
                .pkg(pkg)
                .sourceLocation(pickupLoc)
                .destinationLocation(destLoc)
                .hub(selectedHub)
                .hubSelectionBasis(hubSelectionBasis)
                .currentStage("REQUEST_CREATED")
                .status(DeliveryStatus.CREATED)
                .estimatedDistance(totalEstDist)
                .estimatedTime(totalEstTime)
                .notes(request.getDescription())
                .build();
        delivery = deliveryRepository.save(delivery);

        // 6. Persist Delivery Legs (Leg 1: FIRST_MILE, Leg 2: FINAL_MILE)
        DeliveryLeg leg1 = DeliveryLeg.builder()
                .delivery(delivery)
                .legType(LegType.FIRST_MILE)
                .legOrder(1)
                .sourceLocation(pickupLoc)
                .destinationLocation(selectedHub)
                .status(LegStatus.PENDING)
                .distance(leg1DistanceKm)
                .estimatedTime(leg1DurationMin)
                .notes("First-Mile pickup from sender address to hub.")
                .build();
        leg1 = deliveryLegRepository.save(leg1);

        DeliveryLeg leg2 = DeliveryLeg.builder()
                .delivery(delivery)
                .legType(LegType.FINAL_MILE)
                .legOrder(2)
                .sourceLocation(selectedHub)
                .destinationLocation(destLoc)
                .status(LegStatus.PENDING)
                .distance(leg2DistanceKm)
                .estimatedTime(leg2DurationMin)
                .notes("Final-Mile delivery from hub to customer destination.")
                .build();
        leg2 = deliveryLegRepository.save(leg2);

        // Ensure Pickup and Destination locations are connected to the road network graph
        connectBidirectionalEdge(pickupLoc, selectedHub);
        connectBidirectionalEdge(selectedHub, destLoc);

        // 7. Persist Initial Milestone 1 Event
        ShipmentEvent event1 = ShipmentEvent.builder()
                .delivery(delivery)
                .pkg(pkg)
                .stage("REQUEST_CREATED")
                .title("Shipment Request Created")
                .description("Shipment validated and logged. Assigned service hub: " + selectedHub.getName())
                .locationName(request.getPickupAddress().trim())
                .hub(selectedHub)
                .actorUsername(currentUsername)
                .eventTimestamp(OffsetDateTime.now())
                .build();
        shipmentEventRepository.save(event1);

        return mapDeliveryToResponse(delivery);
    }

    /**
     * Phase 6: Admin Independent Driver and Vehicle Assignment per Leg
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryResponse assignDeliveryLeg(Long deliveryId, Long legId, AssignLegRequest request, String currentUsername) {
        DeliveryLeg leg = deliveryLegRepository.findByIdWithLock(legId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery leg not found with id: " + legId));

        Long actualDeliveryId = deliveryId != null ? deliveryId : leg.getDelivery().getId();
        Delivery delivery = deliveryRepository.findByIdWithLock(actualDeliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + actualDeliveryId));

        if (!leg.getDelivery().getId().equals(delivery.getId())) {
            throw new BadRequestException("Leg #" + legId + " does not belong to Delivery #" + actualDeliveryId);
        }

        // Final Mile Guard: Leg 1 (FIRST_MILE) must be COMPLETED before Leg 2 can be assigned/dispatched!
        if (leg.getLegType() == LegType.FINAL_MILE) {
            DeliveryLeg leg1 = deliveryLegRepository.findByDeliveryIdAndLegType(actualDeliveryId, LegType.FIRST_MILE)
                    .orElseThrow(() -> new BadRequestException("First-mile leg not found for Delivery #" + actualDeliveryId));
            if (leg1.getStatus() != LegStatus.COMPLETED) {
                throw new BadRequestException("Cannot assign Final-Mile partner: Package has not yet arrived at Central Hub (Leg 1 status: " + leg1.getStatus() + ").");
            }
        }

        // Validate driver availability
        Driver driver = driverRepository.findByIdWithLock(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));
        if (!Boolean.TRUE.equals(driver.getActive())) {
            throw new BadRequestException("Driver is inactive: " + driver.getLicenseNumber());
        }
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new ConflictException("Driver is not available (current status: " + driver.getStatus() + ").");
        }

        // Validate vehicle availability and capacity
        Vehicle vehicle = vehicleRepository.findByIdWithLock(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new BadRequestException("Vehicle is inactive: " + vehicle.getRegistrationNumber());
        }
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not available (current status: " + vehicle.getStatus() + ").");
        }
        if (vehicle.getCapacity() < delivery.getPackage().getWeight()) {
            throw new BadRequestException(String.format("Vehicle capacity (%.1f kg) is insufficient for package weight (%.1f kg).",
                    vehicle.getCapacity(), delivery.getPackage().getWeight()));
        }

        // Apply pessimistic assignment state updates
        leg.setDriver(driver);
        leg.setVehicle(vehicle);
        leg.setStatus(LegStatus.ASSIGNED);
        leg.setAssignedAt(OffsetDateTime.now());
        if (StringUtils.hasText(request.getNotes())) {
            leg.setNotes(request.getNotes());
        }

        driver.setStatus(DriverStatus.ON_DELIVERY);
        vehicle.setStatus(VehicleStatus.IN_USE);
        driverRepository.save(driver);
        vehicleRepository.save(vehicle);
        deliveryLegRepository.save(leg);

        String driverName = driver.getUser() != null ? driver.getUser().getFullName() : "Partner #" + driver.getId();
        String vehicleReg = vehicle.getRegistrationNumber();

        if (leg.getLegType() == LegType.FIRST_MILE) {
            delivery.setCurrentStage("PICKUP_ASSIGNED");
            delivery.setStatus(DeliveryStatus.ASSIGNED);
            delivery.setDriver(driver);
            delivery.setVehicle(vehicle);
            delivery.getPackage().setStatus(PackageStatus.IN_DELIVERY);
            packageRepository.save(delivery.getPackage());

            // Milestone 2
            ShipmentEvent event = ShipmentEvent.builder()
                    .delivery(delivery)
                    .pkg(delivery.getPackage())
                    .stage("PICKUP_ASSIGNED")
                    .title("Pickup Partner Assigned")
                    .description(String.format("Pickup driver %s assigned with vehicle %s.", driverName, vehicleReg))
                    .locationName(leg.getSourceLocation().getAddress())
                    .hub(delivery.getHub())
                    .driver(driver)
                    .driverName(driverName)
                    .actorUsername(currentUsername)
                    .eventTimestamp(OffsetDateTime.now())
                    .build();
            shipmentEventRepository.save(event);
        } else {
            delivery.setCurrentStage("OUT_FOR_DELIVERY");
            delivery.setStatus(DeliveryStatus.IN_PROGRESS);
            delivery.setDriver(driver);
            delivery.setVehicle(vehicle);

            // Milestone 5
            ShipmentEvent event = ShipmentEvent.builder()
                    .delivery(delivery)
                    .pkg(delivery.getPackage())
                    .stage("OUT_FOR_DELIVERY")
                    .title("Final-Mile Partner Assigned / Out for Delivery")
                    .description(String.format("Final-mile driver %s dispatched with vehicle %s.", driverName, vehicleReg))
                    .locationName(delivery.getHub() != null ? delivery.getHub().getName() : "Hub")
                    .hub(delivery.getHub())
                    .driver(driver)
                    .driverName(driverName)
                    .actorUsername(currentUsername)
                    .eventTimestamp(OffsetDateTime.now())
                    .build();
            shipmentEventRepository.save(event);
        }

        Delivery saved = deliveryRepository.save(delivery);
        return mapDeliveryToResponse(saved);
    }

    /**
     * Phase 7: Driver Leg State Machine Transitions & Event Logging
     */
    @Transactional
    public DeliveryResponse updateLegStatus(Long legId, String targetStatusStr, String reason, String currentUsername) {
        User currentUser = getCurrentUser(currentUsername);
        LegStatus targetStatus = LegStatus.valueOf(targetStatusStr.trim().toUpperCase());

        DeliveryLeg leg = deliveryLegRepository.findByIdWithLock(legId)
                .orElseThrow(() -> new ResourceNotFoundException("Leg not found with id: " + legId));

        Delivery delivery = deliveryRepository.findByIdWithLock(leg.getDelivery().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + leg.getDelivery().getId()));

        // Role Isolation: Drivers can only update their own assigned tasks
        if (currentUser.getRole() == Role.DRIVER) {
            if (leg.getDriver() == null || leg.getDriver().getUser() == null ||
                    !leg.getDriver().getUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not the assigned driver for this delivery leg.");
            }
        }

        LegStatus currentStatus = leg.getStatus();
        String driverName = leg.getDriver() != null && leg.getDriver().getUser() != null ? 
                leg.getDriver().getUser().getFullName() : currentUser.getFullName();

        if (leg.getLegType() == LegType.FIRST_MILE) {
            if (currentStatus == LegStatus.ASSIGNED && targetStatus == LegStatus.IN_TRANSIT) {
                // Milestone 3: Picked Up from Sender
                leg.setStatus(LegStatus.IN_TRANSIT);
                leg.setStartedAt(OffsetDateTime.now());
                delivery.setCurrentStage("PICKED_UP");
                delivery.setStatus(DeliveryStatus.IN_PROGRESS);

                ShipmentEvent event = ShipmentEvent.builder()
                        .delivery(delivery)
                        .pkg(delivery.getPackage())
                        .stage("PICKED_UP")
                        .title("Picked Up from Sender")
                        .description("Package securely collected from sender by " + driverName + ". In transit to central hub.")
                        .locationName(leg.getSourceLocation().getAddress())
                        .hub(delivery.getHub())
                        .driver(leg.getDriver())
                        .driverName(driverName)
                        .actorUsername(currentUsername)
                        .eventTimestamp(OffsetDateTime.now())
                        .build();
                shipmentEventRepository.save(event);

            } else if (currentStatus == LegStatus.IN_TRANSIT && targetStatus == LegStatus.COMPLETED) {
                // Milestone 4: Received and Processed at Central Hub
                leg.setStatus(LegStatus.COMPLETED);
                leg.setCompletedAt(OffsetDateTime.now());
                delivery.setCurrentStage("HUB_RECEIVED");

                // Release First-Mile Driver & Vehicle only if no other active legs in this batch/trip
                if (leg.getDriver() != null) {
                    boolean hasOtherDriverLegs = deliveryLegRepository.existsByDriverIdAndStatusInAndIdNotAndActiveTrue(
                            leg.getDriver().getId(), List.of(LegStatus.ASSIGNED, LegStatus.IN_TRANSIT), leg.getId());
                    if (!hasOtherDriverLegs) {
                        leg.getDriver().setStatus(DriverStatus.AVAILABLE);
                        driverRepository.save(leg.getDriver());
                    }
                }
                if (leg.getVehicle() != null) {
                    boolean hasOtherVehicleLegs = deliveryLegRepository.existsByVehicleIdAndStatusInAndIdNotAndActiveTrue(
                            leg.getVehicle().getId(), List.of(LegStatus.ASSIGNED, LegStatus.IN_TRANSIT), leg.getId());
                    if (!hasOtherVehicleLegs) {
                        leg.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                        vehicleRepository.save(leg.getVehicle());
                    }
                }

                String hubName = delivery.getHub() != null ? delivery.getHub().getName() : "Central Hub";
                ShipmentEvent event = ShipmentEvent.builder()
                        .delivery(delivery)
                        .pkg(delivery.getPackage())
                        .stage("HUB_RECEIVED")
                        .title("Received and Processed at Central Hub")
                        .description(String.format("Package checked in at %s. Staged for final-mile route dispatch.", hubName))
                        .locationName(hubName)
                        .hub(delivery.getHub())
                        .driver(leg.getDriver())
                        .driverName(driverName)
                        .actorUsername(currentUsername)
                        .eventTimestamp(OffsetDateTime.now())
                        .build();
                shipmentEventRepository.save(event);
            } else {
                throw new BadRequestException("Invalid state transition for First-Mile leg from " + currentStatus + " to " + targetStatus);
            }

        } else { // FINAL_MILE
            if (currentStatus == LegStatus.ASSIGNED && targetStatus == LegStatus.IN_TRANSIT) {
                leg.setStatus(LegStatus.IN_TRANSIT);
                leg.setStartedAt(OffsetDateTime.now());
                delivery.setCurrentStage("OUT_FOR_DELIVERY");

            } else if ((currentStatus == LegStatus.ASSIGNED || currentStatus == LegStatus.IN_TRANSIT) && targetStatus == LegStatus.COMPLETED) {
                // Milestone 6: Delivered
                leg.setStatus(LegStatus.COMPLETED);
                leg.setCompletedAt(OffsetDateTime.now());

                delivery.setStatus(DeliveryStatus.COMPLETED);
                delivery.setCompletedAt(OffsetDateTime.now());
                delivery.setCurrentStage("DELIVERED");

                delivery.getPackage().setStatus(PackageStatus.DELIVERED);
                packageRepository.save(delivery.getPackage());

                // Release Final-Mile Driver & Vehicle only if no other active stops remaining in this batch
                if (leg.getDriver() != null) {
                    boolean hasOtherDriverLegs = deliveryLegRepository.existsByDriverIdAndStatusInAndIdNotAndActiveTrue(
                            leg.getDriver().getId(), List.of(LegStatus.ASSIGNED, LegStatus.IN_TRANSIT), leg.getId());
                    if (!hasOtherDriverLegs) {
                        leg.getDriver().setStatus(DriverStatus.AVAILABLE);
                        driverRepository.save(leg.getDriver());
                    }
                }
                if (leg.getVehicle() != null) {
                    boolean hasOtherVehicleLegs = deliveryLegRepository.existsByVehicleIdAndStatusInAndIdNotAndActiveTrue(
                            leg.getVehicle().getId(), List.of(LegStatus.ASSIGNED, LegStatus.IN_TRANSIT), leg.getId());
                    if (!hasOtherVehicleLegs) {
                        leg.getVehicle().setStatus(VehicleStatus.AVAILABLE);
                        vehicleRepository.save(leg.getVehicle());
                    }
                }

                ShipmentEvent event = ShipmentEvent.builder()
                        .delivery(delivery)
                        .pkg(delivery.getPackage())
                        .stage("DELIVERED")
                        .title("Delivered")
                        .description("Package successfully delivered to recipient " + delivery.getPackage().getReceiverName() + ". Enjoy!")
                        .locationName(leg.getDestinationLocation().getAddress())
                        .hub(delivery.getHub())
                        .driver(leg.getDriver())
                        .driverName(driverName)
                        .actorUsername(currentUsername)
                        .eventTimestamp(OffsetDateTime.now())
                        .build();
                shipmentEventRepository.save(event);
            } else {
                throw new BadRequestException("Invalid state transition for Final-Mile leg from " + currentStatus + " to " + targetStatus);
            }
        }

        deliveryLegRepository.save(leg);
        Delivery saved = deliveryRepository.save(delivery);
        return mapDeliveryToResponse(saved);
    }

    /**
     * Admin Override for Selected Hub
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public DeliveryResponse overrideHub(Long deliveryId, Long newHubId, String reason, String currentUsername) {
        Delivery delivery = deliveryRepository.findByIdWithLock(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        Location newHub = locationRepository.findById(newHubId)
                .orElseThrow(() -> new ResourceNotFoundException("Hub location not found with id: " + newHubId));

        if (!Boolean.TRUE.equals(newHub.getActive())) {
            throw new BadRequestException("Cannot override to inactive hub: " + newHub.getName());
        }

        DeliveryLeg leg1 = deliveryLegRepository.findByDeliveryIdAndLegType(deliveryId, LegType.FIRST_MILE)
                .orElseThrow(() -> new ResourceNotFoundException("Leg 1 not found"));
        if (leg1.getStatus() != LegStatus.PENDING) {
            throw new BadRequestException("Cannot override hub after pickup partner has been assigned or transit started.");
        }

        DeliveryLeg leg2 = deliveryLegRepository.findByDeliveryIdAndLegType(deliveryId, LegType.FINAL_MILE)
                .orElseThrow(() -> new ResourceNotFoundException("Leg 2 not found"));

        leg1.setDestinationLocation(newHub);
        leg2.setSourceLocation(newHub);
        deliveryLegRepository.save(leg1);
        deliveryLegRepository.save(leg2);

        delivery.setHub(newHub);
        delivery.setHubSelectionBasis("Admin override: " + reason);
        Delivery saved = deliveryRepository.save(delivery);

        ShipmentEvent event = ShipmentEvent.builder()
                .delivery(delivery)
                .pkg(delivery.getPackage())
                .stage(delivery.getCurrentStage())
                .title("Service Hub Reassigned")
                .description(String.format("Routing hub updated to %s. Reason: %s", newHub.getName(), reason))
                .locationName(newHub.getName())
                .hub(newHub)
                .actorUsername(currentUsername)
                .eventTimestamp(OffsetDateTime.now())
                .build();
        shipmentEventRepository.save(event);

        return mapDeliveryToResponse(saved);
    }

    /**
     * Shipment Retrieval with Strict Isolation
     */
    @Transactional(readOnly = true)
    public DeliveryResponse getShipmentDetails(Long deliveryId, String currentUsername) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery not found with id: " + deliveryId));

        User currentUser = getCurrentUser(currentUsername);
        if (currentUser.getRole() == Role.USER) {
            if (delivery.getPackage().getCustomer() == null ||
                    !delivery.getPackage().getCustomer().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You can only view your own shipments.");
            }
        }

        return mapDeliveryToResponse(delivery);
    }

    @Transactional(readOnly = true)
    public DeliveryResponse trackShipmentByTrackingNumber(String trackingNumber) {
        Package pkg = packageRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with tracking number: " + trackingNumber));

        Delivery delivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getPackage().getId().equals(pkg.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No active shipment delivery found for tracking number: " + trackingNumber));

        return mapDeliveryToResponse(delivery);
    }

    @Transactional(readOnly = true)
    public List<DeliveryLegResponse> getDriverAssignedLegs(String currentUsername) {
        User driverUser = getCurrentUser(currentUsername);
        Driver driver = driverRepository.findByUserId(driverUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver profile not found for user: " + currentUsername));

        List<DeliveryLeg> legs = deliveryLegRepository.findByDriverIdAndActiveTrue(driver.getId());
        return legs.stream()
                .filter(l -> l.getStatus() == LegStatus.ASSIGNED || l.getStatus() == LegStatus.IN_TRANSIT)
                .map(leg -> {
                    DeliveryLegResponse resp = DeliveryLegResponse.fromEntity(leg);
                    try {
                        if (leg.getSourceLocation() != null && leg.getDestinationLocation() != null) {
                            RoutingResponse r = routingService.calculateRoute(
                                    leg.getSourceLocation().getLatitude(), leg.getSourceLocation().getLongitude(),
                                    leg.getDestinationLocation().getLatitude(), leg.getDestinationLocation().getLongitude()
                            );
                            if (r != null) {
                                resp.setRouteGeometry(r.getGeometryGeoJson());
                            }
                        }
                    } catch (Exception ignored) {}
                    return resp;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> getMyShipments(String currentUsername) {
        User currentUser = getCurrentUser(currentUsername);
        List<Delivery> deliveries;
        if (currentUser.getRole() == Role.ADMIN) {
            deliveries = deliveryRepository.findAll();
        } else if (currentUser.getRole() == Role.DRIVER) {
            Driver driver = driverRepository.findByUserId(currentUser.getId()).orElse(null);
            if (driver == null) return Collections.emptyList();
            deliveries = deliveryRepository.findByDriverIdAndActiveTrue(driver.getId());
        } else {
            deliveries = deliveryRepository.findByPkgCustomerIdAndActiveTrue(currentUser.getId());
        }

        return deliveries.stream()
                .sorted(Comparator.comparing((Delivery d) -> d.getCreatedAt() != null ? d.getCreatedAt() : OffsetDateTime.MIN).reversed())
                .map(this::mapDeliveryToResponse)
                .toList();
    }

    private DeliveryResponse mapDeliveryToResponse(Delivery d) {
        List<DeliveryLeg> legs = deliveryLegRepository.findByDeliveryIdOrderByLegOrderAsc(d.getId());
        List<ShipmentEvent> events = shipmentEventRepository.findByDeliveryIdOrderByEventTimestampAsc(d.getId());

        List<DeliveryLegResponse> legResponses = legs.stream().map(leg -> {
            DeliveryLegResponse res = DeliveryLegResponse.fromEntity(leg);
            // Attach road route geometry if available for visual map rendering
            try {
                if (leg.getSourceLocation() != null && leg.getDestinationLocation() != null) {
                    RoutingResponse r = routingService.calculateRoute(
                            leg.getSourceLocation().getLatitude(), leg.getSourceLocation().getLongitude(),
                            leg.getDestinationLocation().getLatitude(), leg.getDestinationLocation().getLongitude()
                    );
                    if (r != null) {
                        res.setRouteGeometry(r.getGeometryGeoJson());
                    }
                }
            } catch (Exception ignored) {}
            return res;
        }).toList();

        List<ShipmentEventResponse> eventResponses = events.stream()
                .map(ShipmentEventResponse::fromEntity)
                .toList();

        String driverName = null;
        if (d.getDriver() != null) {
            if (d.getDriver().getName() != null) {
                driverName = d.getDriver().getName();
            } else if (d.getDriver().getUser() != null) {
                driverName = d.getDriver().getUser().getFullName();
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
                .driverName(driverName)
                .vehicleId(d.getVehicle() != null ? d.getVehicle().getId() : null)
                .vehicleRegistrationNumber(d.getVehicle() != null ? d.getVehicle().getRegistrationNumber() : null)
                .status(d.getStatus())
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
                .packageDetails(d.getPackage() != null ? PackageResponse.fromEntity(d.getPackage()) : null)
                .legs(legResponses)
                .events(eventResponses)
                .build();
    }
    private void connectBidirectionalEdge(Location a, Location b) {
        if (a == null || b == null || a.getId().equals(b.getId())) {
            return;
        }
        try {
            if (!locationEdgeRepository.existsBySourceLocationIdAndDestinationLocationId(a.getId(), b.getId())) {
                RoutingResponse rAB = routingService.calculateRoute(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
                double dist = rAB != null ? rAB.getDistanceKm() : 0.0;
                if (dist > 0.001) {
                    LocationEdge edge = LocationEdge.builder()
                            .sourceLocation(a)
                            .destinationLocation(b)
                            .distance(dist)
                            .estimatedTime(Math.max(rAB.getDurationMin(), 0.1))
                            .geometryGeojson(rAB.getGeometryGeoJson())
                            .build();
                    edge.setActive(true);
                    edge.setVersion(0L);
                    locationEdgeRepository.save(edge);
                }
            }
            if (!locationEdgeRepository.existsBySourceLocationIdAndDestinationLocationId(b.getId(), a.getId())) {
                RoutingResponse rBA = routingService.calculateRoute(b.getLatitude(), b.getLongitude(), a.getLatitude(), a.getLongitude());
                double dist = rBA != null ? rBA.getDistanceKm() : 0.0;
                if (dist > 0.001) {
                    LocationEdge edge = LocationEdge.builder()
                            .sourceLocation(b)
                            .destinationLocation(a)
                            .distance(dist)
                            .estimatedTime(Math.max(rBA.getDurationMin(), 0.1))
                            .geometryGeojson(rBA.getGeometryGeoJson())
                            .build();
                    edge.setActive(true);
                    edge.setVersion(0L);
                    locationEdgeRepository.save(edge);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not create road edge between {} and {}: {}", a.getName(), b.getName(), ex.getMessage());
        }
    }
}