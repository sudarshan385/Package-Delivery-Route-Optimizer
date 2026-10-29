package com.routeoptimizer.service;

import com.routeoptimizer.dto.AnalyticsResponse;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.VehicleStatus;
import com.routeoptimizer.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PackageRepository packageRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(OffsetDateTime startDate, OffsetDateTime endDate) {
        boolean filterPeriod = startDate != null && endDate != null;

        // 1. Package Analytics
        Map<String, Long> packagesByStatus = new HashMap<>();
        List<Object[]> pkgRows = filterPeriod ?
                packageRepository.countPackagesByStatusBetween(startDate, endDate) :
                packageRepository.countPackagesByStatus();

        long totalPackages = 0;
        for (Object[] row : pkgRows) {
            PackageStatus status = (PackageStatus) row[0];
            Long count = (Long) row[1];
            packagesByStatus.put(status.name(), count);
            totalPackages += count;
        }

        AnalyticsResponse.PackageAnalytics packageStats = AnalyticsResponse.PackageAnalytics.builder()
                .totalPackages(totalPackages)
                .pendingPackages(packagesByStatus.getOrDefault(PackageStatus.PENDING.name(), 0L))
                .inDeliveryPackages(packagesByStatus.getOrDefault(PackageStatus.IN_DELIVERY.name(), 0L))
                .deliveredPackages(packagesByStatus.getOrDefault(PackageStatus.DELIVERED.name(), 0L))
                .failedPackages(packagesByStatus.getOrDefault(PackageStatus.FAILED.name(), 0L))
                .packagesByStatus(packagesByStatus)
                .build();

        // 2. Delivery Analytics
        Map<String, Long> deliveriesByStatus = new HashMap<>();
        List<Object[]> delRows = filterPeriod ?
                deliveryRepository.countDeliveriesByStatusBetween(startDate, endDate) :
                deliveryRepository.countDeliveriesByStatus();

        long totalDeliveries = 0;
        for (Object[] row : delRows) {
            DeliveryStatus status = (DeliveryStatus) row[0];
            Long count = (Long) row[1];
            deliveriesByStatus.put(status.name(), count);
            totalDeliveries += count;
        }

        long completed = deliveriesByStatus.getOrDefault(DeliveryStatus.COMPLETED.name(), 0L);
        double completionRate = totalDeliveries > 0 ?
                Math.round((completed * 100.0 / totalDeliveries) * 100.0) / 100.0 : 0.0;

        AnalyticsResponse.DeliveryAnalytics deliveryStats = AnalyticsResponse.DeliveryAnalytics.builder()
                .totalDeliveries(totalDeliveries)
                .createdDeliveries(deliveriesByStatus.getOrDefault(DeliveryStatus.CREATED.name(), 0L))
                .assignedDeliveries(deliveriesByStatus.getOrDefault(DeliveryStatus.ASSIGNED.name(), 0L))
                .inProgressDeliveries(deliveriesByStatus.getOrDefault(DeliveryStatus.IN_PROGRESS.name(), 0L))
                .completedDeliveries(completed)
                .failedDeliveries(deliveriesByStatus.getOrDefault(DeliveryStatus.FAILED.name(), 0L))
                .cancelledDeliveries(deliveriesByStatus.getOrDefault(DeliveryStatus.CANCELLED.name(), 0L))
                .completionRate(completionRate)
                .deliveriesByStatus(deliveriesByStatus)
                .build();

        // 3. Driver Utilization
        long totalDrivers = driverRepository.countByActiveTrue();
        long availableDrivers = driverRepository.countByStatusAndActiveTrue(DriverStatus.AVAILABLE);
        long onDeliveryDrivers = driverRepository.countByStatusAndActiveTrue(DriverStatus.ON_DELIVERY);
        long offDutyDrivers = driverRepository.countByStatusAndActiveTrue(DriverStatus.OFF_DUTY);
        double driverUtilRate = totalDrivers > 0 ?
                Math.round((onDeliveryDrivers * 100.0 / totalDrivers) * 100.0) / 100.0 : 0.0;

        AnalyticsResponse.DriverUtilization driverUtilization = AnalyticsResponse.DriverUtilization.builder()
                .totalDrivers(totalDrivers)
                .availableDrivers(availableDrivers)
                .onDeliveryDrivers(onDeliveryDrivers)
                .offDutyDrivers(offDutyDrivers)
                .utilizationRate(driverUtilRate)
                .build();

        // 4. Vehicle Utilization
        long totalVehicles = vehicleRepository.countByActiveTrue();
        long availableVehicles = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.AVAILABLE);
        long inUseVehicles = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.IN_USE);
        long maintenanceVehicles = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.MAINTENANCE);
        long offlineVehicles = vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.OFFLINE);
        double vehicleUtilRate = totalVehicles > 0 ?
                Math.round((inUseVehicles * 100.0 / totalVehicles) * 100.0) / 100.0 : 0.0;

        AnalyticsResponse.VehicleUtilization vehicleUtilization = AnalyticsResponse.VehicleUtilization.builder()
                .totalVehicles(totalVehicles)
                .availableVehicles(availableVehicles)
                .inUseVehicles(inUseVehicles)
                .maintenanceVehicles(maintenanceVehicles)
                .offlineVehicles(offlineVehicles)
                .utilizationRate(vehicleUtilRate)
                .build();

        // 5. Route Analytics
        long totalRoutes = routeRepository.countByActiveTrue();
        long activeCurrentRoutes = routeRepository.countByIsCurrentTrueAndActiveTrue();
        Double totalDistance = routeRepository.sumDistanceForCurrentRoutes();
        Double avgDistance = routeRepository.avgDistanceForCurrentRoutes();

        Map<String, Long> algorithmsUsed = new HashMap<>();
        List<Object[]> algoRows = routeRepository.countRoutesByAlgorithm();
        for (Object[] row : algoRows) {
            String algo = (String) row[0];
            Long count = (Long) row[1];
            if (algo != null) {
                algorithmsUsed.put(algo, count);
            }
        }

        AnalyticsResponse.RouteAnalytics routeStats = AnalyticsResponse.RouteAnalytics.builder()
                .totalRoutes(totalRoutes)
                .activeCurrentRoutes(activeCurrentRoutes)
                .totalDistanceKm(totalDistance != null ? Math.round(totalDistance * 100.0) / 100.0 : 0.0)
                .averageDistanceKm(avgDistance != null ? Math.round(avgDistance * 100.0) / 100.0 : 0.0)
                .algorithmsUsed(algorithmsUsed)
                .build();

        return AnalyticsResponse.builder()
                .packageStats(packageStats)
                .deliveryStats(deliveryStats)
                .driverUtilization(driverUtilization)
                .vehicleUtilization(vehicleUtilization)
                .routeStats(routeStats)
                .build();
    }
}
