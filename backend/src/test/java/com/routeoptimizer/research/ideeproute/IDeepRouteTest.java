package com.routeoptimizer.research.ideeproute;

import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.research.domain.HistoricalRoute;
import com.routeoptimizer.research.domain.HistoricalTask;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.ideeproute.domain.HistoricalTaskRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import com.routeoptimizer.research.ideeproute.domain.RiderPreference;
import com.routeoptimizer.research.ideeproute.masking.PrecedenceAwareAttentionMask;
import com.routeoptimizer.research.ideeproute.service.PlaceholderRiderRoutingBehaviorModel;
import com.routeoptimizer.research.ideeproute.service.TemporalTaskChainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IDeepRouteTest {

    private TemporalTaskChainingService chainingService;
    private PlaceholderRiderRoutingBehaviorModel model;

    @BeforeEach
    void setUp() {
        chainingService = new TemporalTaskChainingService();
        model = new PlaceholderRiderRoutingBehaviorModel();
    }

    @Test
    void testHistoricalTasksPreserveChronologicalOrder() {
        Driver driver = new Driver(); driver.setId(100L);
        HistoricalRoute route = new HistoricalRoute(); route.setId(10L); route.setDriver(driver);

        HistoricalTask task2 = HistoricalTask.builder().id(2L).sequenceOrder(2).build();
        HistoricalTask task1 = HistoricalTask.builder().id(1L).sequenceOrder(1).build();
        HistoricalTask task3 = HistoricalTask.builder().id(3L).sequenceOrder(3).build();

        // Adding out of order intentionally
        route.setTasks(Arrays.asList(task2, task3, task1));

        IDeepRouteInput input = chainingService.extractTemporalSequence(route);
        
        List<HistoricalTaskRepresentation> seq = input.getTemporalSequence();
        assertEquals(3, seq.size());
        assertEquals(1L, seq.get(0).getTaskId());
        assertEquals(2L, seq.get(1).getTaskId());
        assertEquals(3L, seq.get(2).getTaskId());
    }

    @Test
    void testPrecedenceAwareMasking() {
        HistoricalTaskRepresentation pickup = HistoricalTaskRepresentation.builder()
                .taskId(1L).deliveryId(50L).taskType(ResearchTaskType.PICKUP).sequenceOrder(1).build();
        HistoricalTaskRepresentation delivery = HistoricalTaskRepresentation.builder()
                .taskId(2L).deliveryId(50L).taskType(ResearchTaskType.DELIVERY).sequenceOrder(2).build();

        PrecedenceAwareAttentionMask mask = new PrecedenceAwareAttentionMask(Arrays.asList(pickup, delivery));

        // Delivery(index 1) must not be a valid predecessor for Pickup(index 0)
        assertFalse(mask.isValidPredecessor(1, 0));
        
        // Pickup(index 0) can be a valid predecessor for Delivery(index 1)
        assertTrue(mask.isValidPredecessor(0, 1));
        
        // Both can attend to themselves initially
        assertTrue(mask.isValidPredecessor(0, 0));
        assertTrue(mask.isValidPredecessor(1, 1));
    }

    @Test
    void testValidTaskRelationshipsRemainAvailable() {
        HistoricalTaskRepresentation p1 = HistoricalTaskRepresentation.builder()
                .taskId(1L).deliveryId(50L).taskType(ResearchTaskType.PICKUP).build();
        HistoricalTaskRepresentation p2 = HistoricalTaskRepresentation.builder()
                .taskId(2L).deliveryId(51L).taskType(ResearchTaskType.PICKUP).build();

        PrecedenceAwareAttentionMask mask = new PrecedenceAwareAttentionMask(Arrays.asList(p1, p2));
        
        // Unrelated pickups can precede each other
        assertTrue(mask.isValidPredecessor(0, 1));
        assertTrue(mask.isValidPredecessor(1, 0));
    }

    @Test
    void testMultipleRidersRemainSeparatedAndContextPreserved() {
        IDeepRouteInput input1 = IDeepRouteInput.builder()
                .driverId(100L).routeId(10L).temporalSequence(Arrays.asList(
                        HistoricalTaskRepresentation.builder().taskId(1L).build()
                )).build();

        IDeepRouteInput input2 = IDeepRouteInput.builder()
                .driverId(200L).routeId(20L).temporalSequence(Arrays.asList(
                        HistoricalTaskRepresentation.builder().taskId(2L).build()
                )).build();

        model.encode(input1);
        model.encode(input2);

        RiderPreference pref1 = model.getRiderPreference(100L);
        RiderPreference pref2 = model.getRiderPreference(200L);

        assertEquals(100L, pref1.getDriverId());
        assertEquals(1, pref1.getEncodedSequences().size());
        assertEquals(1L, pref1.getEncodedSequences().get(0).getOriginalInput().getTemporalSequence().get(0).getTaskId());

        assertEquals(200L, pref2.getDriverId());
        assertEquals(1, pref2.getEncodedSequences().size());
        assertEquals(2L, pref2.getEncodedSequences().get(0).getOriginalInput().getTemporalSequence().get(0).getTaskId());
    }

    @Test
    void testAttentionMaskIsDeterministic() {
        HistoricalTaskRepresentation t1 = HistoricalTaskRepresentation.builder().taskId(1L).deliveryId(50L).taskType(ResearchTaskType.DELIVERY).build();
        HistoricalTaskRepresentation t2 = HistoricalTaskRepresentation.builder().taskId(2L).deliveryId(50L).taskType(ResearchTaskType.PICKUP).build();
        
        PrecedenceAwareAttentionMask mask1 = new PrecedenceAwareAttentionMask(Arrays.asList(t1, t2));
        PrecedenceAwareAttentionMask mask2 = new PrecedenceAwareAttentionMask(Arrays.asList(t1, t2));
        
        assertArrayEquals(mask1.getRawMask(), mask2.getRawMask());
    }
    
    @Test
    void testSameInputProducesSameRepresentation() {
        IDeepRouteInput input = IDeepRouteInput.builder()
                .driverId(100L).routeId(10L).temporalSequence(Arrays.asList(
                        HistoricalTaskRepresentation.builder().taskId(1L).build()
                )).build();
                
        BehaviorRepresentation rep1 = model.encode(input);
        BehaviorRepresentation rep2 = model.encode(input); // Note: encodes again, appends to memory
        
        assertArrayEquals(rep1.getMask().getRawMask(), rep2.getMask().getRawMask());
    }

    @Test
    void testEmptyHistoricalSequenceHandledSafely() {
        IDeepRouteInput input = IDeepRouteInput.builder()
                .driverId(100L).routeId(10L).temporalSequence(Collections.emptyList()).build();

        BehaviorRepresentation rep = model.encode(input);
        assertNull(rep.getMask());
    }

    @Test
    void testMultipleTaskSequencesAreHandled() {
        IDeepRouteInput input1 = IDeepRouteInput.builder()
                .driverId(100L).routeId(10L).temporalSequence(Arrays.asList(
                        HistoricalTaskRepresentation.builder().taskId(1L).build()
                )).build();
        IDeepRouteInput input2 = IDeepRouteInput.builder()
                .driverId(100L).routeId(11L).temporalSequence(Arrays.asList(
                        HistoricalTaskRepresentation.builder().taskId(2L).build()
                )).build();

        model.encode(input1);
        model.encode(input2);
        
        RiderPreference pref = model.getRiderPreference(100L);
        assertEquals(2, pref.getEncodedSequences().size());
    }
}
