package com.routeoptimizer.research.state.service;

import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class StateRepresentationServiceTest {

    private StateRepresentationService service;

    @BeforeEach
    public void setUp() {
        service = new StateRepresentationService();
    }

    @Test
    public void testValidStateCreation() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BehaviorRepresentation behavior = BehaviorRepresentation.builder().build();

        TSMTWTask task1 = TSMTWTask.builder()
                .id(1L)
                .taskType(ResearchTaskType.PICKUP)
                .earliestTime(now.minusHours(1))
                .latestTime(now.plusHours(1))
                .build();

        List<TSMTWTask> remainingTasks = List.of(task1);
        Set<Long> completedTasks = new HashSet<>();
        Set<Long> pickedUp = new HashSet<>();

        RouteDecisionState state = service.buildState(
                100L, 200L, now, remainingTasks, completedTasks, pickedUp, new ArrayList<>(), behavior);

        assertNotNull(state);
        assertEquals(100L, state.getDriverId());
        assertEquals(200L, state.getCurrentLocationId());
        assertEquals(now, state.getCurrentTime());
        assertEquals(1, state.getRemainingTasks().size());
        assertEquals(1L, state.getRemainingTasks().get(0).getTaskId());
        assertEquals("PICKUP", state.getRemainingTasks().get(0).getTaskType());
        assertTrue(state.getValidActionMask().isTaskValid(1L));
        assertEquals(behavior, state.getBehaviorRepresentation());
    }

    @Test
    public void testDeliveryBeforePickupIsMasked() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TSMTWTask deliveryTask = TSMTWTask.builder()
                .id(2L)
                .deliveryId(10L)
                .taskType(ResearchTaskType.DELIVERY)
                .build();

        List<TSMTWTask> remainingTasks = List.of(deliveryTask);
        Set<Long> completedTasks = new HashSet<>();
        Set<Long> pickedUp = new HashSet<>(); // deliveryId 10 is NOT in pickedUp

        RouteDecisionState state = service.buildState(
                100L, 200L, now, remainingTasks, completedTasks, pickedUp, new ArrayList<>(), null);

        assertNotNull(state);
        ValidActionMask mask = state.getValidActionMask();
        assertFalse(mask.isTaskValid(2L), "Delivery should be masked if pickup is not completed");
        assertTrue(mask.getInvalidationReasons().get(2L).contains("before its pickup is completed"));
    }

    @Test
    public void testCompletedTaskIsMasked() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TSMTWTask pickupTask = TSMTWTask.builder()
                .id(1L)
                .taskType(ResearchTaskType.PICKUP)
                .build();

        List<TSMTWTask> remainingTasks = List.of(pickupTask);
        Set<Long> completedTasks = Set.of(1L); // Task 1 is completed
        Set<Long> pickedUp = new HashSet<>();

        RouteDecisionState state = service.buildState(
                100L, 200L, now, remainingTasks, completedTasks, pickedUp, new ArrayList<>(), null);

        assertNotNull(state);
        ValidActionMask mask = state.getValidActionMask();
        assertFalse(mask.isTaskValid(1L), "Completed task should be masked");
        assertTrue(mask.getInvalidationReasons().get(1L).contains("already completed"));
    }

    @Test
    public void testTSMTWInfeasibleActionIsMasked() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CandidateRoute route1 = CandidateRoute.builder().feasible(true).build();
        CandidateRoute route2 = CandidateRoute.builder().feasible(false).rejectionReason("Time window violation").build();

        List<CandidateRoute> candidateRoutes = List.of(route1, route2);

        RouteDecisionState state = service.buildState(
                100L, 200L, now, new ArrayList<>(), new HashSet<>(), new HashSet<>(), candidateRoutes, null);

        assertNotNull(state);
        ValidActionMask mask = state.getValidActionMask();
        assertTrue(mask.getValidCandidateRoutes().get(1L));
        assertFalse(mask.getValidCandidateRoutes().get(2L));
        assertTrue(mask.getInvalidationReasons().get(-2L).contains("TSM-TW infeasible"));
    }

    @Test
    public void testEmptyTaskListHandledSafely() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RouteDecisionState state = service.buildState(
                100L, 200L, now, new ArrayList<>(), new HashSet<>(), new HashSet<>(), new ArrayList<>(), null);

        assertNotNull(state);
        assertTrue(state.getRemainingTasks().isEmpty());
        assertTrue(state.getValidActionMask().getValidNextTasks().isEmpty());
    }

    @Test
    public void testSameInputProducesSameState() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BehaviorRepresentation behavior = BehaviorRepresentation.builder().build();

        TSMTWTask task1 = TSMTWTask.builder()
                .id(1L)
                .taskType(ResearchTaskType.PICKUP)
                .earliestTime(now.minusHours(1))
                .latestTime(now.plusHours(1))
                .build();

        List<TSMTWTask> remainingTasks = List.of(task1);
        Set<Long> completedTasks = new HashSet<>();
        Set<Long> pickedUp = new HashSet<>();

        RouteDecisionState state1 = service.buildState(
                100L, 200L, now, remainingTasks, completedTasks, pickedUp, new ArrayList<>(), behavior);

        RouteDecisionState state2 = service.buildState(
                100L, 200L, now, remainingTasks, completedTasks, pickedUp, new ArrayList<>(), behavior);

        assertEquals(state1, state2);
    }
}
