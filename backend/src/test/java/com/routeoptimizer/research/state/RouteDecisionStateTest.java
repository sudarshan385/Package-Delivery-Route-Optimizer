package com.routeoptimizer.research.state;

import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RouteDecisionStateTest {

    private StateRepresentationService builderService;
    private OffsetDateTime currentTime;

    @BeforeEach
    void setUp() {
        builderService = new StateRepresentationService();
        currentTime = OffsetDateTime.now();
    }

    @Test
    void testStateCreatedFromValidRoutingContext() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(100L).taskType(ResearchTaskType.PICKUP).build();
        List<CandidateRoute> candidates = Collections.singletonList(CandidateRoute.builder().feasible(true).build());
        BehaviorRepresentation behavior = BehaviorRepresentation.builder().originalInput(new IDeepRouteInput()).build();

        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime,
                Collections.singletonList(pickup),
                Collections.emptySet(),
                Collections.emptySet(),
                candidates,
                behavior
        );

        assertNotNull(state);
        assertEquals(10L, state.getDriverId());
        assertEquals(1, state.getRemainingTasks().size());
        assertEquals(1, state.getCandidateRoutes().size());
        assertNotNull(state.getBehaviorRepresentation());
    }

    @Test
    void testEmptyRemainingTaskList() {
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime,
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyList(),
                null
        );

        assertTrue(state.getRemainingTasks().isEmpty());
        assertTrue(state.getValidActionMask().getValidNextTasks().isEmpty());
    }

    @Test
    void testMultipleRemainingTasks() {
        TSMTWTask t1 = TSMTWTask.builder().id(1L).deliveryId(100L).taskType(ResearchTaskType.PICKUP).build();
        TSMTWTask t2 = TSMTWTask.builder().id(2L).deliveryId(101L).taskType(ResearchTaskType.PICKUP).build();
        
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Arrays.asList(t1, t2),
                Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), null
        );

        assertEquals(2, state.getRemainingTasks().size());
    }

    @Test
    void testCompletedTasksAreRepresentedCorrectly() {
        TSMTWTask t1 = TSMTWTask.builder().id(1L).deliveryId(100L).taskType(ResearchTaskType.PICKUP).build();
        
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(t1),
                new HashSet<>(Collections.singletonList(1L)), 
                new HashSet<>(Collections.singletonList(100L)), 
                Collections.emptyList(), null
        );

        assertTrue(state.getCompletedTaskIds().contains(1L));
        assertTrue(state.getPickedUpDeliveryIds().contains(100L));
    }

    @Test
    void testPickupDeliveryPrecedenceRepresentedCorrectly() {
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(100L).taskType(ResearchTaskType.DELIVERY).build();
        
        // Pickup is completed, so delivery should be valid
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(delivery),
                new HashSet<>(Collections.singletonList(1L)), 
                new HashSet<>(Collections.singletonList(100L)), 
                Collections.emptyList(), null
        );

        assertTrue(state.getValidActionMask().isTaskValid(2L));
    }

    @Test
    void testDeliveryBeforePickupProducesInvalidAction() {
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(100L).taskType(ResearchTaskType.DELIVERY).build();
        
        // Pickup is NOT completed
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(delivery),
                Collections.emptySet(), Collections.emptySet(), 
                Collections.emptyList(), null
        );

        assertFalse(state.getValidActionMask().isTaskValid(2L));
        assertEquals("Delivery cannot be selected before its pickup is completed", state.getValidActionMask().getInvalidationReasons().get(2L));
    }

    @Test
    void testCompletedTaskCannotBecomeValidAction() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(100L).taskType(ResearchTaskType.PICKUP).build();
        
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(pickup),
                new HashSet<>(Collections.singletonList(1L)), Collections.emptySet(), 
                Collections.emptyList(), null
        );

        assertFalse(state.getValidActionMask().isTaskValid(1L));
        assertEquals("Task is already completed", state.getValidActionMask().getInvalidationReasons().get(1L));
    }

    @Test
    void testSameInputProducesIdenticalState() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(100L).taskType(ResearchTaskType.PICKUP).build();
        
        RouteDecisionState state1 = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(pickup),
                Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), null
        );

        RouteDecisionState state2 = builderService.buildState(
                10L, 20L, currentTime, Collections.singletonList(pickup),
                Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), null
        );

        assertEquals(state1, state2);
    }
    
    @Test
    void testActionRepresentation() {
        RouteAction action = RouteAction.builder().nextTaskId(1L).build();
        assertEquals(1L, action.getNextTaskId());
    }
    @Test
    void testTsmTwInfeasibleActionIsMasked() {
        CandidateRoute feasible = CandidateRoute.builder().feasible(true).build();
        CandidateRoute infeasible = CandidateRoute.builder().feasible(false).rejectionReason("Time window missed").build();
        
        RouteDecisionState state = builderService.buildState(
                10L, 20L, currentTime, Collections.emptyList(),
                Collections.emptySet(), Collections.emptySet(), 
                Arrays.asList(feasible, infeasible), null
        );

        assertTrue(state.getValidActionMask().getValidCandidateRoutes().get(1L));
        assertFalse(state.getValidActionMask().getValidCandidateRoutes().get(2L));
        assertEquals("Candidate route is TSM-TW infeasible: Time window missed", state.getValidActionMask().getInvalidationReasons().get(-2L));
    }
}
