package com.routeoptimizer.dto;

import com.routeoptimizer.entity.Vehicle;
import com.routeoptimizer.enums.VehicleStatus;
import com.routeoptimizer.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {

    private Long id;
    private String registrationNumber;
    private VehicleType vehicleType;
    private Double capacity;
    private VehicleStatus status;
    private Boolean inActiveDelivery;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static VehicleResponse fromEntity(Vehicle vehicle) {
        if (vehicle == null) {
            return null;
        }
        boolean activeDel = vehicle.getStatus() == VehicleStatus.IN_USE;
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .registrationNumber(vehicle.getRegistrationNumber())
                .vehicleType(vehicle.getVehicleType())
                .capacity(vehicle.getCapacity())
                .status(vehicle.getStatus())
                .inActiveDelivery(activeDel)
                .active(vehicle.getActive())
                .version(vehicle.getVersion())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
