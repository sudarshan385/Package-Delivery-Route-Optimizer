package com.routeoptimizer.research.aper.service;

import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.domain.AperTransition;
import com.routeoptimizer.research.ddqn.domain.Transition;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AperBufferTest {

    private AperBuffer aperBuffer;
    private AperProperties properties;
    private PriorityCalculator priorityCalculator;

    @BeforeEach
    void setUp() {
        properties = new AperProperties();
        properties.setHighImpactThreshold(10.0);
        properties.setHighImpactSampleRatio(0.8);
        properties.setCapacity(100);
        properties.setAlpha(1.0);
        properties.setTdErrorWeight(1.0);
        properties.setComplexityWeight(1.0);

        priorityCalculator = new PriorityCalculator(properties);
        aperBuffer = new AperBuffer(properties, priorityCalculator, new Random(42));
    }

    private Transition createDummyTransition() {
        return Transition.builder()
                .state(RouteDecisionState.builder().build())
                .action(RouteAction.builder().nextTaskId(1L).build())
                .reward(1.0)
                .nextState(RouteDecisionState.builder().build())
                .done(true)
                .build();
    }

    @Test
    void testAperPriorityAndComplexity() {
        Transition t1 = createDummyTransition();
        AperTransition a1 = aperBuffer.store(t1, 5.0, 0.0); // Priority ~ 5.0 -> Low Impact
        assertEquals(1, aperBuffer.getLowImpactSize());
        assertEquals(0, aperBuffer.getHighImpactSize());
        assertTrue(a1.getPriority() < 10.0);

        Transition t2 = createDummyTransition();
        AperTransition a2 = aperBuffer.store(t2, 5.0, 6.0); // Priority = 11.0 -> High Impact
        assertEquals(1, aperBuffer.getLowImpactSize());
        assertEquals(1, aperBuffer.getHighImpactSize());
        assertTrue(a2.getPriority() >= 10.0);
    }

    @Test
    void testAperUpdateTdErrorMigrations() {
        Transition t1 = createDummyTransition();
        AperTransition a1 = aperBuffer.store(t1, 20.0, 0.0); // Priority 20 -> High Impact
        assertEquals(1, aperBuffer.getHighImpactSize());

        // Update tdError so priority drops below 10.0 threshold
        aperBuffer.updateTdError(a1.getId(), 2.0);
        assertEquals(0, aperBuffer.getHighImpactSize());
        assertEquals(1, aperBuffer.getLowImpactSize());

        // Update tdError so priority goes above 10.0 threshold
        aperBuffer.updateTdError(a1.getId(), 30.0);
        assertEquals(1, aperBuffer.getHighImpactSize());
        assertEquals(0, aperBuffer.getLowImpactSize());
    }

    @Test
    void testPrioritizedSampling() {
        // Insert a highly prioritized item and a low prioritized item in high impact buffer
        properties.setHighImpactThreshold(0.0); // Everything in high impact
        
        // Use a deterministic random that picks the higher priority first
        AperBuffer deterministicBuffer = new AperBuffer(properties, priorityCalculator, new Random(42));
        
        Transition t1 = createDummyTransition();
        AperTransition a1 = deterministicBuffer.store(t1, 1.0, 0.0); // P=1
        
        Transition t2 = createDummyTransition();
        AperTransition a2 = deterministicBuffer.store(t2, 100.0, 0.0); // P=100

        List<AperTransition> batch = deterministicBuffer.sample(1);
        assertEquals(1, batch.size());
        assertEquals(a2.getId(), batch.get(0).getId(), "Should probabilistically favor the very high priority transition");
    }
}
