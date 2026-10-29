package com.routeoptimizer.research.state.domain;

import lombok.Builder;
import lombok.Value;

/**
 * Immutable representation of a routing action to be selected by the future DDQN policy.
 */
@Value
@Builder
public class RouteAction {
    /** The ID of the task chosen to be executed next. */
    Long nextTaskId;
    
    /** Optional identifier for the candidate route if selecting entire routes at once. */
    Long candidateRouteId;
}
