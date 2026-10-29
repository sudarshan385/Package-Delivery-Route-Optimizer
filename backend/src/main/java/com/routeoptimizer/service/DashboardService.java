package com.routeoptimizer.service;

import com.routeoptimizer.dto.DashboardResponse;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.VehicleStatus;
import com.routeoptimizer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.Role;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PackageRepository packageRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'USER')")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardMetrics() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.getName() != null) {
            currentUser = userRepository.findByUsername(auth.getName()).orElse(null);
        }
        
        if (currentUser != null && currentUser.getRole() == Role.USER) {
            return getUserMetrics(currentUser.getId());
        } else if (currentUser != null && currentUser.getRole() == Role.DRIVER) {
            return getDriverMetrics(currentUser.getId());
        }

        long totalPackages = packageRepository.countByActiveTrue();
        Map<String, Long> packagesByStatus = new HashMap<>();
        List<Object[]> pkgStatusRows = packageRepository.countPackagesByStatus();
        for (Object[] row : pkgStatusRows) {
            PackageStatus status = (PackageStatus) row[0];
            Long count = (Long) row[1];
            packagesByStatus.put(status.name(), count);
        }

        long totalDeliveries = deliveryRepository.countByActiveTrue();
        Map<String, Long> deliveriesByStatus = new HashMap<>();
        List<Object[]> delStatusRows = deliveryRepository.countDeliveriesByStatus();
        for (Object[] row : delStatusRows) {
            DeliveryStatus status = (DeliveryStatus) row[0];
            Long count = (Long) row[1];
            deliveriesByStatus.put(status.name(), count);
        }

        long completedDeliveries = deliveryRepository.countByStatusAndActiveTrue(DeliveryStatus.COMPLETED);
        long failedDeliveries = deliveryRepository.countByStatusAndActiveTrue(DeliveryStatus.FAILED);
        long cancelledDeliveries = deliveryRepository.countByStatusAndActiveTrue(DeliveryStatus.CANCELLED);

        long availableDrivers = driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE);
        long driversOnDelivery = driverRepository.countByStatusAndActiveTrue(DriverStatus.ON_DELIVERY);

        long availableVehicles = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.AVAILABLE);
        long vehiclesInUse = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.IN_USE);

        long totalRoutes = routeRepository.countByIsCurrentTrueAndActiveTrue();
        Double totalDistance = routeRepository.sumDistanceForCurrentRoutes();

        return DashboardResponse.builder()
                .totalPackages(totalPackages)
                .packagesByStatus(packagesByStatus)
                .totalDeliveries(totalDeliveries)
                .deliveriesByStatus(deliveriesByStatus)
                .completedDeliveries(completedDeliveries)
                .failedDeliveries(failedDeliveries)
                .cancelledDeliveries(cancelledDeliveries)
                .availableDrivers(availableDrivers)
                .driversOnDelivery(driversOnDelivery)
                .availableVehicles(availableVehicles)
                .vehiclesInUse(vehiclesInUse)
                .totalRoutes(totalRoutes)
                .totalRouteDistance(totalDistance != null ? Math.round(totalDistance * 100.0) / 100.0 : 0.0)
                .build();
    }

    private DashboardResponse getUserMetrics(Long customerId) {
        // Find packages for the customer
        List<Package> packages = packageRepository.findAll().stream()
            .filter(p -> p.getActive() && p.getCustomer() != null && p.getCustomer().getId().equals(customerId))
            .toList();
        
        long totalPackages = packages.size();
        Map<String, Long> packagesByStatus = new HashMap<>();
        packages.forEach(p -> packagesByStatus.merge(p.getStatus().name(), 1L, Long::sum));

        List<Delivery> deliveries = deliveryRepository.findByPkgCustomerIdAndActiveTrue(customerId);
        long totalDeliveries = deliveries.size();
        Map<String, Long> deliveriesByStatus = new HashMap<>();
        long completed = 0, failed = 0, cancelled = 0;

        for (Delivery d : deliveries) {
            deliveriesByStatus.merge(d.getStatus().name(), 1L, Long::sum);
            if (d.getStatus() == DeliveryStatus.COMPLETED) completed++;
            else if (d.getStatus() == DeliveryStatus.FAILED) failed++;
            else if (d.getStatus() == DeliveryStatus.CANCELLED) cancelled++;
        }

        return DashboardResponse.builder()
                .totalPackages(totalPackages)
                .packagesByStatus(packagesByStatus)
                .totalDeliveries(totalDeliveries)
                .deliveriesByStatus(deliveriesByStatus)
                .completedDeliveries(completed)
                .failedDeliveries(failed)
                .cancelledDeliveries(cancelled)
                .build();
    }

    private DashboardResponse getDriverMetrics(Long userId) {
        Driver driver = driverRepository.findByUserId(userId).orElse(null);
        if (driver == null) {
            return DashboardResponse.builder().build();
        }

        List<Delivery> deliveries = deliveryRepository.findByDriverIdAndActiveTrue(driver.getId());
        long totalDeliveries = deliveries.size();
        Map<String, Long> deliveriesByStatus = new HashMap<>();
        long completed = 0, failed = 0, cancelled = 0;

        for (Delivery d : deliveries) {
            deliveriesByStatus.merge(d.getStatus().name(), 1L, Long::sum);
            if (d.getStatus() == DeliveryStatus.COMPLETED) completed++;
            else if (d.getStatus() == DeliveryStatus.FAILED) failed++;
            else if (d.getStatus() == DeliveryStatus.CANCELLED) cancelled++;
        }

        return DashboardResponse.builder()
                .totalDeliveries(totalDeliveries)
                .deliveriesByStatus(deliveriesByStatus)
                .completedDeliveries(completed)
                .failedDeliveries(failed)
                .cancelledDeliveries(cancelled)
                .build();
    }
}
