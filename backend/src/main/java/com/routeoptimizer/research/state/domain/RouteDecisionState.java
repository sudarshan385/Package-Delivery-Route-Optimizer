package com.routeoptimizer.research.state.domain;

import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Immutable representation of the RL state available at a routing decision point.
 */
@Value
@Builder
public class RouteDecisionState {
    
    // Categorical / Context
    Long driverId;
    Long currentLocationId;
    OffsetDateTime currentTime;

    // Task Information
    List<StateTask> remainingTasks;
    Set<Long> completedTaskIds;
    Set<Long> pickedUpDeliveryIds;

    // Candidate routes from TSM-TW
    List<CandidateRoute> candidateRoutes;

    // Rider behavior from i-DeepRoute
    BehaviorRepresentation behaviorRepresentation;

    // Constraint / Action Mask
    ValidActionMask validActionMask;

    @Value
    @Builder
    public static class StateTask {
        Long taskId;
        Long locationId;
        Long deliveryId;
        String taskType; // PICKUP or DELIVERY
        OffsetDateTime earliestTime;
        OffsetDateTime latestTime;
    }
}
