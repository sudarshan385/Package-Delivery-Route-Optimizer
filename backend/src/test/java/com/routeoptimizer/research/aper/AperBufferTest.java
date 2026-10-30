package com.routeoptimizer.research.aper;

import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.domain.AperTransition;
import com.routeoptimizer.research.aper.service.AperBuffer;
import com.routeoptimizer.research.aper.service.PriorityCalculator;
import com.routeoptimizer.research.ddqn.domain.Transition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AperBufferTest {

    private AperBuffer buffer;
    private AperProperties properties;
    private Random deterministicRandom;

    @BeforeEach
    public void setUp() {
        properties = new AperProperties();
        properties.setHighImpactThreshold(0.5);
        properties.setHighImpactSampleRatio(0.6); // 60% high impact
        properties.setCapacity(100);
        properties.setAlpha(1.0);
        properties.setTdErrorWeight(1.0);
        properties.setComplexityWeight(0.0);

        PriorityCalculator calculator = new PriorityCalculator(properties);
        deterministicRandom = new Random(100);
        buffer = new AperBuffer(properties, calculator, deterministicRandom);
    }

    @Test
    public void testTransitionCreationAndClassification() {
        Transition t1 = Transition.builder().build();
        AperTransition aperT1 = buffer.store(t1, 0.1, 0.0); // Priority ~0.1 -> Low impact
        
        Transition t2 = Transition.builder().build();
        AperTransition aperT2 = buffer.store(t2, 0.8, 0.0); // Priority ~0.8 -> High impact

        assertNotNull(aperT1.getId());
        assertNotNull(aperT2.getId());

        assertEquals(1, buffer.getLowImpactSize());
        assertEquals(1, buffer.getHighImpactSize());
        
        assertFalse(aperT1.isHighImpact());
        assertTrue(aperT2.isHighImpact());
    }

    @Test
    public void testTdErrorUpdateChangesPriorityAndBuffer() {
        Transition t1 = Transition.builder().build();
        AperTransition aperT1 = buffer.store(t1, 0.1, 0.0);
        
        assertEquals(1, buffer.getLowImpactSize());
        assertEquals(0, buffer.getHighImpactSize());
        
        // Update TD error to make it high impact
        buffer.updateTdError(aperT1.getId(), 0.9);
        
        assertEquals(0, buffer.getLowImpactSize());
        assertEquals(1, buffer.getHighImpactSize());
        assertTrue(aperT1.isHighImpact());
    }

    @Test
    public void testSamplingFromEmptyBuffer() {
        List<AperTransition> sample = buffer.sample(10);
        assertTrue(sample.isEmpty(), "Sampling from empty buffer should return empty list safely");
    }

    @Test
    public void testSamplingFewerTransitionsThanBatchSize() {
        buffer.store(Transition.builder().build(), 0.9, 0.0);
        buffer.store(Transition.builder().build(), 0.1, 0.0);
        
        List<AperTransition> sample = buffer.sample(10);
        assertEquals(2, sample.size(), "Should safely return all available items if batch size is larger");
    }

    @Test
    public void testPrioritizedAdaptiveSampling() {
        // Store 5 high impact and 5 low impact
        for (int i = 0; i < 5; i++) {
            buffer.store(Transition.builder().build(), 0.8, 0.0);
            buffer.store(Transition.builder().build(), 0.2, 0.0);
        }
        
        // Batch size 5, ratio is 0.6 -> 3 high impact, 2 low impact
        List<AperTransition> sample = buffer.sample(5);
        
        assertEquals(5, sample.size());
        
        long highCount = sample.stream().filter(AperTransition::isHighImpact).count();
        long lowCount = sample.stream().filter(t -> !t.isHighImpact()).count();
        
        assertEquals(3, highCount, "Should sample exactly 3 high impact items based on ratio");
        assertEquals(2, lowCount, "Should sample exactly 2 low impact items based on ratio");
    }
    
    @Test
    public void testAperDoesNotBehaveAsUniformReplay() {
        // Uniform replay would give roughly equal chances to everything.
        // We'll put 1 extremely high priority item and 9 medium priority items in high-impact buffer.
        buffer.store(Transition.builder().build(), 100.0, 0.0);
        
        for (int i = 0; i < 9; i++) {
            buffer.store(Transition.builder().build(), 0.6, 0.0);
        }
        
        // We only sample 1 item from high impact (by setting batch size to 1, since ratio 0.6 rounds to 1 high, 0 low).
        // It should pick the superHigh item overwhelmingly often.
        int superHighCount = 0;
        
        for (int i = 0; i < 100; i++) {
            // Need to recreate buffer or reset it because sampling is WITHOUT replacement internally, 
            // wait, our method sampleFromBuffer removes items. 
            // Ah, let's fix the test to just check if prioritized logic favors higher values.
            // Since it removes, we'll store, sample 1, check, repeat.
            AperBuffer tempBuffer = new AperBuffer(properties, new PriorityCalculator(properties), new Random(i));
            AperTransition tSuper = tempBuffer.store(Transition.builder().build(), 100.0, 0.0);
            for (int j = 0; j < 9; j++) {
                tempBuffer.store(Transition.builder().build(), 0.6, 0.0);
            }
            List<AperTransition> s = tempBuffer.sample(1);
            if (!s.isEmpty() && s.get(0).getId().equals(tSuper.getId())) {
                superHighCount++;
            }
        }
        
        assertTrue(superHighCount > 10, "Prioritized sampling must favor the higher priority item heavily over uniform random (which would be ~10%)");
    }
}
