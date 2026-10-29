package com.routeoptimizer.dto;

import com.routeoptimizer.enums.DriverStatus;
import com.routeoptimizer.enums.VehicleStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchAssignmentResponse {
    private Long routeId;
    private Long driverId;
    private String driverName;
    private DriverStatus driverStatus;
    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private Double vehicleCapacityKg;
    private VehicleStatus vehicleStatus;
    private Double totalWeightKg;
    private List<Long> assignedDeliveryIds;
    private String status;
    private OffsetDateTime assignedAt;
}
