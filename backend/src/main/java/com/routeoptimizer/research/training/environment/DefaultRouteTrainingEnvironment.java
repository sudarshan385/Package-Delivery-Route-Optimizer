package com.routeoptimizer.research.training.environment;

import com.routeoptimizer.research.reward.domain.DualRewardResult;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultRouteTrainingEnvironment implements RouteTrainingEnvironment {

    private final TrainingInput initialInput;
    private final DualRewardService rewardService;
    private final StateRepresentationService stateRepresentationService;
    private final List<CandidateRoute> candidateRoutes;
    
    private RouteDecisionState currentState;
    private final Set<Long> completedTaskIds = new HashSet<>();
    private final Set<Long> pickedUpDeliveryIds = new HashSet<>();
    private final List<Long> executedSequence = new ArrayList<>();
    
    private boolean terminal = false;

    public DefaultRouteTrainingEnvironment(
            TrainingInput initialInput,
            List<CandidateRoute> candidateRoutes,
            DualRewardService rewardService,
            StateRepresentationService stateRepresentationService) {
        
        if (initialInput == null) {
            throw new IllegalArgumentException("TrainingInput cannot be null");
        }
        if (initialInput.getTasks() == null || initialInput.getTasks().isEmpty()) {
            throw new IllegalArgumentException("Tasks cannot be null or empty");
        }
        
        this.initialInput = initialInput;
        this.candidateRoutes = candidateRoutes;
        this.rewardService = rewardService;
        this.stateRepresentationService = stateRepresentationService;
        
        updateState();
    }

    private void updateState() {
        this.currentState = stateRepresentationService.buildState(
                initialInput.getDriverId(),
                initialInput.getInitialLocationId(),
                initialInput.getCurrentTime(),
                initialInput.getTasks(),
                completedTaskIds,
                pickedUpDeliveryIds,
                candidateRoutes,
                initialInput.getRiderBehavior()
        );
        
        // Terminal check
        if (completedTaskIds.size() >= initialInput.getTasks().size() || 
           (currentState.getValidActionMask().getValidNextTasks().isEmpty() && 
            currentState.getValidActionMask().getValidCandidateRoutes().isEmpty())) {
            this.terminal = true;
        }
    }

    @Override
    public RouteDecisionState getCurrentState() {
        return currentState;
    }

    @Override
    public void step(RouteAction action) {
        if (terminal) {
            throw new IllegalStateException("Episode is already terminal");
        }
        if (action == null) {
            throw new IllegalArgumentException("Action cannot be null");
        }
        
        if (action.getNextTaskId() != null) {
            completedTaskIds.add(action.getNextTaskId());
            executedSequence.add(action.getNextTaskId());
            
            initialInput.getTasks().stream()
                .filter(t -> t.getId().equals(action.getNextTaskId()))
                .findFirst()
                .ifPresent(task -> {
                    if ("PICKUP".equals(task.getTaskType().name()) && task.getDeliveryId() != null) {
                        pickedUpDeliveryIds.add(task.getDeliveryId());
                    }
                });
        }
        else if (action.getCandidateRouteId() != null) {
            int idx = (int) (action.getCandidateRouteId() - 1);
            if (candidateRoutes == null || idx < 0 || idx >= candidateRoutes.size()) {
                throw new IllegalArgumentException("Invalid candidate route id");
            }
            CandidateRoute selected = candidateRoutes.get(idx);
            selected.getOrderedTasks().forEach(task -> {
                completedTaskIds.add(task.getId());
                executedSequence.add(task.getId());
                if ("PICKUP".equals(task.getTaskType().name()) && task.getDeliveryId() != null) {
                    pickedUpDeliveryIds.add(task.getDeliveryId());
                }
            });
        }
        else {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
        
        updateState();
    }

    @Override
    public boolean isTerminal() {
        return terminal;
    }

    @Override
    public DualRewardResult calculateReward() {
        if (initialInput.getReferenceRoute() == null || initialInput.getReferenceRoute().isEmpty()) {
            throw new IllegalArgumentException("Missing required reference route for CMR/ED calculation");
        }
        return rewardService.calculateReward(executedSequence, initialInput.getReferenceRoute());
    }
}
