package com.routeoptimizer.service;

import com.routeoptimizer.dto.PackageRequest;
import com.routeoptimizer.dto.PackageResponse;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import com.routeoptimizer.service.GeocodingService;
import com.routeoptimizer.dto.GeocodeResponse;

import com.routeoptimizer.enums.FulfillmentMode;

@Service
@RequiredArgsConstructor
public class PackageService {

    private final PackageRepository packageRepository;
    private final LocationRepository locationRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final GeocodingService geocodingService;

    private void validatePhoneNumber(String phone, String fieldName) {
        if (StringUtils.hasText(phone)) {
            String trimmed = phone.trim();
            if (!trimmed.matches("^[0-9+\\-\\s()]{7,20}$")) {
                throw new BadRequestException(fieldName + " contains invalid characters or length (allowed: 7-20 digits or +, -, (), spaces).");
            }
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Transactional
    public PackageResponse createPackage(PackageRequest request) {
        if (request.getWeight() == null || request.getWeight() <= 0) {
            throw new BadRequestException("Weight must be greater than zero.");
        }

        if (request.getStatus() != null && request.getStatus() != PackageStatus.PENDING) {
            throw new BadRequestException("New packages must start in status PENDING.");
        }

        validatePhoneNumber(request.getSenderPhone(), "Sender phone");
        validatePhoneNumber(request.getReceiverPhone(), "Receiver phone");

        FulfillmentMode mode = request.getFulfillmentMode();
        if (mode == null) {
            if (request.getSourceLocationId() != null && request.getDestinationLocationId() == null && !StringUtils.hasText(request.getPickupLocation())) {
                mode = FulfillmentMode.HUB_STAGED;
            } else {
                mode = FulfillmentMode.CUSTOMER_PICKUP;
            }
        }

        String trackingNumber = request.getTrackingNumber();
        if (StringUtils.hasText(trackingNumber)) {
            trackingNumber = trackingNumber.trim();
            if (packageRepository.existsByTrackingNumber(trackingNumber)) {
                throw new ConflictException("Tracking number '" + trackingNumber + "' already exists.");
            }
        } else {
            trackingNumber = "PKG-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }

        Location source = null;
        Location destination = null;
        String trimmedCity = null;
        String trimmedPickup = null;
        String trimmedDelivery = null;

        if (mode == FulfillmentMode.HUB_STAGED) {
            if (request.getSourceLocationId() == null) {
                throw new BadRequestException("Central hub source location is required for HUB_STAGED fulfillment.");
            }
            source = locationRepository.findById(request.getSourceLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source hub location not found with id: " + request.getSourceLocationId()));
            if (!Boolean.TRUE.equals(source.getActive())) {
                throw new BadRequestException("Cannot create package: source hub location is inactive: " + source.getName());
            }
            if (source.getLocationType() != null &&
                    !source.getLocationType().equalsIgnoreCase("HUB") &&
                    !source.getLocationType().equalsIgnoreCase("DEPOT") &&
                    !source.getLocationType().equalsIgnoreCase("WAREHOUSE")) {
                throw new BadRequestException("Selected source location is not a valid central hub or depot: " + source.getName());
            }

            trimmedPickup = StringUtils.hasText(request.getPickupLocation()) ? request.getPickupLocation().trim() : source.getName();

            if (request.getDestinationLocationId() != null) {
                if (source.getId().equals(request.getDestinationLocationId())) {
                    throw new BadRequestException("Source hub location and destination location must be distinct.");
                }
                destination = locationRepository.findById(request.getDestinationLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));
                if (!Boolean.TRUE.equals(destination.getActive())) {
                    throw new BadRequestException("Cannot create package: destination location is inactive: " + destination.getName());
                }
                trimmedDelivery = destination.getName();
                trimmedCity = StringUtils.hasText(request.getCity()) ? request.getCity().trim() :
                        (source.getCity() != null ? source.getCity().trim() :
                        (destination.getCity() != null ? destination.getCity().trim() : null));
            } else {
                trimmedCity = StringUtils.hasText(request.getCity()) ? request.getCity().trim() : source.getCity();
                if (!StringUtils.hasText(trimmedCity)) {
                    throw new BadRequestException("City is required.");
                }
                trimmedDelivery = request.getDeliveryLocation() != null ? request.getDeliveryLocation().trim() : "";
                if (!StringUtils.hasText(trimmedDelivery)) {
                    throw new BadRequestException("Delivery destination location is required.");
                }
                if (trimmedDelivery.length() < 2) {
                    throw new BadRequestException("Delivery destination location must be at least 2 characters.");
                }
                if (source.getName().equalsIgnoreCase(trimmedDelivery) ||
                        (source.getAddress() != null && source.getAddress().equalsIgnoreCase(trimmedDelivery))) {
                    throw new BadRequestException("Source hub location and delivery destination location must be distinct.");
                }

                try {
                    String fullDelivery = trimmedDelivery + ", " + trimmedCity;
                    destination = locationRepository.findFirstByAddressIgnoreCaseAndCityIgnoreCase(trimmedDelivery, trimmedCity).orElse(null);
                    if (destination == null) {
                        GeocodeResponse deliveryGeo = geocodingService.geocodeAddress(fullDelivery);
                        destination = Location.builder()
                                .name(trackingNumber + "-DST")
                                .city(trimmedCity)
                                .address(trimmedDelivery)
                                .latitude(deliveryGeo.getLatitude())
                                .longitude(deliveryGeo.getLongitude())
                                .locationType("CUSTOMER")
                                .build();
                        destination.setActive(false);
                        destination.setVersion(0L);
                        destination = locationRepository.save(destination);
                    }
                } catch (com.routeoptimizer.exception.RateLimitExceededException |
                         com.routeoptimizer.exception.GeocodingUpstreamException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BadRequestException("Failed to geocode delivery location: " + e.getMessage());
                }
            }
        } else {
            // CUSTOMER_PICKUP mode (preserving legacy behavior)
            boolean hasLocationIds = request.getSourceLocationId() != null && request.getDestinationLocationId() != null;
            boolean hasCityOrCustomLocations = StringUtils.hasText(request.getCity()) ||
                    StringUtils.hasText(request.getPickupLocation()) ||
                    StringUtils.hasText(request.getDeliveryLocation());

            if (!hasLocationIds && !hasCityOrCustomLocations) {
                throw new BadRequestException("Either location IDs (sourceLocationId, destinationLocationId) or city location details (city, pickupLocation, deliveryLocation) must be provided.");
            }

            if (hasCityOrCustomLocations) {
                trimmedCity = request.getCity() != null ? request.getCity().trim() : "";
                trimmedPickup = request.getPickupLocation() != null ? request.getPickupLocation().trim() : "";
                trimmedDelivery = request.getDeliveryLocation() != null ? request.getDeliveryLocation().trim() : "";

                if (!StringUtils.hasText(trimmedCity)) {
                    throw new BadRequestException("City is required.");
                }
                if (!StringUtils.hasText(trimmedPickup)) {
                    throw new BadRequestException("Pickup location is required.");
                }
                if (trimmedPickup.length() < 2) {
                    throw new BadRequestException("Pickup location must be at least 2 characters.");
                }
                if (!StringUtils.hasText(trimmedDelivery)) {
                    throw new BadRequestException("Delivery location is required.");
                }
                if (trimmedDelivery.length() < 2) {
                    throw new BadRequestException("Delivery location must be at least 2 characters.");
                }
                if (trimmedPickup.equalsIgnoreCase(trimmedDelivery)) {
                    throw new BadRequestException("Pickup location and delivery location must be distinct.");
                }
            }

            if (hasLocationIds) {
                if (request.getSourceLocationId().equals(request.getDestinationLocationId())) {
                    throw new BadRequestException("Source location and destination location must be distinct.");
                }

                source = locationRepository.findById(request.getSourceLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + request.getSourceLocationId()));
                if (!Boolean.TRUE.equals(source.getActive())) {
                    throw new BadRequestException("Cannot create package: source location is inactive.");
                }

                destination = locationRepository.findById(request.getDestinationLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));
                if (!Boolean.TRUE.equals(destination.getActive())) {
                    throw new BadRequestException("Cannot create package: destination location is inactive.");
                }

                if (!StringUtils.hasText(trimmedCity)) {
                    trimmedCity = source.getCity() != null ? source.getCity().trim() : (destination.getCity() != null ? destination.getCity().trim() : null);
                }
                if (!StringUtils.hasText(trimmedPickup)) {
                    trimmedPickup = source.getName();
                }
                if (!StringUtils.hasText(trimmedDelivery)) {
                    trimmedDelivery = destination.getName();
                }
            }

            if (hasCityOrCustomLocations && !hasLocationIds) {
                try {
                    String fullPickup = trimmedPickup + ", " + trimmedCity;
                    source = locationRepository.findFirstByAddressIgnoreCaseAndCityIgnoreCase(trimmedPickup, trimmedCity).orElse(null);
                    if (source == null) {
                        GeocodeResponse pickupGeo = geocodingService.geocodeAddress(fullPickup);
                        source = Location.builder()
                                .name(trackingNumber + "-SRC")
                                .city(trimmedCity)
                                .address(trimmedPickup)
                                .latitude(pickupGeo.getLatitude())
                                .longitude(pickupGeo.getLongitude())
                                .locationType("CUSTOMER")
                                .build();
                        source.setActive(false);
                        source.setVersion(0L);
                        source = locationRepository.save(source);
                    }
                } catch (com.routeoptimizer.exception.RateLimitExceededException |
                         com.routeoptimizer.exception.GeocodingUpstreamException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BadRequestException("Failed to geocode pickup location: " + e.getMessage());
                }

                try {
                    String fullDelivery = trimmedDelivery + ", " + trimmedCity;
                    destination = locationRepository.findFirstByAddressIgnoreCaseAndCityIgnoreCase(trimmedDelivery, trimmedCity).orElse(null);
                    if (destination == null) {
                        GeocodeResponse deliveryGeo = geocodingService.geocodeAddress(fullDelivery);
                        destination = Location.builder()
                                .name(trackingNumber + "-DST")
                                .city(trimmedCity)
                                .address(trimmedDelivery)
                                .latitude(deliveryGeo.getLatitude())
                                .longitude(deliveryGeo.getLongitude())
                                .locationType("CUSTOMER")
                                .build();
                        destination.setActive(false);
                        destination.setVersion(0L);
                        destination = locationRepository.save(destination);
                    }
                } catch (com.routeoptimizer.exception.RateLimitExceededException |
                         com.routeoptimizer.exception.GeocodingUpstreamException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BadRequestException("Failed to geocode delivery location: " + e.getMessage());
                }
            }
        }

        Package pkg = Package.builder()
                .trackingNumber(trackingNumber)
                .senderName(request.getSenderName().trim())
                .senderPhone(request.getSenderPhone() != null ? request.getSenderPhone().trim() : null)
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone() != null ? request.getReceiverPhone().trim() : null)
                .city(trimmedCity)
                .pickupLocation(trimmedPickup)
                .deliveryLocation(trimmedDelivery)
                .sourceLocation(source)
                .destinationLocation(destination)
                .weight(request.getWeight())
                .priority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM)
                .status(PackageStatus.PENDING)
                .description(request.getDescription())
                .build();
        pkg.setActive(true);
        pkg.setVersion(0L);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = resolveAuthenticatedUser();
        boolean isUser = isUserRole(auth, currentUser);

        if (isUser) {
            if (request.getCustomerId() != null && currentUser != null && !request.getCustomerId().equals(currentUser.getId())) {
                throw new BadRequestException("Customers cannot create packages on behalf of other users.");
            }
            if (currentUser != null) {
                pkg.setCustomer(currentUser);
            }
        } else {
            if (request.getCustomerId() != null) {
                User targetCustomer = userRepository.findById(request.getCustomerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));
                pkg.setCustomer(targetCustomer);
            } else if (currentUser != null) {
                pkg.setCustomer(currentUser);
            }
        }

        Package saved = packageRepository.save(pkg);
        return PackageResponse.fromEntity(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    @Transactional(readOnly = true)
    public PackageResponse getPackageById(Long id) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = resolveAuthenticatedUser();
        boolean isUser = isUserRole(auth, currentUser);

        if (isUser) {
            if (currentUser == null || pkg.getCustomer() == null || !pkg.getCustomer().getId().equals(currentUser.getId())) {
                throw new ResourceNotFoundException("Package not found with id: " + id);
            }
        }

        return PackageResponse.fromEntity(pkg);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    @Transactional(readOnly = true)
    public Page<PackageResponse> getAllPackages(PackageStatus status, Priority priority, Boolean active, Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = resolveAuthenticatedUser();
        boolean isUser = isUserRole(auth, currentUser);

        Page<Package> page;
        if (isUser) {
            if (currentUser == null) {
                return Page.empty(pageable);
            }
            Long customerId = currentUser.getId();
            if (status != null && active != null) {
                page = packageRepository.findByCustomerIdAndStatusAndActive(customerId, status, active, pageable);
            } else if (status != null) {
                page = packageRepository.findByCustomerIdAndStatus(customerId, status, pageable);
            } else if (priority != null) {
                page = packageRepository.findByCustomerIdAndPriority(customerId, priority, pageable);
            } else if (active != null) {
                page = packageRepository.findByCustomerIdAndActive(customerId, active, pageable);
            } else {
                page = packageRepository.findByCustomerId(customerId, pageable);
            }
        } else {
            if (status != null && active != null) {
                page = packageRepository.findByStatusAndActive(status, active, pageable);
            } else if (status != null) {
                page = packageRepository.findByStatus(status, pageable);
            } else if (priority != null) {
                page = packageRepository.findByPriority(priority, pageable);
            } else if (active != null) {
                page = packageRepository.findByActive(active, pageable);
            } else {
                page = packageRepository.findAll(pageable);
            }
        }

        return page.map(PackageResponse::fromEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PackageResponse updatePackage(Long id, PackageRequest request) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));

        // Delivery lifecycle remains authoritative for status transitions
        if (request.getStatus() != null && request.getStatus() != pkg.getStatus()) {
            throw new BadRequestException("Package status transitions must be managed through the delivery lifecycle.");
        }

        boolean inTransitOrDelivered = pkg.getStatus() == PackageStatus.IN_DELIVERY || pkg.getStatus() == PackageStatus.DELIVERED;

        // If package is already in transit or delivered, routing and weight cannot be changed
        if (inTransitOrDelivered) {
            if (request.getSourceLocationId() != null && pkg.getSourceLocation() != null && !request.getSourceLocationId().equals(pkg.getSourceLocation().getId())) {
                throw new ConflictException("Cannot modify source location for package in status: " + pkg.getStatus());
            }
            if (request.getDestinationLocationId() != null && pkg.getDestinationLocation() != null && !request.getDestinationLocationId().equals(pkg.getDestinationLocation().getId())) {
                throw new ConflictException("Cannot modify destination location for package in status: " + pkg.getStatus());
            }
            if (request.getPickupLocation() != null && pkg.getPickupLocation() != null && !request.getPickupLocation().trim().equalsIgnoreCase(pkg.getPickupLocation())) {
                throw new ConflictException("Cannot modify pickup location for package in status: " + pkg.getStatus());
            }
            if (request.getDeliveryLocation() != null && pkg.getDeliveryLocation() != null && !request.getDeliveryLocation().trim().equalsIgnoreCase(pkg.getDeliveryLocation())) {
                throw new ConflictException("Cannot modify delivery location for package in status: " + pkg.getStatus());
            }
            if (request.getWeight() != null && !request.getWeight().equals(pkg.getWeight())) {
                throw new ConflictException("Cannot modify weight for package in status: " + pkg.getStatus());
            }
        }

        if (StringUtils.hasText(request.getTrackingNumber())) {
            String trimmedTracking = request.getTrackingNumber().trim();
            if (packageRepository.existsByTrackingNumberAndIdNot(trimmedTracking, id)) {
                throw new ConflictException("Tracking number '" + trimmedTracking + "' already exists.");
            }
            pkg.setTrackingNumber(trimmedTracking);
        }

        if (!inTransitOrDelivered) {
            if (StringUtils.hasText(request.getCity())) {
                pkg.setCity(request.getCity().trim());
            }
            if (StringUtils.hasText(request.getPickupLocation()) && StringUtils.hasText(request.getDeliveryLocation())) {
                String p = request.getPickupLocation().trim();
                String d = request.getDeliveryLocation().trim();
                if (p.equalsIgnoreCase(d)) {
                    throw new BadRequestException("Pickup location and delivery location must be distinct.");
                }
                pkg.setPickupLocation(p);
                pkg.setDeliveryLocation(d);
            }

            if (request.getSourceLocationId() != null && request.getDestinationLocationId() != null) {
                if (request.getSourceLocationId().equals(request.getDestinationLocationId())) {
                    throw new BadRequestException("Source location and destination location must be distinct.");
                }

                Location source = locationRepository.findById(request.getSourceLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Source location not found with id: " + request.getSourceLocationId()));
                if (!Boolean.TRUE.equals(source.getActive())) {
                    throw new BadRequestException("Cannot update package: source location is inactive.");
                }

                Location destination = locationRepository.findById(request.getDestinationLocationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Destination location not found with id: " + request.getDestinationLocationId()));
                if (!Boolean.TRUE.equals(destination.getActive())) {
                    throw new BadRequestException("Cannot update package: destination location is inactive.");
                }

                pkg.setSourceLocation(source);
                pkg.setDestinationLocation(destination);
            }
        }

        if (!inTransitOrDelivered && request.getWeight() != null) {
            if (request.getWeight() <= 0) {
                throw new BadRequestException("Weight must be greater than zero.");
            }
            pkg.setWeight(request.getWeight());
        }

        validatePhoneNumber(request.getSenderPhone(), "Sender phone");
        validatePhoneNumber(request.getReceiverPhone(), "Receiver phone");

        pkg.setSenderName(request.getSenderName().trim());
        pkg.setSenderPhone(request.getSenderPhone() != null ? request.getSenderPhone().trim() : null);
        pkg.setReceiverName(request.getReceiverName().trim());
        pkg.setReceiverPhone(request.getReceiverPhone() != null ? request.getReceiverPhone().trim() : null);
        if (request.getPriority() != null) {
            pkg.setPriority(request.getPriority());
        }
        pkg.setDescription(request.getDescription());

        if (request.getCustomerId() != null) {
            User targetCustomer = userRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));
            pkg.setCustomer(targetCustomer);
        }

        Package saved = packageRepository.save(pkg);
        return PackageResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PackageResponse deactivatePackage(Long id) {
        Package pkg = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package not found with id: " + id));

        boolean hasActiveDelivery = deliveryRepository.existsByPackageIdAndStatusInAndActiveTrue(
                id, List.of(DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS));
        if (hasActiveDelivery) {
            throw new ConflictException("Cannot deactivate package with active deliveries.");
        }

        pkg.setActive(false);
        Package saved = packageRepository.save(pkg);
        return PackageResponse.fromEntity(saved);
    }

    private User resolveAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !StringUtils.hasText(auth.getName()) || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        String identifier = auth.getName().trim();
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier).orElse(null);
    }

    private boolean isUserRole(Authentication auth, User currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.USER) {
            return true;
        }
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equalsIgnoreCase(a.getAuthority()));
        }
        return false;
    }
}
