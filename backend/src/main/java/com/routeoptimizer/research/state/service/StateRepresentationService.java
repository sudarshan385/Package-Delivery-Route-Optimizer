package com.routeoptimizer.research.state.service;

import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.RouteDecisionState.StateTask;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StateRepresentationService {

    /**
     * Constructs the immutable RL State representation based on the current routing context.
     * This deterministic method avoids random values or side effects.
     */
    public RouteDecisionState buildState(
            Long driverId,
            Long currentLocationId,
            OffsetDateTime currentTime,
            List<TSMTWTask> remainingTasks,
            Set<Long> completedTaskIds,
            Set<Long> pickedUpDeliveryIds,
            List<CandidateRoute> candidateRoutes,
            BehaviorRepresentation behaviorRepresentation) {

        List<StateTask> stateTasks = remainingTasks.stream().map(task ->
                StateTask.builder()
                        .taskId(task.getId())
                        .locationId(task.getLocationId())
                        .deliveryId(task.getDeliveryId())
                        .taskType(task.getTaskType().name())
                        .earliestTime(task.getEarliestTime())
                        .latestTime(task.getLatestTime())
                        .build()
        ).collect(Collectors.toList());

        ValidActionMask actionMask = generateValidActionMask(remainingTasks, completedTaskIds, pickedUpDeliveryIds, candidateRoutes);

        return RouteDecisionState.builder()
                .driverId(driverId)
                .currentLocationId(currentLocationId)
                .currentTime(currentTime)
                .remainingTasks(stateTasks)
                .completedTaskIds(completedTaskIds)
                .pickedUpDeliveryIds(pickedUpDeliveryIds)
                .candidateRoutes(candidateRoutes)
                .behaviorRepresentation(behaviorRepresentation)
                .validActionMask(actionMask)
                .build();
    }

    private ValidActionMask generateValidActionMask(
            List<TSMTWTask> remainingTasks,
            Set<Long> completedTaskIds,
            Set<Long> pickedUpDeliveryIds,
            List<CandidateRoute> candidateRoutes) {

        Map<Long, Boolean> validNextTasks = new HashMap<>();
        Map<Long, Boolean> validCandidateRoutes = new HashMap<>();
        Map<Long, String> invalidationReasons = new HashMap<>();

        if (remainingTasks != null) {
            for (TSMTWTask task : remainingTasks) {
                Long taskId = task.getId();

                if (completedTaskIds != null && completedTaskIds.contains(taskId)) {
                    validNextTasks.put(taskId, false);
                    invalidationReasons.put(taskId, "Task is already completed");
                    continue;
                }

                if (task.getTaskType() == ResearchTaskType.DELIVERY) {
                    // A delivery is only valid if its corresponding pickup has been completed.
                    // For delivery-only datasets (like Amazon), deliveryId will be null, so we skip the check.
                    if (task.getDeliveryId() != null && (pickedUpDeliveryIds == null || !pickedUpDeliveryIds.contains(task.getDeliveryId()))) {
                        validNextTasks.put(taskId, false);
                        invalidationReasons.put(taskId, "Delivery cannot be selected before its pickup is completed");
                        continue;
                    }
                }

                validNextTasks.put(taskId, true);
            }
        }

        if (candidateRoutes != null) {
            long routeIdCounter = 1L;
            for (CandidateRoute route : candidateRoutes) {
                // If candidate route from TSM-TW is explicitly flagged as infeasible, mask it out
                if (!route.isFeasible()) {
                    validCandidateRoutes.put(routeIdCounter, false);
                    invalidationReasons.put(-routeIdCounter, "Candidate route is TSM-TW infeasible: " + route.getRejectionReason());
                } else {
                    validCandidateRoutes.put(routeIdCounter, true);
                }
                routeIdCounter++;
            }
        }

        return ValidActionMask.builder()
                .validNextTasks(validNextTasks)
                .validCandidateRoutes(validCandidateRoutes)
                .invalidationReasons(invalidationReasons)
                .build();
    }
}
