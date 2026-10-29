package com.routeoptimizer.research.training.environment;

import com.routeoptimizer.research.reward.domain.DualRewardResult;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;

public interface RouteTrainingEnvironment {
    RouteDecisionState getCurrentState();
    void step(RouteAction action);
    boolean isTerminal();
    DualRewardResult calculateReward();
}
