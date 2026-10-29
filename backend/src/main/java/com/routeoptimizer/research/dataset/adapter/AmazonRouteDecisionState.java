package com.routeoptimizer.research.dataset.adapter;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * String-based representation boundary for Amazon data to interface
 * with research concepts (State representation, action mask) without breaking
 * the generic Long-based legacy research models.
 */
@Value
@Builder
public class AmazonRouteDecisionState {
    
    String driverId;
    String currentLocationId;
    OffsetDateTime currentTime;

    List<StateTaskString> remainingTasks;
    List<String> completedTaskIds;

    // DDQN-compatible valid action mask using Strings
    Map<String, Boolean> validNextTasks;
    Map<String, String> invalidationReasons;

    @Value
    @Builder
    public static class StateTaskString {
        String taskId;
        String locationId;
        String deliveryId;
        String taskType;
        OffsetDateTime earliestTime;
        OffsetDateTime latestTime;
    }
}
