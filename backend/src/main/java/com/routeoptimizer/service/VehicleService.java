package com.routeoptimizer.service;

import com.routeoptimizer.dto.VehicleRequest;
import com.routeoptimizer.dto.VehicleResponse;
import com.routeoptimizer.entity.Vehicle;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.VehicleStatus;
import com.routeoptimizer.enums.VehicleType;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DeliveryRepository deliveryRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VehicleResponse createVehicle(VehicleRequest request) {
        String trimmedReg = request.getRegistrationNumber().trim().toUpperCase();
        if (vehicleRepository.existsByRegistrationNumber(trimmedReg)) {
            throw new ConflictException("Vehicle with registration number '" + trimmedReg + "' already exists.");
        }

        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BadRequestException("Capacity must be greater than zero.");
        }

        Vehicle vehicle = Vehicle.builder()
                .registrationNumber(trimmedReg)
                .vehicleType(request.getVehicleType())
                .capacity(request.getCapacity())
                .status(request.getStatus() != null ? request.getStatus() : VehicleStatus.AVAILABLE)
                .build();
        vehicle.setActive(true);
        vehicle.setVersion(0L);

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.fromEntity(saved);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));
        return VehicleResponse.fromEntity(vehicle);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER')")
    @Transactional(readOnly = true)
    public Page<VehicleResponse> getAllVehicles(VehicleStatus status, VehicleType vehicleType, Boolean active, Pageable pageable) {
        Page<Vehicle> page;
        if (status != null && active != null) {
            page = vehicleRepository.findByStatusAndActive(status, active, pageable);
        } else if (status != null) {
            page = vehicleRepository.findByStatus(status, pageable);
        } else if (vehicleType != null) {
            page = vehicleRepository.findByVehicleType(vehicleType, pageable);
        } else if (active != null) {
            page = vehicleRepository.findByActive(active, pageable);
        } else {
            page = vehicleRepository.findAll(pageable);
        }

        return page.map(VehicleResponse::fromEntity);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        String trimmedReg = request.getRegistrationNumber().trim().toUpperCase();
        if (vehicleRepository.existsByRegistrationNumberAndIdNot(trimmedReg, id)) {
            throw new ConflictException("Vehicle with registration number '" + trimmedReg + "' already exists.");
        }

        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BadRequestException("Capacity must be greater than zero.");
        }

        if (request.getStatus() != null && request.getStatus() != vehicle.getStatus()) {
            boolean hasActiveDelivery = deliveryRepository.existsByVehicleIdAndStatusInAndActiveTrue(
                    id, List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS));
            if (hasActiveDelivery && request.getStatus() != VehicleStatus.IN_USE) {
                throw new ConflictException("Cannot change vehicle status to " + request.getStatus() + " while vehicle is in active delivery.");
            }
            vehicle.setStatus(request.getStatus());
        }

        vehicle.setRegistrationNumber(trimmedReg);
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setCapacity(request.getCapacity());

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.fromEntity(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public VehicleResponse deactivateVehicle(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + id));

        boolean hasActiveDelivery = deliveryRepository.existsByVehicleIdAndStatusInAndActiveTrue(
                id, List.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_PROGRESS));
        if (hasActiveDelivery) {
            throw new ConflictException("Cannot deactivate vehicle with active deliveries.");
        }

        vehicle.setActive(false);
        vehicle.setStatus(VehicleStatus.OFFLINE);

        Vehicle saved = vehicleRepository.save(vehicle);
        return VehicleResponse.fromEntity(saved);
    }
}
