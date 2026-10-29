package com.routeoptimizer.research.ideeproute.service;

import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import com.routeoptimizer.research.ideeproute.domain.RiderPreference;

public interface RiderRoutingBehaviorModel {
    /**
     * Encodes historical task sequences into a feature representation.
     */
    BehaviorRepresentation encode(IDeepRouteInput input);

    /**
     * Gets rider-specific routing preference representation.
     */
    RiderPreference getRiderPreference(Long driverId);
}
