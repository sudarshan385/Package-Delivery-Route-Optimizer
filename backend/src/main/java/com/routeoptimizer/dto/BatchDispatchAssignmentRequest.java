package com.routeoptimizer.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchDispatchAssignmentRequest {

    @NotNull(message = "Batch type is required (FIRST_MILE or FINAL_MILE)")
    private String batchType;

    @NotNull(message = "Hub ID is required")
    private Long hubId;

    @NotNull(message = "Driver ID is required")
    private Long driverId;

    @NotNull(message = "Vehicle ID is required")
    private Long vehicleId;

    @NotEmpty(message = "At least one delivery leg ID is required for batch dispatch")
    private List<Long> legIds;

    private String notes;
}
