package com.routeoptimizer.research.ddqn.network;

import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A deterministic baseline Q-network implementation suitable for unit/integration testing.
 * Limitation: Since no suitable ML framework (e.g., Deeplearning4j, PyTorch) is present in the stack,
 * this class cannot perform genuine gradient-based neural-network training. 
 * It acts as a deterministic mock-like tabular structure to prove DDQN architecture correctness.
 */
public class BaselineDeterministicQNetwork implements QNetwork {

    private final Map<StateActionPair, Double> qTable = new HashMap<>();

    @Override
    public double evaluate(RouteDecisionState state, RouteAction action) {
        // Deterministic pseudo-random Q-value if not updated yet, based on task ID for testing.
        return qTable.getOrDefault(new StateActionPair(state, action), (double) (action.getNextTaskId() != null ? action.getNextTaskId() : 0));
    }

    @Override
    public void update(RouteDecisionState state, RouteAction action, double target, double learningRate) {
        StateActionPair pair = new StateActionPair(state, action);
        double currentQ = evaluate(state, action);
        double newQ = currentQ + learningRate * (target - currentQ);
        qTable.put(pair, newQ);
    }

    @Override
    public void copyWeightsFrom(QNetwork otherNetwork) {
        if (otherNetwork instanceof BaselineDeterministicQNetwork) {
            this.qTable.clear();
            this.qTable.putAll(((BaselineDeterministicQNetwork) otherNetwork).qTable);
        } else {
            throw new IllegalArgumentException("Cannot copy weights from different network type");
        }
    }
    
    public Map<StateActionPair, Double> getWeights() {
        return new HashMap<>(qTable);
    }

    private static class StateActionPair {
        private final RouteDecisionState state;
        private final RouteAction action;

        public StateActionPair(RouteDecisionState state, RouteAction action) {
            this.state = state;
            this.action = action;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StateActionPair that = (StateActionPair) o;
            return state.equals(that.state) && action.equals(that.action);
        }

        @Override
        public int hashCode() {
            return Objects.hash(state, action);
        }
    }
}
