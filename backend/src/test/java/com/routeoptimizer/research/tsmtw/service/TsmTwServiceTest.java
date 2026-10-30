package com.routeoptimizer.research.tsmtw.service;

import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TsmTwServiceTest {

    @Mock
    private LocationEdgeRepository locationEdgeRepository;

    @InjectMocks
    private TsmTwService tsmTwService;

    private OffsetDateTime startTime;

    @BeforeEach
    void setUp() {
        startTime = OffsetDateTime.now();
    }

    @Test
    void testValidPickupDeliverySequence() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L).build();
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L).build();
        
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Arrays.asList(pickup, delivery)).build();

        LocationEdge edge1 = new LocationEdge(); edge1.setDistance(10.0); edge1.setEstimatedTime(10.0);
        LocationEdge edge2 = new LocationEdge(); edge2.setDistance(5.0); edge2.setEstimatedTime(5.0);

        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(1L, 2L)).thenReturn(Optional.of(edge1));
        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(2L, 3L)).thenReturn(Optional.of(edge2));

        TSMTWResult result = tsmTwService.generateCandidates(request);

        assertTrue(result.isFeasible());
        assertEquals(1, result.getCandidateRoutes().size());
        assertEquals(15.0, result.getCandidateRoutes().get(0).getTotalDistance());
    }

    @Test
    void testDeliveryBeforePickupRejected() {
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L).build();
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L).build();
        
        // Only permutation is Delivery -> Pickup since we mock edges to only work in one way, or just let permutations generate both
        // Wait, TSMTWService evaluates all permutations. One will fail precedence, the other will fail edge (if not mocked).
        // Let's mock edges for both so only precedence fails for the invalid one.
        LocationEdge edge1 = new LocationEdge(); edge1.setDistance(10.0); edge1.setEstimatedTime(10.0);
        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(anyLong(), anyLong())).thenReturn(Optional.of(edge1));

        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Arrays.asList(pickup, delivery)).build();
        
        TSMTWResult result = tsmTwService.generateCandidates(request);
        
        assertTrue(result.isFeasible());
        assertEquals(1, result.getCandidateRoutes().size()); // 2 permutations, 1 rejected due to delivery before pickup, 1 accepted
        assertEquals(1L, result.getCandidateRoutes().get(0).getOrderedTasks().get(0).getId()); // Pickup first
    }

    @Test
    void testMissingPickupRejected() {
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L).build();
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Collections.singletonList(delivery)).build();
        
        TSMTWResult result = tsmTwService.generateCandidates(request);
        
        assertFalse(result.isFeasible());
        assertEquals("Missing pickup for deliveryId: 10", result.getRejectionReason());
    }

    @Test
    void testMissingDeliveryRejected() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L).build();
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Collections.singletonList(pickup)).build();
        
        TSMTWResult result = tsmTwService.generateCandidates(request);
        
        assertFalse(result.isFeasible());
        assertEquals("Missing delivery for deliveryId: 10", result.getRejectionReason());
    }

    @Test
    void testValidTimeWindowAccepted() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L)
                .earliestTime(startTime.plusMinutes(5)).latestTime(startTime.plusMinutes(20)).build();
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L)
                .earliestTime(startTime.plusMinutes(15)).latestTime(startTime.plusMinutes(30)).build();
        
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Arrays.asList(pickup, delivery)).build();

        LocationEdge edge1 = new LocationEdge(); edge1.setDistance(10.0); edge1.setEstimatedTime(10.0);
        LocationEdge edge2 = new LocationEdge(); edge2.setDistance(5.0); edge2.setEstimatedTime(5.0);

        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(1L, 2L)).thenReturn(Optional.of(edge1));
        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(2L, 3L)).thenReturn(Optional.of(edge2));

        TSMTWResult result = tsmTwService.generateCandidates(request);

        assertTrue(result.isFeasible());
    }

    @Test
    void testArrivalBeforeEarliestTimeWaitingHandledCorrectly() {
        // arrive at pickup at +10m, earliest is +15m -> wait 5m. Total time = 15m.
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L)
                .earliestTime(startTime.plusMinutes(15)).build();
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L).build();
        
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Arrays.asList(pickup, delivery)).build();

        LocationEdge edge1 = new LocationEdge(); edge1.setDistance(10.0); edge1.setEstimatedTime(10.0);
        LocationEdge edge2 = new LocationEdge(); edge2.setDistance(5.0); edge2.setEstimatedTime(5.0);

        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(1L, 2L)).thenReturn(Optional.of(edge1));
        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(2L, 3L)).thenReturn(Optional.of(edge2));

        TSMTWResult result = tsmTwService.generateCandidates(request);

        assertTrue(result.isFeasible());
        assertEquals(20.0, result.getCandidateRoutes().get(0).getEstimatedTime()); // 10 travel + 5 wait + 5 travel = 20
    }

    @Test
    void testArrivalAfterLatestTimeRejected() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(10L).taskType(ResearchTaskType.PICKUP).locationId(2L)
                .latestTime(startTime.plusMinutes(5)).build(); // arrive at +10m -> fails
        TSMTWTask delivery = TSMTWTask.builder().id(2L).deliveryId(10L).taskType(ResearchTaskType.DELIVERY).locationId(3L).build();
        
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Arrays.asList(pickup, delivery)).build();

        LocationEdge edge1 = new LocationEdge(); edge1.setDistance(10.0); edge1.setEstimatedTime(10.0);
        LocationEdge edge2 = new LocationEdge(); edge2.setDistance(5.0); edge2.setEstimatedTime(5.0);
        
        // Mock loosely to let both permutations evaluate and fail
        when(locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(anyLong(), anyLong())).thenReturn(Optional.of(edge1));

        TSMTWResult result = tsmTwService.generateCandidates(request);

        assertFalse(result.isFeasible());
        assertEquals("No feasible routes found", result.getRejectionReason());
    }

    @Test
    void testEmptyTaskListHandledCorrectly() {
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Collections.emptyList()).build();
        TSMTWResult result = tsmTwService.generateCandidates(request);
        
        assertFalse(result.isFeasible());
        assertEquals("Empty task list", result.getRejectionReason());
    }

    @Test
    void testInvalidPairedTaskRejected() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).deliveryId(null).taskType(ResearchTaskType.PICKUP).locationId(2L).build();
        TSMTWRequest request = TSMTWRequest.builder().startLocationId(1L).startTime(startTime).tasks(Collections.singletonList(pickup)).build();
        
        TSMTWResult result = tsmTwService.generateCandidates(request);
        assertFalse(result.isFeasible());
        assertEquals("Task missing delivery ID", result.getRejectionReason());
    }
}
