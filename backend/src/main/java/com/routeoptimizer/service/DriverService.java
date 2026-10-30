package com.routeoptimizer.service;

import com.routeoptimizer.dto.DriverRequest;
import com.routeoptimizer.dto.DriverResponse;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.DriverRepository;
import com.routeoptimizer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final DeliveryRepository deliveryRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DriverResponse createDriver(DriverRequest request) {
        String trimmedLicense = request.getLicenseNumber().trim();
        if (driverRepository.existsByLicenseNumber(trimmedLicense)) {
            throw new ConflictException("Driver license number '" + trimmedLicense + "' is already in use.");
        }

        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

            if (user.getRole() != Role.DRIVER) {
                throw new BadRequestException("Associated user must have DRIVER role. User " + user.getUsername() + " has role " + user.getRole());
            }

            if (driverRepository.existsByUserId(user.getId())) {
                throw new ConflictException("User '" + user.getUsername() + "' is already associated with a driver profile.");
            }
        }

        Driver driver = Driver.builder()
                .user(user)
                .name(request.getName().trim())
                .phone(request.getPhone())
                .licenseNumber(trimmedLicense)
                .status(request.getStatus() != null ? request.getStatus() : DriverStatus.AVAILABLE)
                .build();
        driver.setActive(true);
        driver.setVersion(0L);

        Driver saved = driverRepository.save(driver);
        return DriverResponse.fromEntity(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    @Transactional(readOnly = true)
    public DriverResponse getDriverById(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DRIVER"))) {
            // A driver can only view their own profile
            if (driver.getUser() == null || !driver.getUser().getUsername().equals(auth.getName())) {
                throw new AccessDeniedException("Access denied: Drivers may only view their own profile.");
            }
        }

        return DriverResponse.fromEntity(driver);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<DriverResponse> getAllDrivers(DriverStatus status, Boolean active, Pageable pageable) {
        Page<Driver> page;
        if (status != null && active != null) {
            page = driverRepository.findByStatusAndActive(status, active, pageable);
        } else if (status != null) {
            page = driverRepository.findByStatus(status, pageable);
        } else if (active != null) {
            page = driverRepository.findByActive(active, pageable);
        } else {
            page = driverRepository.findAll(pageable);
        }

        return page.map(DriverResponse::fromEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DriverResponse updateDriver(Long id, DriverRequest request) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));

        String trimmedLicense = request.getLicenseNumber().trim();
        if (driverRepository.existsByLicenseNumberAndIdNot(trimmedLicense, id)) {
            throw new ConflictException("Driver license number '" + trimmedLicense + "' is already in use.");
        }

        if (request.getStatus() != null && request.getStatus() != driver.getStatus()) {
            boolean isOccupied = driver.getStatus() == DriverStatus.ON_DELIVERY ||
                    deliveryRepository.existsByDriverIdAndStatusInAndActiveTrue(
                            id, List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS));
            if (isOccupied && request.getStatus() != DriverStatus.ON_DELIVERY) {
                throw new ConflictException("Cannot change driver status to " + request.getStatus() + " while driver has active deliveries.");
            }
            driver.setStatus(request.getStatus());
        }

        if (request.getUserId() != null) {
            if (driver.getUser() == null || !driver.getUser().getId().equals(request.getUserId())) {
                User user = userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

                if (user.getRole() != Role.DRIVER) {
                    throw new BadRequestException("Associated user must have DRIVER role.");
                }

                if (driverRepository.existsByUserIdAndIdNot(user.getId(), id)) {
                    throw new ConflictException("User '" + user.getUsername() + "' is already associated with another driver profile.");
                }
                driver.setUser(user);
            }
        }

        driver.setName(request.getName().trim());
        driver.setPhone(request.getPhone());
        driver.setLicenseNumber(trimmedLicense);

        Driver saved = driverRepository.save(driver);
        return DriverResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DriverResponse deactivateDriver(Long id) {
        Driver driver = driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + id));

        boolean hasActiveDelivery = deliveryRepository.existsByDriverIdAndStatusInAndActiveTrue(
                id, List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS));
        if (hasActiveDelivery) {
            throw new ConflictException("Cannot deactivate driver with active deliveries.");
        }

        driver.setActive(false);
        driver.setStatus(DriverStatus.OFF_DUTY);

        Driver saved = driverRepository.save(driver);
        return DriverResponse.fromEntity(saved);
    }
}
