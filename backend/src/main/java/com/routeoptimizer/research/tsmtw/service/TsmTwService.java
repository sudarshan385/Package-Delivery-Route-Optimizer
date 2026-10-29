package com.routeoptimizer.research.tsmtw.service;

import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TsmTwService {

    private final LocationEdgeRepository locationEdgeRepository;

    public TSMTWResult generateCandidates(TSMTWRequest request) {
        if (request == null || request.getTasks() == null || request.getTasks().isEmpty()) {
            return TSMTWResult.builder()
                    .feasible(false)
                    .rejectionReason("Empty task list")
                    .candidateRoutes(Collections.emptyList())
                    .build();
        }

        // Validate basic constraints first: matching pickup/delivery
        Map<Long, Integer> pickupCount = new HashMap<>();
        Map<Long, Integer> deliveryCount = new HashMap<>();

        for (TSMTWTask task : request.getTasks()) {
            if (task.getDeliveryId() == null) {
                return TSMTWResult.builder().feasible(false).rejectionReason("Task missing delivery ID").candidateRoutes(Collections.emptyList()).build();
            }
            if (task.getTaskType() == ResearchTaskType.PICKUP) {
                pickupCount.put(task.getDeliveryId(), pickupCount.getOrDefault(task.getDeliveryId(), 0) + 1);
            } else if (task.getTaskType() == ResearchTaskType.DELIVERY) {
                deliveryCount.put(task.getDeliveryId(), deliveryCount.getOrDefault(task.getDeliveryId(), 0) + 1);
            }
        }

        for (Long deliveryId : pickupCount.keySet()) {
            if (!deliveryCount.containsKey(deliveryId) || deliveryCount.get(deliveryId) == 0) {
                return TSMTWResult.builder().feasible(false).rejectionReason("Missing delivery for deliveryId: " + deliveryId).candidateRoutes(Collections.emptyList()).build();
            }
        }

        for (Long deliveryId : deliveryCount.keySet()) {
            if (!pickupCount.containsKey(deliveryId) || pickupCount.get(deliveryId) == 0) {
                return TSMTWResult.builder().feasible(false).rejectionReason("Missing pickup for deliveryId: " + deliveryId).candidateRoutes(Collections.emptyList()).build();
            }
        }

        // Generate permutations of tasks (simple factorial permutation for candidate generation)
        List<List<TSMTWTask>> permutations = generatePermutations(request.getTasks());
        
        List<CandidateRoute> candidates = new ArrayList<>();
        
        for (List<TSMTWTask> perm : permutations) {
            CandidateRoute route = evaluateCandidate(request.getStartLocationId(), request.getStartTime(), perm);
            if (route != null && route.isFeasible()) {
                candidates.add(route);
            }
        }

        // Sort permutations deterministically based on distance
        candidates.sort(Comparator.comparing(CandidateRoute::getTotalDistance, Comparator.nullsLast(Double::compareTo)));
        
        if (candidates.isEmpty()) {
            return TSMTWResult.builder().feasible(false).rejectionReason("No feasible routes found").candidateRoutes(Collections.emptyList()).build();
        }

        return TSMTWResult.builder()
                .feasible(true)
                .candidateRoutes(candidates)
                .build();
    }

    private CandidateRoute evaluateCandidate(Long startLocationId, OffsetDateTime startTime, List<TSMTWTask> tasks) {
        Set<Long> pickedUp = new HashSet<>();
        
        double totalDistance = 0.0;
        double totalEstimatedTime = 0.0;
        
        Long currentLocationId = startLocationId;
        OffsetDateTime currentTime = startTime != null ? startTime : OffsetDateTime.now();

        for (TSMTWTask task : tasks) {
            // 1. Pickup-before-delivery precedence
            if (task.getTaskType() == ResearchTaskType.DELIVERY) {
                if (!pickedUp.contains(task.getDeliveryId())) {
                    return CandidateRoute.builder().orderedTasks(tasks).feasible(false).rejectionReason("Delivery before pickup for deliveryId: " + task.getDeliveryId()).build();
                }
            } else if (task.getTaskType() == ResearchTaskType.PICKUP) {
                pickedUp.add(task.getDeliveryId());
            }

            // 2. Travel time/distance calculation
            if (currentLocationId != null && !currentLocationId.equals(task.getLocationId())) {
                LocationEdge edge = locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(currentLocationId, task.getLocationId()).orElse(null);
                if (edge == null) {
                    return CandidateRoute.builder().orderedTasks(tasks).feasible(false).rejectionReason("No route edge from " + currentLocationId + " to " + task.getLocationId()).build();
                }
                totalDistance += edge.getDistance() != null ? edge.getDistance() : 0.0;
                double edgeTime = edge.getEstimatedTime() != null ? edge.getEstimatedTime() : 0.0;
                totalEstimatedTime += edgeTime;
                currentTime = currentTime.plusSeconds((long) (edgeTime * 60)); // assuming estimatedTime is in minutes
            }

            // 3. Time Windows
            if (task.getEarliestTime() != null) {
                if (currentTime.isBefore(task.getEarliestTime())) {
                    // Waiting is allowed
                    Duration wait = Duration.between(currentTime, task.getEarliestTime());
                    currentTime = task.getEarliestTime();
                    totalEstimatedTime += wait.toMinutes();
                }
            }
            if (task.getLatestTime() != null) {
                if (currentTime.isAfter(task.getLatestTime())) {
                    return CandidateRoute.builder().orderedTasks(tasks).feasible(false).rejectionReason("Arrival after latest time for task: " + task.getId()).build();
                }
            }
            
            // "complete" task, update current location
            currentLocationId = task.getLocationId();
        }

        return CandidateRoute.builder()
                .orderedTasks(tasks)
                .feasible(true)
                .totalDistance(totalDistance)
                .estimatedTime(totalEstimatedTime)
                .build();
    }

    private List<List<TSMTWTask>> generatePermutations(List<TSMTWTask> original) {
        List<List<TSMTWTask>> result = new ArrayList<>();
        permute(original, 0, result);
        // Sort to make it deterministic
        result.sort((l1, l2) -> {
            for (int i = 0; i < l1.size(); i++) {
                int cmp = l1.get(i).getId().compareTo(l2.get(i).getId());
                if (cmp != 0) return cmp;
            }
            return 0;
        });
        return result;
    }

    private void permute(List<TSMTWTask> arr, int k, List<List<TSMTWTask>> result) {
        if (k == arr.size()) {
            result.add(new ArrayList<>(arr));
            return;
        }
        for (int i = k; i < arr.size(); i++) {
            Collections.swap(arr, i, k);
            permute(arr, k + 1, result);
            Collections.swap(arr, k, i);
        }
    }
}
