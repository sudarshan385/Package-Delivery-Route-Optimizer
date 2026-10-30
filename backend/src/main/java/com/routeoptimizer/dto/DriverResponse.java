package com.routeoptimizer.dto;

import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.enums.DriverStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {

    private Long id;
    private Long userId;
    private String username;
    private String name;
    private String phone;
    private String licenseNumber;
    private DriverStatus status;
    private Boolean inActiveDelivery;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static DriverResponse fromEntity(Driver driver) {
        if (driver == null) {
            return null;
        }
        boolean activeDel = driver.getStatus() == DriverStatus.ON_DELIVERY;
        return DriverResponse.builder()
                .id(driver.getId())
                .userId(driver.getUser() != null ? driver.getUser().getId() : null)
                .username(driver.getUser() != null ? driver.getUser().getUsername() : null)
                .name(driver.getName())
                .phone(driver.getPhone())
                .licenseNumber(driver.getLicenseNumber())
                .status(driver.getStatus())
                .inActiveDelivery(activeDel)
                .active(driver.getActive())
                .version(driver.getVersion())
                .createdAt(driver.getCreatedAt())
                .updatedAt(driver.getUpdatedAt())
                .build();
    }
}
