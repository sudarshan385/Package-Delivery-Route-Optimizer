package com.routeoptimizer.research.state.domain;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic valid-action mask based on existing operational constraints.
 * Ensures the future DDQN never selects an invalid task.
 */
@Value
@Builder
public class ValidActionMask {
    /** 
     * Map indicating if a specific task ID is a valid next action. 
     * True means valid, false means masked (invalid).
     */
    @Builder.Default
    Map<Long, Boolean> validNextTasks = Collections.emptyMap();
    
    /**
     * Optional: Map indicating if a specific candidate route (from TSM-TW) is valid.
     */
    @Builder.Default
    Map<Long, Boolean> validCandidateRoutes = Collections.emptyMap();

    /**
     * Set of reason codes/strings explaining why certain actions were masked,
     * useful for debugging and tracing state generation.
     */
    @Builder.Default
    Map<Long, String> invalidationReasons = Collections.emptyMap();

    public boolean isTaskValid(Long taskId) {
        return Boolean.TRUE.equals(validNextTasks.get(taskId));
    }
}
