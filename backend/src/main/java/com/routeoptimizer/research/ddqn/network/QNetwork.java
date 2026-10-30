package com.routeoptimizer.research.ddqn.network;

import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;

public interface QNetwork {
    /**
     * Evaluates a specific action for a given state.
     */
    double evaluate(RouteDecisionState state, RouteAction action);
    
    /**
     * Updates the network based on a training target.
     * Currently a baseline for genuine gradient-based updates which would require an ML framework.
     */
    void update(RouteDecisionState state, RouteAction action, double target, double learningRate);
    
    /**
     * Copies weights from another network. Used for target network synchronization.
     */
    void copyWeightsFrom(QNetwork otherNetwork);
}
