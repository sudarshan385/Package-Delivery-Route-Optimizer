package com.routeoptimizer.research.ddqn.domain;

import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Transition {
    RouteDecisionState state;
    RouteAction action;
    double reward;
    RouteDecisionState nextState;
    boolean done;
}
