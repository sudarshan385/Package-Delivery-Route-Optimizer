package com.routeoptimizer.research.ddqn.service;

import com.routeoptimizer.research.ddqn.config.DDQNProperties;
import com.routeoptimizer.research.ddqn.domain.Transition;
import com.routeoptimizer.research.ddqn.exception.NoValidActionException;
import com.routeoptimizer.research.ddqn.network.BaselineDeterministicQNetwork;
import com.routeoptimizer.research.ddqn.network.QNetwork;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class DDQNService {

    private final DDQNProperties properties;
    private final QNetwork onlineNetwork;
    private final QNetwork targetNetwork;
    private final Random random;
    private double currentEpsilon;
    private int stepCount = 0;

    @org.springframework.beans.factory.annotation.Autowired
    public DDQNService(DDQNProperties properties) {
        this.properties = properties;
        this.onlineNetwork = new BaselineDeterministicQNetwork();
        this.targetNetwork = new BaselineDeterministicQNetwork();
        this.random = new Random(42); // deterministic for testing
        this.currentEpsilon = properties.getEpsilon();
    }

    // For testing
    public DDQNService(DDQNProperties properties, Random random) {
        this.properties = properties;
        this.onlineNetwork = new BaselineDeterministicQNetwork();
        this.targetNetwork = new BaselineDeterministicQNetwork();
        this.random = random;
        this.currentEpsilon = properties.getEpsilon();
    }

    public RouteAction selectAction(RouteDecisionState state) {
        List<RouteAction> validActions = getValidActions(state);

        if (validActions.isEmpty()) {
            throw new NoValidActionException("No valid actions available in the current state.");
        }

        if (random.nextDouble() < currentEpsilon) {
            // Explore
            return validActions.get(random.nextInt(validActions.size()));
        } else {
            // Exploit
            return getBestAction(state, validActions, onlineNetwork);
        }
    }

    public double calculateTdError(Transition transition) {
        double target;
        if (transition.isDone()) {
            target = transition.getReward();
        } else {
            List<RouteAction> nextValidActions = getValidActions(transition.getNextState());
            if (nextValidActions.isEmpty()) {
                target = transition.getReward();
            } else {
                RouteAction bestNextAction = getBestAction(transition.getNextState(), nextValidActions, onlineNetwork);
                double qTargetNext = targetNetwork.evaluate(transition.getNextState(), bestNextAction);
                target = transition.getReward() + properties.getGamma() * qTargetNext;
            }
        }
        double currentQ = onlineNetwork.evaluate(transition.getState(), transition.getAction());
        return Math.abs(target - currentQ);
    }

    public double train(Transition transition) {
        double target;
        if (transition.isDone()) {
            target = transition.getReward();
        } else {
            List<RouteAction> nextValidActions = getValidActions(transition.getNextState());
            if (nextValidActions.isEmpty()) {
                target = transition.getReward();
            } else {
                // Double DQN Principle:
                // 1. Online network selects the best next action
                RouteAction bestNextAction = getBestAction(transition.getNextState(), nextValidActions, onlineNetwork);

                // 2. Target network evaluates that selected action
                double qTargetNext = targetNetwork.evaluate(transition.getNextState(), bestNextAction);
                target = transition.getReward() + properties.getGamma() * qTargetNext;
            }
        }

        double currentQ = onlineNetwork.evaluate(transition.getState(), transition.getAction());
        double tdError = Math.abs(target - currentQ);

        onlineNetwork.update(transition.getState(), transition.getAction(), target, properties.getLearningRate());

        stepCount++;
        if (stepCount % properties.getTargetUpdateFrequency() == 0) {
            syncTargetNetwork();
        }

        decayEpsilon();
        return tdError;
    }

    public void syncTargetNetwork() {
        targetNetwork.copyWeightsFrom(onlineNetwork);
    }

    public QNetwork getOnlineNetwork() {
        return onlineNetwork;
    }

    public QNetwork getTargetNetwork() {
        return targetNetwork;
    }

    public double getCurrentEpsilon() {
        return currentEpsilon;
    }

    private void decayEpsilon() {
        currentEpsilon = Math.max(properties.getMinEpsilon(), currentEpsilon * properties.getEpsilonDecay());
    }

    private RouteAction getBestAction(RouteDecisionState state, List<RouteAction> validActions, QNetwork network) {
        RouteAction bestAction = null;
        double maxQ = Double.NEGATIVE_INFINITY;

        for (RouteAction action : validActions) {
            double qValue = network.evaluate(state, action);
            if (qValue > maxQ) {
                maxQ = qValue;
                bestAction = action;
            }
        }
        return bestAction;
    }

    private List<RouteAction> getValidActions(RouteDecisionState state) {
        ValidActionMask mask = state.getValidActionMask();
        List<RouteAction> actions = new ArrayList<>();

        if (mask == null) {
            return actions;
        }

        if (mask.getValidNextTasks() != null) {
            for (Map.Entry<Long, Boolean> entry : mask.getValidNextTasks().entrySet()) {
                if (entry.getValue()) {
                    actions.add(RouteAction.builder().nextTaskId(entry.getKey()).build());
                }
            }
        }

        if (mask.getValidCandidateRoutes() != null) {
            for (Map.Entry<Long, Boolean> entry : mask.getValidCandidateRoutes().entrySet()) {
                if (entry.getValue()) {
                    actions.add(RouteAction.builder().candidateRouteId(entry.getKey()).build());
                }
            }
        }

        return actions;
    }
}
