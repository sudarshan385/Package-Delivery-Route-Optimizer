package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchGroupProposal {
    private Integer groupIndex;
    private String label;
    @Builder.Default
    private List<Long> legIds = new ArrayList<>();
    @Builder.Default
    private List<Long> deliveryIds = new ArrayList<>();
    private Double totalWeightKg;
    private Integer packageCount;
    private Double estimatedDistanceKm;
    private Double estimatedDurationMin;
    private boolean compliantWithVehicleCapacity;
}
