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
public class BatchRouteProposalResponse {
    private String batchType; // FIRST_MILE or FINAL_MILE
    private Long hubId;
    private String hubName;
    private Double hubLatitude;
    private Double hubLongitude;
    private String hubAddress;

    private Double configuredRadiusKm;
    private Double effectiveRadiusKm;
    private String corridorCalculationRule;

    private Long vehicleId;
    private String vehicleRegistrationNumber;
    private Double vehicleCapacityKg;
    private Double totalBatchWeightKg;
    private Double remainingCapacityKg;
    private boolean exceedsVehicleCapacity;

    @Builder.Default
    private List<Long> includedLegIds = new ArrayList<>();
    @Builder.Default
    private List<Long> excludedLegIds = new ArrayList<>();

    @Builder.Default
    private List<CandidateStopEvaluation> allCandidates = new ArrayList<>();

    @Builder.Default
    private List<CandidateStopEvaluation> orderedIncludedStops = new ArrayList<>();

    @Builder.Default
    private List<BatchGroupProposal> capacityGroups = new ArrayList<>();

    private Double totalRouteDistanceKm;
    private Double totalRouteDurationMin;
    private Double initialTourDistanceKm;
    private Double finalTourDistanceKm;
    private Double initialTourDurationMin;
    private Double finalTourDurationMin;
    private String routeGeometryGeoJson;
    private String routingProvider;
    private String geometrySource;
    private String optimizationAlgorithm;
    private String heuristicNotice;
    private Boolean hubReturnPreserved;
}
