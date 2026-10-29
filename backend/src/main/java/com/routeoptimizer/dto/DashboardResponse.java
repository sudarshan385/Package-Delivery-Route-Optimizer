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
public class DashboardResponse {

    private Long totalPackages;
    private Map<String, Long> packagesByStatus;
    private Long totalDeliveries;
    private Map<String, Long> deliveriesByStatus;
    private Long completedDeliveries;
    private Long failedDeliveries;
    private Long cancelledDeliveries;
    private Long availableDrivers;
    private Long driversOnDelivery;
    private Long availableVehicles;
    private Long vehiclesInUse;
    private Long totalRoutes;
    private Double totalRouteDistance;
}
