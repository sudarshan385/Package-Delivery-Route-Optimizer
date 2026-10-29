package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private PackageAnalytics packageStats;
    private DeliveryAnalytics deliveryStats;
    private DriverUtilization driverUtilization;
    private VehicleUtilization vehicleUtilization;
    private RouteAnalytics routeStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageAnalytics {
        private Long totalPackages;
        private Long pendingPackages;
        private Long inDeliveryPackages;
        private Long deliveredPackages;
        private Long failedPackages;
        private Map<String, Long> packagesByStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryAnalytics {
        private Long totalDeliveries;
        private Long createdDeliveries;
        private Long assignedDeliveries;
        private Long inProgressDeliveries;
        private Long completedDeliveries;
        private Long failedDeliveries;
        private Long cancelledDeliveries;
        private Double completionRate;
        private Map<String, Long> deliveriesByStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverUtilization {
        private Long totalDrivers;
        private Long availableDrivers;
        private Long onDeliveryDrivers;
        private Long offDutyDrivers;
        private Double utilizationRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleUtilization {
        private Long totalVehicles;
        private Long availableVehicles;
        private Long inUseVehicles;
        private Long maintenanceVehicles;
        private Long offlineVehicles;
        private Double utilizationRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteAnalytics {
        private Long totalRoutes;
        private Long activeCurrentRoutes;
        private Double totalDistanceKm;
        private Double averageDistanceKm;
        private Map<String, Long> algorithmsUsed;
    }
}
