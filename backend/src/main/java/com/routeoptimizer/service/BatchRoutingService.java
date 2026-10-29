package com.routeoptimizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.optimizer.DijkstraRouteOptimizationStrategy;
import com.routeoptimizer.optimizer.OptimizationResult;
import com.routeoptimizer.repository.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchRoutingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLegRepository deliveryLegRepository;
    private final LocationRepository locationRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final RouteEdgeRepository routeEdgeRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final PackageRepository packageRepository;
    private final UserRepository userRepository;
    private final RoutingService routingService;
    private final DijkstraRouteOptimizationStrategy dijkstraStrategy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${delivery.batch.pickup-radius-km:10.0}")
    private double configuredPickupRadiusKm;

    @Value("${delivery.batch.delivery-radius-km:10.0}")
    private double configuredDeliveryRadiusKm;

    public double getConfiguredPickupRadiusKm() {
        return configuredPickupRadiusKm > 0 ? configuredPickupRadiusKm : 10.0;
    }

    public double getConfiguredDeliveryRadiusKm() {
        return configuredDeliveryRadiusKm > 0 ? configuredDeliveryRadiusKm : 10.0;
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Phase 2 & 4: Batch Pickup Route Proposal with Configurable Corridor Distance Rule
     */
    @Transactional(readOnly = true)
    public BatchRouteProposalResponse proposeBatchPickup(Long hubId, Double maxRadiusKm, Long vehicleId) {
        double effectiveRadius = (maxRadiusKm != null && maxRadiusKm > 0) ? maxRadiusKm : getConfiguredPickupRadiusKm();
        if (effectiveRadius <= 0) {
            throw new BadRequestException("Batch pickup radius must be a positive number.");
        }

        Location hub = locationRepository.findById(hubId)
                .orElseThrow(() -> new ResourceNotFoundException("Central Hub not found with id: " + hubId));

        Vehicle vehicle = null;
        Double vehicleCapacity = null;
        if (vehicleId != null) {
            vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
            vehicleCapacity = vehicle.getCapacity();
        }

        // 1. Discover all candidate FIRST_MILE legs awaiting pickup for this Hub
        List<DeliveryLeg> allFirstMileLegs = deliveryLegRepository.findByLegTypeAndStatusAndActiveTrue(
                LegType.FIRST_MILE, LegStatus.PENDING);

        List<CandidateStopEvaluation> candidateEvals = new ArrayList<>();
        List<DeliveryLeg> compatibleLegs = new ArrayList<>();

        for (DeliveryLeg leg : allFirstMileLegs) {
            Delivery delivery = leg.getDelivery();
            if (delivery == null || !Boolean.TRUE.equals(delivery.getActive())) {
                continue;
            }

            // Must match destination hub
            boolean hubMatches = (leg.getDestinationLocation() != null && leg.getDestinationLocation().getId().equals(hub.getId())) ||
                    (delivery.getHub() != null && delivery.getHub().getId().equals(hub.getId()));
            if (!hubMatches) {
                continue;
            }

            Package pkg = delivery.getPackage();
            Location pickupLoc = leg.getSourceLocation();
            double pkgWeight = (pkg != null && pkg.getWeight() != null) ? pkg.getWeight() : 1.0;

            CandidateStopEvaluation eval = CandidateStopEvaluation.builder()
                    .legId(leg.getId())
                    .deliveryId(delivery.getId())
                    .trackingNumber(pkg != null ? pkg.getTrackingNumber() : "DEL-" + delivery.getId())
                    .packageWeightKg(pkgWeight)
                    .locationId(pickupLoc.getId())
                    .locationName(pickupLoc.getName())
                    .latitude(pickupLoc.getLatitude())
                    .longitude(pickupLoc.getLongitude())
                    .address(pickupLoc.getAddress())
                    .senderName(pkg != null ? pkg.getSenderName() : "Sender")
                    .receiverName(pkg != null ? pkg.getReceiverName() : null)
                    .contactPhone(pkg != null ? pkg.getSenderPhone() : null)
                    .build();

            // Geographical sanity check
            if (pickupLoc.getLatitude() < 12.5 || pickupLoc.getLatitude() > 13.5 ||
                pickupLoc.getLongitude() < 77.2 || pickupLoc.getLongitude() > 78.0) {
                eval.setEligible(false);
                eval.setExclusionReason("Pickup coordinates outside Bengaluru metropolitan boundaries.");
                candidateEvals.add(eval);
                continue;
            }

            // Calculate road distance to Hub
            double directRoadDistKm = 0.0;
            try {
                RoutingResponse routeToHub = routingService.calculateRoute(
                        pickupLoc.getLatitude(), pickupLoc.getLongitude(),
                        hub.getLatitude(), hub.getLongitude()
                );
                directRoadDistKm = routeToHub.getDistanceKm();
            } catch (Exception ex) {
                directRoadDistKm = calculateHaversineKm(
                        pickupLoc.getLatitude(), pickupLoc.getLongitude(),
                        hub.getLatitude(), hub.getLongitude()
                );
            }
            eval.setDirectDistanceToHubKm(directRoadDistKm);

            compatibleLegs.add(leg);
            candidateEvals.add(eval);
        }

        // 2. Compute Corridor Detour Distance
        // If there are compatible legs, choose the anchor stop (the one farthest from hub to define the inward corridor)
        Location anchorLoc = null;
        double maxDist = -1.0;
        for (CandidateStopEvaluation eval : candidateEvals) {
            if (eval.getDirectDistanceToHubKm() != null && eval.getDirectDistanceToHubKm() > maxDist) {
                maxDist = eval.getDirectDistanceToHubKm();
                anchorLoc = Location.builder().latitude(eval.getLatitude()).longitude(eval.getLongitude()).build();
            }
        }

        double anchorToHubDist = maxDist > 0 ? maxDist : 0.0;

        List<CandidateStopEvaluation> eligibleCandidates = new ArrayList<>();

        for (CandidateStopEvaluation eval : candidateEvals) {
            if (eval.getExclusionReason() != null) {
                continue; // already excluded by geography
            }

            double detourKm;
            if (anchorLoc == null || anchorToHubDist <= 0) {
                detourKm = eval.getDirectDistanceToHubKm();
            } else {
                double anchorToStopDist = calculateHaversineKm(anchorLoc.getLatitude(), anchorLoc.getLongitude(),
                        eval.getLatitude(), eval.getLongitude());
                detourKm = anchorToStopDist + eval.getDirectDistanceToHubKm() - anchorToHubDist;
                if (detourKm < 0) detourKm = 0.0;
            }
            eval.setDetourKm(detourKm);

            // Corridor limit rule: Direct distance to Hub and Corridor Detour must not exceed configured limit
            if (eval.getDirectDistanceToHubKm() != null && eval.getDirectDistanceToHubKm() > effectiveRadius) {
                eval.setEligible(false);
                eval.setExclusionReason(String.format("Pickup location (%.1f km) exceeds configured route-corridor limit (%.1f km)",
                        eval.getDirectDistanceToHubKm(), effectiveRadius));
            } else if (detourKm > effectiveRadius) {
                eval.setEligible(false);
                eval.setExclusionReason(String.format("Detour (%.1f km) exceeds configured corridor limit (%.1f km)",
                        detourKm, effectiveRadius));
            } else {
                eval.setEligible(true);
                eligibleCandidates.add(eval);
            }
        }

        // 3. Vehicle Capacity Constraint & Batch Grouping
        List<BatchGroupProposal> capacityGroups = new ArrayList<>();
        List<CandidateStopEvaluation> includedInPrimaryBatch = new ArrayList<>();
        double cumulativeWeight = 0.0;
        int groupIndex = 1;

        List<Long> currentGroupLegs = new ArrayList<>();
        List<Long> currentGroupDeliveries = new ArrayList<>();
        double currentGroupWeight = 0.0;

        for (CandidateStopEvaluation cand : eligibleCandidates) {
            double candWeight = cand.getPackageWeightKg() != null ? cand.getPackageWeightKg() : 1.0;

            if (vehicleCapacity != null && vehicleCapacity > 0) {
                if (currentGroupWeight + candWeight > vehicleCapacity && !currentGroupLegs.isEmpty()) {
                    // Finalize current group
                    capacityGroups.add(BatchGroupProposal.builder()
                            .groupIndex(groupIndex++)
                            .label("Batch Group " + (groupIndex - 1))
                            .legIds(new ArrayList<>(currentGroupLegs))
                            .deliveryIds(new ArrayList<>(currentGroupDeliveries))
                            .totalWeightKg(currentGroupWeight)
                            .packageCount(currentGroupLegs.size())
                            .compliantWithVehicleCapacity(true)
                            .build());

                    currentGroupLegs.clear();
                    currentGroupDeliveries.clear();
                    currentGroupWeight = 0.0;
                }
            }

            currentGroupLegs.add(cand.getLegId());
            currentGroupDeliveries.add(cand.getDeliveryId());
            currentGroupWeight += candWeight;

            // Add to primary batch if vehicle capacity allows
            if (vehicleCapacity == null || cumulativeWeight + candWeight <= vehicleCapacity) {
                includedInPrimaryBatch.add(cand);
                cumulativeWeight += candWeight;
            } else {
                cand.setEligible(false);
                cand.setExclusionReason(String.format("Vehicle capacity exceeded: requires %.1f kg, but vehicle capacity is %.1f kg (current load: %.1f kg)",
                        candWeight, vehicleCapacity, cumulativeWeight));
            }
        }

        if (!currentGroupLegs.isEmpty()) {
            capacityGroups.add(BatchGroupProposal.builder()
                    .groupIndex(groupIndex)
                    .label("Batch Group " + groupIndex)
                    .legIds(new ArrayList<>(currentGroupLegs))
                    .deliveryIds(new ArrayList<>(currentGroupDeliveries))
                    .totalWeightKg(currentGroupWeight)
                    .packageCount(currentGroupLegs.size())
                    .compliantWithVehicleCapacity(vehicleCapacity == null || currentGroupWeight <= vehicleCapacity)
                    .build());
        }

        // 4. Optimize Stop Sequence for Included Candidates
        MultiStopTourResult tourResult = optimizeDirectedBatchTour(includedInPrimaryBatch, hub, true);
        List<CandidateStopEvaluation> orderedStops = tourResult.getOrderedStops();

        List<Long> includedIds = orderedStops.stream().map(CandidateStopEvaluation::getLegId).toList();
        List<Long> excludedIds = candidateEvals.stream()
                .filter(c -> !includedIds.contains(c.getLegId()))
                .map(CandidateStopEvaluation::getLegId)
                .toList();

        return BatchRouteProposalResponse.builder()
                .batchType("FIRST_MILE")
                .hubId(hub.getId())
                .hubName(hub.getName())
                .hubLatitude(hub.getLatitude())
                .hubLongitude(hub.getLongitude())
                .hubAddress(hub.getAddress())
                .configuredRadiusKm(getConfiguredPickupRadiusKm())
                .effectiveRadiusKm(effectiveRadius)
                .corridorCalculationRule("Direct Road Detour D(P_anchor -> P_candidate -> Hub) - D(P_anchor -> Hub) <= " + effectiveRadius + " km")
                .vehicleId(vehicleId)
                .vehicleRegistrationNumber(vehicle != null ? vehicle.getRegistrationNumber() : null)
                .vehicleCapacityKg(vehicleCapacity)
                .totalBatchWeightKg(cumulativeWeight)
                .remainingCapacityKg(vehicleCapacity != null ? Math.max(0, vehicleCapacity - cumulativeWeight) : null)
                .exceedsVehicleCapacity(vehicleCapacity != null && cumulativeWeight > vehicleCapacity)
                .includedLegIds(includedIds)
                .excludedLegIds(excludedIds)
                .allCandidates(candidateEvals)
                .orderedIncludedStops(orderedStops)
                .capacityGroups(capacityGroups)
                .totalRouteDistanceKm(tourResult.getFinalDistanceKm())
                .totalRouteDurationMin(tourResult.getFinalDurationMin())
                .initialTourDistanceKm(tourResult.getInitialDistanceKm())
                .finalTourDistanceKm(tourResult.getFinalDistanceKm())
                .initialTourDurationMin(tourResult.getInitialDurationMin())
                .finalTourDurationMin(tourResult.getFinalDurationMin())
                .routeGeometryGeoJson(tourResult.getFullGeoJson())
                .routingProvider("OSRM Real Road Network (Bengaluru)")
                .geometrySource(tourResult.getGeometrySource())
                .optimizationAlgorithm("Dijkstra Directed Road Matrix + Nearest Neighbor + Directed 2-Opt Local Search")
                .heuristicNotice("Nearest Neighbor with directed 2-Opt local search is a heuristic algorithm that delivers rapid, high-quality routing solutions but does not mathematically guarantee the global minimum multi-stop tour.")
                .hubReturnPreserved(true)
                .build();
    }

    /**
     * Phase 3 & 4: Batch Final-Mile Delivery Route Proposal
     */
    @Transactional(readOnly = true)
    public BatchRouteProposalResponse proposeBatchDelivery(Long hubId, Double maxRadiusKm, Long vehicleId) {
        double effectiveRadius = (maxRadiusKm != null && maxRadiusKm > 0) ? maxRadiusKm : getConfiguredDeliveryRadiusKm();
        if (effectiveRadius <= 0) {
            throw new BadRequestException("Batch delivery radius must be a positive number.");
        }

        Location hub = locationRepository.findById(hubId)
                .orElseThrow(() -> new ResourceNotFoundException("Central Hub not found with id: " + hubId));

        Vehicle vehicle = null;
        Double vehicleCapacity = null;
        if (vehicleId != null) {
            vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + vehicleId));
            vehicleCapacity = vehicle.getCapacity();
        }

        // 1. Discover all candidate FINAL_MILE legs that have been genuinely received at this Hub
        List<DeliveryLeg> allFinalMileLegs = deliveryLegRepository.findByLegTypeAndStatusAndActiveTrue(
                LegType.FINAL_MILE, LegStatus.PENDING);

        List<CandidateStopEvaluation> candidateEvals = new ArrayList<>();
        List<DeliveryLeg> compatibleLegs = new ArrayList<>();

        for (DeliveryLeg leg : allFinalMileLegs) {
            Delivery delivery = leg.getDelivery();
            if (delivery == null || !Boolean.TRUE.equals(delivery.getActive())) {
                continue;
            }

            // Must be physically received at Central Hub
            boolean isAtHub = "HUB_RECEIVED".equalsIgnoreCase(delivery.getCurrentStage());
            boolean hubMatches = (leg.getSourceLocation() != null && leg.getSourceLocation().getId().equals(hub.getId())) ||
                    (delivery.getHub() != null && delivery.getHub().getId().equals(hub.getId()));

            Package pkg = delivery.getPackage();
            Location destLoc = leg.getDestinationLocation();
            double pkgWeight = (pkg != null && pkg.getWeight() != null) ? pkg.getWeight() : 1.0;

            CandidateStopEvaluation eval = CandidateStopEvaluation.builder()
                    .legId(leg.getId())
                    .deliveryId(delivery.getId())
                    .trackingNumber(pkg != null ? pkg.getTrackingNumber() : "DEL-" + delivery.getId())
                    .packageWeightKg(pkgWeight)
                    .locationId(destLoc.getId())
                    .locationName(destLoc.getName())
                    .latitude(destLoc.getLatitude())
                    .longitude(destLoc.getLongitude())
                    .address(destLoc.getAddress())
                    .senderName(pkg != null ? pkg.getSenderName() : "Sender")
                    .receiverName(pkg != null ? pkg.getReceiverName() : null)
                    .contactPhone(pkg != null ? pkg.getReceiverPhone() : null)
                    .build();

            if (!hubMatches) {
                continue;
            }

            if (!isAtHub) {
                eval.setEligible(false);
                eval.setExclusionReason("Package has not yet arrived at Central Hub (Current Stage: " + delivery.getCurrentStage() + ")");
                candidateEvals.add(eval);
                continue;
            }

            // Geographical sanity check
            if (destLoc.getLatitude() < 12.5 || destLoc.getLatitude() > 13.5 ||
                destLoc.getLongitude() < 77.2 || destLoc.getLongitude() > 78.0) {
                eval.setEligible(false);
                eval.setExclusionReason("Destination coordinates outside Bengaluru metropolitan boundaries.");
                candidateEvals.add(eval);
                continue;
            }

            // Road distance from Hub
            double distFromHubKm = 0.0;
            try {
                RoutingResponse route = routingService.calculateRoute(
                        hub.getLatitude(), hub.getLongitude(),
                        destLoc.getLatitude(), destLoc.getLongitude()
                );
                distFromHubKm = route.getDistanceKm();
            } catch (Exception ex) {
                distFromHubKm = calculateHaversineKm(
                        hub.getLatitude(), hub.getLongitude(),
                        destLoc.getLatitude(), destLoc.getLongitude()
                );
            }
            eval.setDirectDistanceToHubKm(distFromHubKm);

            if (distFromHubKm > effectiveRadius) {
                eval.setEligible(false);
                eval.setExclusionReason(String.format("Destination distance (%.1f km) exceeds delivery corridor limit (%.1f km)",
                        distFromHubKm, effectiveRadius));
            } else {
                eval.setEligible(true);
                compatibleLegs.add(leg);
            }

            candidateEvals.add(eval);
        }

        // Capacity grouping & primary selection
        List<CandidateStopEvaluation> eligibleCandidates = candidateEvals.stream().filter(CandidateStopEvaluation::isEligible).toList();
        List<BatchGroupProposal> capacityGroups = new ArrayList<>();
        List<CandidateStopEvaluation> includedInPrimaryBatch = new ArrayList<>();
        double cumulativeWeight = 0.0;
        int groupIndex = 1;

        List<Long> currentGroupLegs = new ArrayList<>();
        List<Long> currentGroupDeliveries = new ArrayList<>();
        double currentGroupWeight = 0.0;

        for (CandidateStopEvaluation cand : eligibleCandidates) {
            double candWeight = cand.getPackageWeightKg() != null ? cand.getPackageWeightKg() : 1.0;

            if (vehicleCapacity != null && vehicleCapacity > 0) {
                if (currentGroupWeight + candWeight > vehicleCapacity && !currentGroupLegs.isEmpty()) {
                    capacityGroups.add(BatchGroupProposal.builder()
                            .groupIndex(groupIndex++)
                            .label("Delivery Batch Group " + (groupIndex - 1))
                            .legIds(new ArrayList<>(currentGroupLegs))
                            .deliveryIds(new ArrayList<>(currentGroupDeliveries))
                            .totalWeightKg(currentGroupWeight)
                            .packageCount(currentGroupLegs.size())
                            .compliantWithVehicleCapacity(true)
                            .build());

                    currentGroupLegs.clear();
                    currentGroupDeliveries.clear();
                    currentGroupWeight = 0.0;
                }
            }

            currentGroupLegs.add(cand.getLegId());
            currentGroupDeliveries.add(cand.getDeliveryId());
            currentGroupWeight += candWeight;

            if (vehicleCapacity == null || cumulativeWeight + candWeight <= vehicleCapacity) {
                includedInPrimaryBatch.add(cand);
                cumulativeWeight += candWeight;
            } else {
                cand.setEligible(false);
                cand.setExclusionReason(String.format("Vehicle capacity exceeded: package weight %.1f kg exceeds vehicle limit %.1f kg",
                        candWeight, vehicleCapacity));
            }
        }

        if (!currentGroupLegs.isEmpty()) {
            capacityGroups.add(BatchGroupProposal.builder()
                    .groupIndex(groupIndex)
                    .label("Delivery Batch Group " + groupIndex)
                    .legIds(new ArrayList<>(currentGroupLegs))
                    .deliveryIds(new ArrayList<>(currentGroupDeliveries))
                    .totalWeightKg(currentGroupWeight)
                    .packageCount(currentGroupLegs.size())
                    .compliantWithVehicleCapacity(vehicleCapacity == null || currentGroupWeight <= vehicleCapacity)
                    .build());
        }

        // Stop order optimization: Start at Hub -> visit destinations
        MultiStopTourResult tourResult = optimizeDirectedBatchTour(includedInPrimaryBatch, hub, false);
        List<CandidateStopEvaluation> orderedStops = tourResult.getOrderedStops();

        List<Long> includedIds = orderedStops.stream().map(CandidateStopEvaluation::getLegId).toList();
        List<Long> excludedIds = candidateEvals.stream()
                .filter(c -> !includedIds.contains(c.getLegId()))
                .map(CandidateStopEvaluation::getLegId)
                .toList();

        return BatchRouteProposalResponse.builder()
                .batchType("FINAL_MILE")
                .hubId(hub.getId())
                .hubName(hub.getName())
                .hubLatitude(hub.getLatitude())
                .hubLongitude(hub.getLongitude())
                .hubAddress(hub.getAddress())
                .configuredRadiusKm(getConfiguredDeliveryRadiusKm())
                .effectiveRadiusKm(effectiveRadius)
                .corridorCalculationRule("Real road distance from Hub to destination <= " + effectiveRadius + " km")
                .vehicleId(vehicleId)
                .vehicleRegistrationNumber(vehicle != null ? vehicle.getRegistrationNumber() : null)
                .vehicleCapacityKg(vehicleCapacity)
                .totalBatchWeightKg(cumulativeWeight)
                .remainingCapacityKg(vehicleCapacity != null ? Math.max(0, vehicleCapacity - cumulativeWeight) : null)
                .exceedsVehicleCapacity(vehicleCapacity != null && cumulativeWeight > vehicleCapacity)
                .includedLegIds(includedIds)
                .excludedLegIds(excludedIds)
                .allCandidates(candidateEvals)
                .orderedIncludedStops(orderedStops)
                .capacityGroups(capacityGroups)
                .totalRouteDistanceKm(tourResult.getFinalDistanceKm())
                .totalRouteDurationMin(tourResult.getFinalDurationMin())
                .initialTourDistanceKm(tourResult.getInitialDistanceKm())
                .finalTourDistanceKm(tourResult.getFinalDistanceKm())
                .initialTourDurationMin(tourResult.getInitialDurationMin())
                .finalTourDurationMin(tourResult.getFinalDurationMin())
                .routeGeometryGeoJson(tourResult.getFullGeoJson())
                .routingProvider("OSRM Real Road Network (Bengaluru)")
                .geometrySource(tourResult.getGeometrySource())
                .optimizationAlgorithm("Dijkstra Directed Road Matrix + Nearest Neighbor + Directed 2-Opt Local Search")
                .heuristicNotice("Nearest Neighbor with directed 2-Opt local search is a heuristic algorithm that delivers rapid, high-quality routing solutions but does not mathematically guarantee the global minimum multi-stop tour.")
                .hubReturnPreserved(true)
                .build();
    }

    /**
     * Phase 2 & 3: Transactional Batch Dispatch Assignment
     */
    @Transactional
    public BatchDispatchAssignmentResponse dispatchBatch(BatchDispatchAssignmentRequest request, String currentUsername) {
        if (request.getLegIds() == null || request.getLegIds().isEmpty()) {
            throw new BadRequestException("At least one leg must be included in the batch dispatch request.");
        }

        // 1. Pessimistic lock driver
        Driver driver = driverRepository.findByIdWithLock(request.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found with id: " + request.getDriverId()));
        if (!Boolean.TRUE.equals(driver.getActive())) {
            throw new BadRequestException("Driver is inactive: " + driver.getLicenseNumber());
        }
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            throw new ConflictException("Driver is not available (current status: " + driver.getStatus() + ").");
        }

        // 2. Pessimistic lock vehicle
        Vehicle vehicle = vehicleRepository.findByIdWithLock(request.getVehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found with id: " + request.getVehicleId()));
        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            throw new BadRequestException("Vehicle is inactive: " + vehicle.getRegistrationNumber());
        }
        if (vehicle.getStatus() != VehicleStatus.AVAILABLE) {
            throw new ConflictException("Vehicle is not available (current status: " + vehicle.getStatus() + ").");
        }

        // 3. Lock Central Hub
        Location hub = locationRepository.findById(request.getHubId())
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + request.getHubId()));

        boolean isFirstMile = "FIRST_MILE".equalsIgnoreCase(request.getBatchType());

        // 4. Pessimistic lock each leg and validate eligibility
        List<DeliveryLeg> lockedLegs = new ArrayList<>();
        double totalBatchWeight = 0.0;
        List<Long> assignedDeliveryIds = new ArrayList<>();

        for (Long legId : request.getLegIds()) {
            DeliveryLeg leg = deliveryLegRepository.findByIdWithLock(legId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery leg not found with id: " + legId));

            if (!Boolean.TRUE.equals(leg.getActive())) {
                throw new BadRequestException("Delivery leg " + legId + " is inactive.");
            }
            if (leg.getStatus() != LegStatus.PENDING) {
                throw new ConflictException(String.format("Delivery leg %d is not pending assignment (current status: %s).",
                        legId, leg.getStatus()));
            }

            Delivery delivery = leg.getDelivery();
            if (delivery == null || !Boolean.TRUE.equals(delivery.getActive())) {
                throw new BadRequestException("Delivery for leg " + legId + " is invalid or inactive.");
            }

            if (isFirstMile) {
                if (leg.getLegType() != LegType.FIRST_MILE) {
                    throw new BadRequestException("Leg " + legId + " is not a FIRST_MILE leg.");
                }
            } else {
                if (leg.getLegType() != LegType.FINAL_MILE) {
                    throw new BadRequestException("Leg " + legId + " is not a FINAL_MILE leg.");
                }
                if (!"HUB_RECEIVED".equalsIgnoreCase(delivery.getCurrentStage())) {
                    throw new BadRequestException(String.format("Package for delivery %d has not been received at hub (current stage: %s).",
                            delivery.getId(), delivery.getCurrentStage()));
                }
            }

            double weight = (delivery.getPackage() != null && delivery.getPackage().getWeight() != null) ?
                    delivery.getPackage().getWeight() : 1.0;
            totalBatchWeight += weight;

            lockedLegs.add(leg);
            assignedDeliveryIds.add(delivery.getId());
        }

        // 5. Validate Vehicle Payload Capacity
        if (vehicle.getCapacity() != null && totalBatchWeight > vehicle.getCapacity()) {
            throw new BadRequestException(String.format("Combined batch weight (%.1f kg) exceeds vehicle capacity (%.1f kg). Please reduce batch size.",
                    totalBatchWeight, vehicle.getCapacity()));
        }

        // 6. Create Route and Route Stops
        List<CandidateStopEvaluation> stopEvals = new ArrayList<>();
        for (DeliveryLeg leg : lockedLegs) {
            Location loc = isFirstMile ? leg.getSourceLocation() : leg.getDestinationLocation();
            stopEvals.add(CandidateStopEvaluation.builder()
                    .legId(leg.getId())
                    .deliveryId(leg.getDelivery().getId())
                    .locationId(loc.getId())
                    .locationName(loc.getName())
                    .latitude(loc.getLatitude())
                    .longitude(loc.getLongitude())
                    .address(loc.getAddress())
                    .packageWeightKg(leg.getDelivery().getPackage() != null ? leg.getDelivery().getPackage().getWeight() : 1.0)
                    .build());
        }

        MultiStopTourResult tourResult = optimizeDirectedBatchTour(stopEvals, hub, isFirstMile);
        List<CandidateStopEvaluation> orderedStops = tourResult.getOrderedStops();

        Location routeSource = isFirstMile ? (orderedStops.isEmpty() ? hub :
                locationRepository.findById(orderedStops.get(0).getLocationId()).orElse(hub)) : hub;
        Location routeDest = isFirstMile ? hub : (orderedStops.isEmpty() ? hub :
                locationRepository.findById(orderedStops.get(orderedStops.size() - 1).getLocationId()).orElse(hub));

        Route route = Route.builder()
                .delivery(lockedLegs.get(0).getDelivery())
                .sourceLocation(routeSource)
                .destinationLocation(routeDest)
                .versionNumber(1)
                .isCurrent(true)
                .totalDistance(tourResult.getFinalDistanceKm())
                .estimatedTime(tourResult.getFinalDurationMin())
                .algorithmUsed("BATCH_CORRIDOR_2OPT")
                .build();
        Route savedRoute = routeRepository.save(route);

        int stopOrder = 1;

        if (!isFirstMile) {
            // Hub Start Stop
            RouteStop hubStart = RouteStop.builder()
                    .route(savedRoute)
                    .location(hub)
                    .stopOrder(stopOrder++)
                    .stopType(StopType.SOURCE)
                    .estimatedArrivalTime(OffsetDateTime.now())
                    .build();
            routeStopRepository.save(hubStart);
        }

        for (int i = 0; i < orderedStops.size(); i++) {
            CandidateStopEvaluation stopEval = orderedStops.get(i);
            Location loc = locationRepository.findById(stopEval.getLocationId()).orElse(null);
            DeliveryLeg leg = lockedLegs.stream().filter(l -> l.getId().equals(stopEval.getLegId())).findFirst().orElse(null);

            RouteStop stop = RouteStop.builder()
                    .route(savedRoute)
                    .location(loc)
                    .stopOrder(stopOrder++)
                    .stopType(isFirstMile ? StopType.WAYPOINT : StopType.DESTINATION)
                    .delivery(leg != null ? leg.getDelivery() : null)
                    .customerStopNumber(i + 1)
                    .estimatedArrivalTime(OffsetDateTime.now().plusMinutes((long) (stopEval.getEstimatedArrivalMinutes() != null ? stopEval.getEstimatedArrivalMinutes() : 15 * (i + 1))))
                    .build();
            routeStopRepository.save(stop);
        }

        if (isFirstMile) {
            // Hub Terminal Stop
            RouteStop hubEnd = RouteStop.builder()
                    .route(savedRoute)
                    .location(hub)
                    .stopOrder(stopOrder++)
                    .stopType(StopType.DESTINATION)
                    .estimatedArrivalTime(OffsetDateTime.now().plusMinutes((long) tourResult.getFinalDurationMin()))
                    .build();
            routeStopRepository.save(hubEnd);
        }

        // Persist RouteEdge records for each tour segment
        int edgeOrder = 1;
        if (tourResult.getSegments() != null) {
            for (DirectedRoadSegment seg : tourResult.getSegments()) {
                Location sLoc = seg.getFromLocation() != null && seg.getFromLocation().getId() != null ?
                        locationRepository.findById(seg.getFromLocation().getId()).orElse(seg.getFromLocation()) : seg.getFromLocation();
                Location dLoc = seg.getToLocation() != null && seg.getToLocation().getId() != null ?
                        locationRepository.findById(seg.getToLocation().getId()).orElse(seg.getToLocation()) : seg.getToLocation();

                if (sLoc != null && dLoc != null) {
                    RouteEdge edge = RouteEdge.builder()
                            .route(savedRoute)
                            .edgeOrder(edgeOrder++)
                            .sourceLocation(sLoc)
                            .destinationLocation(dLoc)
                            .distance(seg.getDistanceKm())
                            .estimatedTime(seg.getDurationMin())
                            .geometryGeojson(seg.getGeometryGeoJson())
                            .build();
                    routeEdgeRepository.save(edge);
                }
            }
        }

        // 7. Apply assignments to all legs and record milestone events
        String driverName = driver.getUser() != null ? driver.getUser().getFullName() : "Driver #" + driver.getId();
        OffsetDateTime now = OffsetDateTime.now();

        for (DeliveryLeg leg : lockedLegs) {
            leg.setDriver(driver);
            leg.setVehicle(vehicle);
            leg.setRoute(savedRoute);
            leg.setStatus(LegStatus.ASSIGNED);
            leg.setAssignedAt(now);
            if (request.getNotes() != null) {
                leg.setNotes(request.getNotes());
            }
            deliveryLegRepository.save(leg);

            Delivery delivery = leg.getDelivery();
            if (isFirstMile) {
                delivery.setCurrentStage("PICKUP_ASSIGNED");
                delivery.setStatus(DeliveryStatus.ASSIGNED);
                delivery.setDriver(driver);
                delivery.setVehicle(vehicle);
                delivery.getPackage().setStatus(PackageStatus.IN_DELIVERY);
                packageRepository.save(delivery.getPackage());

                ShipmentEvent event = ShipmentEvent.builder()
                        .delivery(delivery)
                        .pkg(delivery.getPackage())
                        .stage("PICKUP_ASSIGNED")
                        .title("Batch Pickup Assigned")
                        .description(String.format("Assigned to batch pickup route (Stop %d) with driver %s (%s).",
                                orderedStops.indexOf(orderedStops.stream().filter(s -> s.getLegId().equals(leg.getId())).findFirst().orElse(null)) + 1,
                                driverName, vehicle.getRegistrationNumber()))
                        .locationName(leg.getSourceLocation().getAddress())
                        .hub(hub)
                        .driver(driver)
                        .driverName(driverName)
                        .actorUsername(currentUsername)
                        .eventTimestamp(now)
                        .build();
                shipmentEventRepository.save(event);
            } else {
                delivery.setCurrentStage("OUT_FOR_DELIVERY");
                delivery.setStatus(DeliveryStatus.IN_PROGRESS);
                delivery.setDriver(driver);
                delivery.setVehicle(vehicle);

                ShipmentEvent event = ShipmentEvent.builder()
                        .delivery(delivery)
                        .pkg(delivery.getPackage())
                        .stage("OUT_FOR_DELIVERY")
                        .title("Out for Delivery (Batch Route)")
                        .description(String.format("Dispatched on batch final-mile route with driver %s (%s).",
                                driverName, vehicle.getRegistrationNumber()))
                        .locationName(hub.getName())
                        .hub(hub)
                        .driver(driver)
                        .driverName(driverName)
                        .actorUsername(currentUsername)
                        .eventTimestamp(now)
                        .build();
                shipmentEventRepository.save(event);
            }
            deliveryRepository.save(delivery);
        }

        // 8. Lock driver and vehicle in use
        driver.setStatus(DriverStatus.ON_DELIVERY);
        vehicle.setStatus(VehicleStatus.IN_USE);
        driverRepository.save(driver);
        vehicleRepository.save(vehicle);

        return BatchDispatchAssignmentResponse.builder()
                .routeId(savedRoute.getId())
                .batchType(isFirstMile ? "FIRST_MILE" : "FINAL_MILE")
                .hubId(hub.getId())
                .hubName(hub.getName())
                .driverId(driver.getId())
                .driverName(driverName)
                .vehicleId(vehicle.getId())
                .vehicleRegistrationNumber(vehicle.getRegistrationNumber())
                .vehicleCapacityKg(vehicle.getCapacity())
                .assignedLegIds(request.getLegIds())
                .assignedDeliveryIds(assignedDeliveryIds)
                .totalPackages(lockedLegs.size())
                .totalWeightKg(totalBatchWeight)
                .remainingCapacityKg(vehicle.getCapacity() != null ? Math.max(0, vehicle.getCapacity() - totalBatchWeight) : null)
                .totalDistanceKm(tourResult.getFinalDistanceKm())
                .estimatedDurationMin(tourResult.getFinalDurationMin())
                .dispatchedAt(now)
                .status("DISPATCHED")
                .message(String.format("Successfully dispatched batch of %d packages to driver %s with vehicle %s.",
                        lockedLegs.size(), driverName, vehicle.getRegistrationNumber()))
                .build();
    }

    /**
     * Phase 7: Driver Manifest View
     */
    @Transactional(readOnly = true)
    public DriverManifestResponse getDriverManifest(String currentUsername) {
        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUsername));

        Driver driver = driverRepository.findByUserId(user.getId())
                .orElse(null);

        // If not directly linked by driver.user_id, find if user is an ADMIN inspecting driver manifest
        if (driver == null && user.getRole() == Role.ADMIN) {
            // Get any active driver on delivery
            driver = driverRepository.findAll().stream()
                    .filter(d -> d.getStatus() == DriverStatus.ON_DELIVERY)
                    .findFirst().orElse(null);
        }

        if (driver == null) {
            return DriverManifestResponse.builder()
                    .driverName(user.getFullName())
                    .stops(Collections.emptyList())
                    .totalStopsCount(0)
                    .completedStopsCount(0)
                    .pendingStopsCount(0)
                    .build();
        }

        List<DeliveryLeg> activeLegs = deliveryLegRepository.findByDriverIdAndActiveTrue(driver.getId());
        List<DeliveryLeg> currentTripLegs = activeLegs.stream()
                .filter(l -> l.getStatus() == LegStatus.ASSIGNED || l.getStatus() == LegStatus.IN_TRANSIT ||
                        (l.getStatus() == LegStatus.COMPLETED && l.getCompletedAt() != null &&
                                l.getCompletedAt().isAfter(OffsetDateTime.now().minusHours(4))))
                .toList();

        if (currentTripLegs.isEmpty()) {
            return DriverManifestResponse.builder()
                    .driverId(driver.getId())
                    .driverName(driver.getUser() != null ? driver.getUser().getFullName() : "Driver #" + driver.getId())
                    .stops(Collections.emptyList())
                    .totalStopsCount(0)
                    .completedStopsCount(0)
                    .pendingStopsCount(0)
                    .build();
        }

        Vehicle vehicle = currentTripLegs.get(0).getVehicle();
        Long routeId = currentTripLegs.get(0).getRoute() != null ? currentTripLegs.get(0).getRoute().getId() : null;

        List<ManifestStop> stops = new ArrayList<>();
        int seq = 1;
        ManifestStop activeStop = null;
        double currentLoad = 0.0;
        int completedCount = 0;

        for (DeliveryLeg leg : currentTripLegs) {
            boolean isFirstMile = leg.getLegType() == LegType.FIRST_MILE;
            Location loc = isFirstMile ? leg.getSourceLocation() : leg.getDestinationLocation();
            Delivery del = leg.getDelivery();
            Package pkg = del != null ? del.getPackage() : null;

            double weight = (pkg != null && pkg.getWeight() != null) ? pkg.getWeight() : 1.0;
            boolean isCompleted = leg.getStatus() == LegStatus.COMPLETED;
            if (!isCompleted) {
                currentLoad += weight;
            } else {
                completedCount++;
            }

            ManifestStop stop = ManifestStop.builder()
                    .sequenceNumber(seq++)
                    .legId(leg.getId())
                    .deliveryId(del != null ? del.getId() : null)
                    .trackingNumber(pkg != null ? pkg.getTrackingNumber() : (del != null ? "DEL-" + del.getId() : null))
                    .stopType(isFirstMile ? "PICKUP" : "DESTINATION_DELIVERY")
                    .legType(leg.getLegType().name())
                    .locationId(loc.getId())
                    .locationName(loc.getName())
                    .address(loc.getAddress())
                    .latitude(loc.getLatitude())
                    .longitude(loc.getLongitude())
                    .contactPerson(isFirstMile ? (pkg != null ? pkg.getSenderName() : "Sender") : (pkg != null ? pkg.getReceiverName() : "Recipient"))
                    .contactPhone(isFirstMile ? (pkg != null ? pkg.getSenderPhone() : null) : (pkg != null ? pkg.getReceiverPhone() : null))
                    .packageWeightKg(weight)
                    .itemName(pkg != null && pkg.getItemName() != null ? pkg.getItemName() : "Parcel")
                    .instructions(del != null ? del.getNotes() : null)
                    .legStatus(leg.getStatus().name())
                    .isCompleted(isCompleted)
                    .estimatedArrivalTime(leg.getAssignedAt() != null ? leg.getAssignedAt().plusMinutes(15L * seq) : null)
                    .completedAt(leg.getCompletedAt())
                    .build();

            if (!isCompleted && activeStop == null) {
                stop.setCurrentStop(true);
                activeStop = stop;
            }

            stops.add(stop);
        }

        return DriverManifestResponse.builder()
                .routeId(routeId)
                .driverId(driver.getId())
                .driverName(driver.getUser() != null ? driver.getUser().getFullName() : "Driver #" + driver.getId())
                .vehicleId(vehicle != null ? vehicle.getId() : null)
                .vehicleRegistrationNumber(vehicle != null ? vehicle.getRegistrationNumber() : null)
                .vehicleCapacityKg(vehicle != null ? vehicle.getCapacity() : null)
                .currentLoadKg(currentLoad)
                .remainingCapacityKg(vehicle != null && vehicle.getCapacity() != null ?
                        Math.max(0, vehicle.getCapacity() - currentLoad) : null)
                .batchType(currentTripLegs.get(0).getLegType().name())
                .totalStopsCount(stops.size())
                .completedStopsCount(completedCount)
                .pendingStopsCount(stops.size() - completedCount)
                .currentActiveStop(activeStop)
                .stops(stops)
                .build();
    }

    /**
     * Directed Road Segment Model representing a real road path between two locations
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectedRoadSegment {
        private Location fromLocation;
        private Location toLocation;
        private double distanceKm;
        private double durationMin;
        @Builder.Default
        private List<List<Double>> coordinates = new ArrayList<>();
        private String geometryGeoJson;
        private String source;
        @Builder.Default
        private List<Location> intermediateLocations = new ArrayList<>();
        @Builder.Default
        private List<LocationEdge> graphEdges = new ArrayList<>();
    }

    /**
     * Multi-Stop Tour Optimization Result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MultiStopTourResult {
        @Builder.Default
        private List<CandidateStopEvaluation> orderedStops = new ArrayList<>();
        private double initialDistanceKm;
        private double finalDistanceKm;
        private double initialDurationMin;
        private double finalDurationMin;
        private String fullGeoJson;
        private String geometrySource;
        @Builder.Default
        private List<DirectedRoadSegment> segments = new ArrayList<>();
    }

    private Location toLocation(CandidateStopEvaluation eval) {
        if (eval == null) return null;
        return Location.builder()
                .id(eval.getLocationId())
                .name(eval.getLocationName() != null ? eval.getLocationName() : "Stop Location")
                .latitude(eval.getLatitude())
                .longitude(eval.getLongitude())
                .address(eval.getAddress())
                .build();
    }

    private DirectedRoadSegment getDirectedRoadSegment(CandidateStopEvaluation from, CandidateStopEvaluation to, Map<String, DirectedRoadSegment> cache) {
        return getDirectedRoadSegment(toLocation(from), toLocation(to), cache);
    }

    private DirectedRoadSegment getDirectedRoadSegment(CandidateStopEvaluation from, Location to, Map<String, DirectedRoadSegment> cache) {
        return getDirectedRoadSegment(toLocation(from), to, cache);
    }

    private DirectedRoadSegment getDirectedRoadSegment(Location from, CandidateStopEvaluation to, Map<String, DirectedRoadSegment> cache) {
        return getDirectedRoadSegment(from, toLocation(to), cache);
    }

    /**
     * Calculates directed road segment using Dijkstra for graph locations, falling back to OSRM road routing.
     * Respects directed edges: cost(A -> B) != cost(B -> A).
     */
    public DirectedRoadSegment getDirectedRoadSegment(Location fromLoc, Location toLoc, Map<String, DirectedRoadSegment> cache) {
        if (fromLoc == null || toLoc == null) {
            return DirectedRoadSegment.builder().distanceKm(0.0).durationMin(0.0).source("NONE").build();
        }
        if (fromLoc.getId() != null && toLoc.getId() != null && fromLoc.getId().equals(toLoc.getId())) {
            return DirectedRoadSegment.builder()
                    .fromLocation(fromLoc)
                    .toLocation(toLoc)
                    .distanceKm(0.0)
                    .durationMin(0.0)
                    .source("SAME_LOCATION")
                    .build();
        }

        String key = (fromLoc.getId() != null ? "ID:" + fromLoc.getId() : String.format(Locale.US, "%.5f,%.5f", fromLoc.getLatitude(), fromLoc.getLongitude()))
                + "->" +
                (toLoc.getId() != null ? "ID:" + toLoc.getId() : String.format(Locale.US, "%.5f,%.5f", toLoc.getLatitude(), toLoc.getLongitude()));

        if (cache != null && cache.containsKey(key)) {
            return cache.get(key);
        }

        DirectedRoadSegment segment = null;

        // 1. Try Dijkstra shortest directed path if both vertices exist in graph
        if (fromLoc.getId() != null && toLoc.getId() != null) {
            try {
                OptimizationResult dijResult = dijkstraStrategy.optimize(com.routeoptimizer.dto.OptimizationRequest.builder()
                        .startLocationId(fromLoc.getId())
                        .destinationLocationId(toLoc.getId())
                        .criterion("SHORTEST_DISTANCE")
                        .build());

                if (dijResult != null && dijResult.isPathExists()) {
                    List<List<Double>> coords = new ArrayList<>();
                    if (dijResult.getOrderedEdges() != null && !dijResult.getOrderedEdges().isEmpty()) {
                        for (LocationEdge edge : dijResult.getOrderedEdges()) {
                            if (edge.getGeometryGeojson() != null && !edge.getGeometryGeojson().isBlank()) {
                                extractCoordinates(edge.getGeometryGeojson(), coords);
                            } else {
                                coords.add(List.of(edge.getSourceLocation().getLongitude(), edge.getSourceLocation().getLatitude()));
                                coords.add(List.of(edge.getDestinationLocation().getLongitude(), edge.getDestinationLocation().getLatitude()));
                            }
                        }
                    }
                    if (coords.isEmpty() && dijResult.getOrderedLocations() != null) {
                        for (Location loc : dijResult.getOrderedLocations()) {
                            coords.add(List.of(loc.getLongitude(), loc.getLatitude()));
                        }
                    }

                    segment = DirectedRoadSegment.builder()
                            .fromLocation(fromLoc)
                            .toLocation(toLoc)
                            .distanceKm(Math.round(dijResult.getTotalDistance() * 10.0) / 10.0)
                            .durationMin(Math.round(dijResult.getTotalEstimatedTime() * 10.0) / 10.0)
                            .coordinates(coords)
                            .geometryGeoJson(buildLineStringJson(coords))
                            .source("Dijkstra Directed Graph")
                            .intermediateLocations(dijResult.getOrderedLocations())
                            .graphEdges(dijResult.getOrderedEdges())
                            .build();
                }
            } catch (Exception ignored) {
                // Not reachable in location_edges graph; proceed to OSRM road routing
            }
        }

        // 2. Query OSRM road routing engine
        if (segment == null && fromLoc.getLatitude() != null && fromLoc.getLongitude() != null &&
                toLoc.getLatitude() != null && toLoc.getLongitude() != null) {
            try {
                RoutingResponse osrm = routingService.calculateRoute(
                        fromLoc.getLatitude(), fromLoc.getLongitude(),
                        toLoc.getLatitude(), toLoc.getLongitude());

                List<List<Double>> coords = new ArrayList<>();
                if (osrm.getGeometryGeoJson() != null) {
                    extractCoordinates(osrm.getGeometryGeoJson(), coords);
                }
                if (coords.isEmpty()) {
                    coords.add(List.of(fromLoc.getLongitude(), fromLoc.getLatitude()));
                    coords.add(List.of(toLoc.getLongitude(), toLoc.getLatitude()));
                }

                segment = DirectedRoadSegment.builder()
                        .fromLocation(fromLoc)
                        .toLocation(toLoc)
                        .distanceKm(osrm.getDistanceKm())
                        .durationMin(osrm.getDurationMin())
                        .coordinates(coords)
                        .geometryGeoJson(osrm.getGeometryGeoJson() != null ? osrm.getGeometryGeoJson() : buildLineStringJson(coords))
                        .source(osrm.getProvider() != null ? osrm.getProvider() : "OSRM Real Road Network (Bengaluru)")
                        .build();
            } catch (Exception ex) {
                // 3. Fallback deterministic circuity road approximation
                double haversine = calculateHaversineKm(fromLoc.getLatitude(), fromLoc.getLongitude(),
                        toLoc.getLatitude(), toLoc.getLongitude());
                double dist = Math.round(haversine * 1.3 * 10.0) / 10.0;
                double dur = Math.round(dist * 2.5 * 10.0) / 10.0;
                List<List<Double>> coords = List.of(
                        List.of(fromLoc.getLongitude(), fromLoc.getLatitude()),
                        List.of(toLoc.getLongitude(), toLoc.getLatitude())
                );
                segment = DirectedRoadSegment.builder()
                        .fromLocation(fromLoc)
                        .toLocation(toLoc)
                        .distanceKm(dist)
                        .durationMin(dur)
                        .coordinates(coords)
                        .geometryGeoJson(buildLineStringJson(coords))
                        .source("Road Network Approximation (Deterministic Circuity 1.3x)")
                        .build();
            }
        }

        if (segment == null) {
            segment = DirectedRoadSegment.builder()
                    .fromLocation(fromLoc)
                    .toLocation(toLoc)
                    .distanceKm(0.0)
                    .durationMin(0.0)
                    .source("UNRESOLVED")
                    .build();
        }

        if (cache != null) {
            cache.put(key, segment);
        }
        return segment;
    }

    /**
     * Multi-Stop Directed Tour Optimizer:
     * 1. Constructs Nearest Neighbor tour respecting directed road costs.
     * 2. Evaluates candidate tour reversals using directed 2-Opt local search.
     * 3. Guarantees that finalTourDistance <= initialTourDistance.
     * 4. Preserves hub-return constraints and stitches real road geometry.
     */
    public MultiStopTourResult optimizeDirectedBatchTour(List<CandidateStopEvaluation> stops, Location hub, boolean isFirstMile) {
        if (stops == null || stops.isEmpty()) {
            return MultiStopTourResult.builder()
                    .orderedStops(Collections.emptyList())
                    .initialDistanceKm(0.0)
                    .finalDistanceKm(0.0)
                    .initialDurationMin(0.0)
                    .finalDurationMin(0.0)
                    .fullGeoJson(null)
                    .geometrySource("None")
                    .segments(Collections.emptyList())
                    .build();
        }

        Map<String, DirectedRoadSegment> cache = new HashMap<>();

        // Single Stop Case
        if (stops.size() == 1) {
            CandidateStopEvaluation single = stops.get(0);
            single.setProposedStopOrder(1);

            DirectedRoadSegment seg;
            if (isFirstMile) {
                // Pickup: single stop -> finish at hub
                seg = getDirectedRoadSegment(single, hub, cache);
                single.setCumulativeDistanceKm(0.0);
                single.setEstimatedArrivalMinutes(0.0);
            } else {
                // Delivery: start at hub -> single stop
                seg = getDirectedRoadSegment(hub, single, cache);
                single.setCumulativeDistanceKm(seg.getDistanceKm());
                single.setEstimatedArrivalMinutes(Math.round((seg.getDurationMin() + 5.0) * 10.0) / 10.0);
            }

            double totalDist = seg.getDistanceKm();
            double totalDur = Math.round((seg.getDurationMin() + 5.0) * 10.0) / 10.0;

            return MultiStopTourResult.builder()
                    .orderedStops(List.of(single))
                    .initialDistanceKm(totalDist)
                    .finalDistanceKm(totalDist)
                    .initialDurationMin(totalDur)
                    .finalDurationMin(totalDur)
                    .fullGeoJson(seg.getGeometryGeoJson())
                    .geometrySource(seg.getSource())
                    .segments(List.of(seg))
                    .build();
        }

        // -------------------------------------------------------------
        // Stage A: Nearest Neighbor Initial Tour Construction
        // -------------------------------------------------------------
        List<CandidateStopEvaluation> tour = new ArrayList<>();
        Set<CandidateStopEvaluation> unvisited = new HashSet<>(stops);

        CandidateStopEvaluation current = null;
        if (isFirstMile) {
            // Find stop farthest from hub to anchor the inward pickup corridor
            double maxDist = -1.0;
            for (CandidateStopEvaluation s : unvisited) {
                DirectedRoadSegment seg = getDirectedRoadSegment(s, hub, cache);
                if (seg.getDistanceKm() > maxDist) {
                    maxDist = seg.getDistanceKm();
                    current = s;
                }
            }
        } else {
            // Find stop closest to hub to commence outward delivery
            double minDist = Double.MAX_VALUE;
            for (CandidateStopEvaluation s : unvisited) {
                DirectedRoadSegment seg = getDirectedRoadSegment(hub, s, cache);
                if (seg.getDistanceKm() < minDist) {
                    minDist = seg.getDistanceKm();
                    current = s;
                }
            }
        }

        if (current == null) {
            current = stops.get(0);
        }
        tour.add(current);
        unvisited.remove(current);

        while (!unvisited.isEmpty()) {
            CandidateStopEvaluation bestNext = null;
            double bestCost = Double.MAX_VALUE;

            // Sort remaining stops by ID for deterministic tie-breaking
            List<CandidateStopEvaluation> candidates = new ArrayList<>(unvisited);
            candidates.sort(Comparator.comparingLong(CandidateStopEvaluation::getLegId));

            for (CandidateStopEvaluation cand : candidates) {
                DirectedRoadSegment seg = getDirectedRoadSegment(current, cand, cache);
                if (seg.getDistanceKm() < bestCost - 1e-6) {
                    bestCost = seg.getDistanceKm();
                    bestNext = cand;
                }
            }

            if (bestNext == null) {
                bestNext = candidates.get(0);
            }
            tour.add(bestNext);
            unvisited.remove(bestNext);
            current = bestNext;
        }

        List<CandidateStopEvaluation> initialTour = new ArrayList<>(tour);
        double initialDistance = evaluateTourDistance(initialTour, hub, isFirstMile, cache);
        double initialDuration = evaluateTourDuration(initialTour, hub, isFirstMile, cache);

        // -------------------------------------------------------------
        // Stage B: Directed 2-Opt Local Search
        // -------------------------------------------------------------
        boolean improved = true;
        int pass = 0;
        int maxPasses = 50;
        double currentDistance = initialDistance;

        while (improved && pass < maxPasses && tour.size() > 2) {
            improved = false;
            pass++;

            for (int i = 0; i < tour.size() - 1; i++) {
                for (int j = i + 1; j < tour.size(); j++) {
                    List<CandidateStopEvaluation> candidateTour = reverseSubsegment(tour, i, j);
                    double candidateDistance = evaluateTourDistance(candidateTour, hub, isFirstMile, cache);

                    if (candidateDistance < currentDistance - 1e-6) {
                        tour = candidateTour;
                        currentDistance = candidateDistance;
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
        }

        // Safety Invariant: Guarantee 2-Opt never increases objective relative to initial tour
        if (currentDistance > initialDistance) {
            tour = initialTour;
            currentDistance = initialDistance;
        }

        // -------------------------------------------------------------
        // Stage C: Reconstruct Road Segments, Cumulative ETAs, and GeoJSON
        // -------------------------------------------------------------
        List<DirectedRoadSegment> segments = new ArrayList<>();
        List<List<Double>> allCoordinates = new ArrayList<>();
        Set<String> sources = new LinkedHashSet<>();

        double cumulativeKm = 0.0;
        double cumulativeMin = 0.0;

        if (isFirstMile) {
            // First stop has 0 cumulative distance (pickup starting point)
            tour.get(0).setProposedStopOrder(1);
            tour.get(0).setCumulativeDistanceKm(0.0);
            tour.get(0).setEstimatedArrivalMinutes(0.0);

            for (int i = 1; i < tour.size(); i++) {
                DirectedRoadSegment seg = getDirectedRoadSegment(tour.get(i - 1), tour.get(i), cache);
                cumulativeKm += seg.getDistanceKm();
                cumulativeMin += seg.getDurationMin() + 5.0; // travel + 5 min dwell
                tour.get(i).setProposedStopOrder(i + 1);
                tour.get(i).setCumulativeDistanceKm(Math.round(cumulativeKm * 10.0) / 10.0);
                tour.get(i).setEstimatedArrivalMinutes(Math.round(cumulativeMin * 10.0) / 10.0);
                segments.add(seg);
                if (seg.getCoordinates() != null) allCoordinates.addAll(seg.getCoordinates());
                if (seg.getSource() != null) sources.add(seg.getSource());
            }

            // Terminal leg: last pickup stop -> Hub
            DirectedRoadSegment hubSeg = getDirectedRoadSegment(tour.get(tour.size() - 1), hub, cache);
            cumulativeKm += hubSeg.getDistanceKm();
            cumulativeMin += hubSeg.getDurationMin();
            segments.add(hubSeg);
            if (hubSeg.getCoordinates() != null) allCoordinates.addAll(hubSeg.getCoordinates());
            if (hubSeg.getSource() != null) sources.add(hubSeg.getSource());

        } else {
            // Final-Mile: Hub -> Destination 0
            DirectedRoadSegment hubStartSeg = getDirectedRoadSegment(hub, tour.get(0), cache);
            cumulativeKm += hubStartSeg.getDistanceKm();
            cumulativeMin += hubStartSeg.getDurationMin() + 5.0;
            tour.get(0).setProposedStopOrder(1);
            tour.get(0).setCumulativeDistanceKm(Math.round(cumulativeKm * 10.0) / 10.0);
            tour.get(0).setEstimatedArrivalMinutes(Math.round(cumulativeMin * 10.0) / 10.0);
            segments.add(hubStartSeg);
            if (hubStartSeg.getCoordinates() != null) allCoordinates.addAll(hubStartSeg.getCoordinates());
            if (hubStartSeg.getSource() != null) sources.add(hubStartSeg.getSource());

            for (int i = 1; i < tour.size(); i++) {
                DirectedRoadSegment seg = getDirectedRoadSegment(tour.get(i - 1), tour.get(i), cache);
                cumulativeKm += seg.getDistanceKm();
                cumulativeMin += seg.getDurationMin() + 5.0;
                tour.get(i).setProposedStopOrder(i + 1);
                tour.get(i).setCumulativeDistanceKm(Math.round(cumulativeKm * 10.0) / 10.0);
                tour.get(i).setEstimatedArrivalMinutes(Math.round(cumulativeMin * 10.0) / 10.0);
                segments.add(seg);
                if (seg.getCoordinates() != null) allCoordinates.addAll(seg.getCoordinates());
                if (seg.getSource() != null) sources.add(seg.getSource());
            }
        }

        double finalDistance = Math.round(cumulativeKm * 10.0) / 10.0;
        double finalDuration = Math.round(cumulativeMin * 10.0) / 10.0;
        String fullGeoJson = buildLineStringJson(allCoordinates);
        String geometrySource = sources.isEmpty() ? "OSRM Real Road Network (Bengaluru)" : String.join(", ", sources);

        return MultiStopTourResult.builder()
                .orderedStops(tour)
                .initialDistanceKm(Math.round(initialDistance * 10.0) / 10.0)
                .finalDistanceKm(finalDistance)
                .initialDurationMin(Math.round(initialDuration * 10.0) / 10.0)
                .finalDurationMin(finalDuration)
                .fullGeoJson(fullGeoJson)
                .geometrySource(geometrySource)
                .segments(segments)
                .build();
    }

    private double evaluateTourDistance(List<CandidateStopEvaluation> tour, Location hub, boolean isFirstMile, Map<String, DirectedRoadSegment> cache) {
        if (tour == null || tour.isEmpty()) return 0.0;
        double dist = 0.0;

        if (isFirstMile) {
            for (int i = 0; i < tour.size() - 1; i++) {
                dist += getDirectedRoadSegment(tour.get(i), tour.get(i + 1), cache).getDistanceKm();
            }
            dist += getDirectedRoadSegment(tour.get(tour.size() - 1), hub, cache).getDistanceKm();
        } else {
            dist += getDirectedRoadSegment(hub, tour.get(0), cache).getDistanceKm();
            for (int i = 0; i < tour.size() - 1; i++) {
                dist += getDirectedRoadSegment(tour.get(i), tour.get(i + 1), cache).getDistanceKm();
            }
        }
        return dist;
    }

    private double evaluateTourDuration(List<CandidateStopEvaluation> tour, Location hub, boolean isFirstMile, Map<String, DirectedRoadSegment> cache) {
        if (tour == null || tour.isEmpty()) return 0.0;
        double dur = 0.0;

        if (isFirstMile) {
            for (int i = 0; i < tour.size() - 1; i++) {
                dur += getDirectedRoadSegment(tour.get(i), tour.get(i + 1), cache).getDurationMin() + 5.0;
            }
            dur += getDirectedRoadSegment(tour.get(tour.size() - 1), hub, cache).getDurationMin();
        } else {
            dur += getDirectedRoadSegment(hub, tour.get(0), cache).getDurationMin() + 5.0;
            for (int i = 0; i < tour.size() - 1; i++) {
                dur += getDirectedRoadSegment(tour.get(i), tour.get(i + 1), cache).getDurationMin() + 5.0;
            }
        }
        return dur;
    }

    private List<CandidateStopEvaluation> reverseSubsegment(List<CandidateStopEvaluation> tour, int i, int j) {
        List<CandidateStopEvaluation> res = new ArrayList<>(tour);
        while (i < j) {
            CandidateStopEvaluation temp = res.get(i);
            res.set(i, res.get(j));
            res.set(j, temp);
            i++;
            j--;
        }
        return res;
    }

    private void extractCoordinates(String geoJson, List<List<Double>> target) {
        if (geoJson == null || geoJson.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            JsonNode coords = root.get("coordinates");
            if (coords != null && coords.isArray()) {
                for (JsonNode pt : coords) {
                    if (pt.isArray() && pt.size() >= 2) {
                        target.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String buildLineStringJson(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) return null;
        try {
            Map<String, Object> feature = Map.of(
                    "type", "LineString",
                    "coordinates", coordinates
            );
            return objectMapper.writeValueAsString(feature);
        } catch (Exception ex) {
            return null;
        }
    }
}
