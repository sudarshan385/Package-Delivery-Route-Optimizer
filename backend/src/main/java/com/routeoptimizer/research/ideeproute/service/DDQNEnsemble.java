package com.routeoptimizer.research.ideeproute.service;

import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;

/**
 * Interface representing the future ensemble boundary.
 * In a full DDQN pipeline, this would consume the TSM-TW candidate routes
 * and the i-DeepRoute rider behavioral encoding to score/select the final route.
 */
public interface DDQNEnsemble {
    void evaluate(TSMTWResult tsmTwResult, BehaviorRepresentation behaviorRepresentation);
}
