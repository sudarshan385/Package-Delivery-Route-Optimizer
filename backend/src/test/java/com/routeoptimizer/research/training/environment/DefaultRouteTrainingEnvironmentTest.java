package com.routeoptimizer.research.training.environment;

import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultRouteTrainingEnvironmentTest {

    private DefaultRouteTrainingEnvironment environment;
    private TrainingInput initialInput;

    @BeforeEach
    void setUp() {
        DualRewardService rewardService = Mockito.mock(DualRewardService.class);
        StateRepresentationService stateRepresentationService = new StateRepresentationService();

        TSMTWTask task1 = TSMTWTask.builder().id(1L).locationId(10L).taskType(ResearchTaskType.PICKUP).deliveryId(2L).build();
        TSMTWTask task2 = TSMTWTask.builder().id(2L).locationId(20L).taskType(ResearchTaskType.DELIVERY).deliveryId(2L).build();

        initialInput = TrainingInput.builder()
                .driverId(100L)
                .initialLocationId(5L)
                .currentTime(OffsetDateTime.now())
                .tasks(Arrays.asList(task1, task2))
                .referenceRoute(Arrays.asList(1L, 2L))
                .build();

        environment = new DefaultRouteTrainingEnvironment(
                initialInput,
                Collections.emptyList(),
                rewardService,
                stateRepresentationService
        );
    }

    @Test
    void testEnvironmentStateTransitions() {
        RouteDecisionState initialState = environment.getCurrentState();
        assertNotNull(initialState);
        assertFalse(environment.isTerminal());
        
        // Initial state allows task 1 (pickup), but masks out task 2 (delivery before pickup)
        assertTrue(initialState.getValidActionMask().getValidNextTasks().get(1L));
        assertFalse(initialState.getValidActionMask().getValidNextTasks().get(2L));

        // Step 1: Perform Pickup
        RouteAction action1 = RouteAction.builder().nextTaskId(1L).build();
        environment.step(action1);

        RouteDecisionState nextState = environment.getCurrentState();
        assertFalse(environment.isTerminal());
        assertTrue(nextState.getCompletedTaskIds().contains(1L));
        assertTrue(nextState.getPickedUpDeliveryIds().contains(2L)); // delivery 2 is picked up

        // Now task 1 should be masked out (completed) and task 2 should be valid (pickup completed)
        assertFalse(nextState.getValidActionMask().getValidNextTasks().get(1L));
        assertTrue(nextState.getValidActionMask().getValidNextTasks().get(2L));

        // Step 2: Perform Delivery
        RouteAction action2 = RouteAction.builder().nextTaskId(2L).build();
        environment.step(action2);

        RouteDecisionState terminalState = environment.getCurrentState();
        assertTrue(environment.isTerminal());
        assertTrue(terminalState.getCompletedTaskIds().contains(1L));
        assertTrue(terminalState.getCompletedTaskIds().contains(2L));
        
        // Everything should be masked out
        assertFalse(terminalState.getValidActionMask().getValidNextTasks().get(1L));
        assertFalse(terminalState.getValidActionMask().getValidNextTasks().get(2L));
    }
}
