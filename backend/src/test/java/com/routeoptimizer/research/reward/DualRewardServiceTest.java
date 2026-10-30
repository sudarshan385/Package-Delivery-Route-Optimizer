package com.routeoptimizer.research.reward;

import com.routeoptimizer.research.reward.config.DualRewardProperties;
import com.routeoptimizer.research.reward.domain.DualRewardResult;
import com.routeoptimizer.research.reward.service.CmrCalculator;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.reward.service.EditDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DualRewardServiceTest {

    private DualRewardService service;
    private DualRewardProperties properties;

    @BeforeEach
    public void setUp() {
        properties = new DualRewardProperties();
        properties.setCmrWeight(0.6);
        properties.setEdWeight(0.4);
        properties.setMaxReward(100.0);
        
        CmrCalculator cmrCalculator = new CmrCalculator();
        EditDistanceCalculator editDistanceCalculator = new EditDistanceCalculator();
        
        service = new DualRewardService(cmrCalculator, editDistanceCalculator, properties);
    }

    @Test
    public void testDualRewardContainsBothCmrAndEd() {
        List<Long> predicted = Arrays.asList(1L, 2L, 4L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        
        DualRewardResult result = service.calculateReward(predicted, reference);
        
        assertNotNull(result);
        
        // CMR: 2 matches out of 3 = 0.666...
        assertEquals(2.0 / 3.0, result.getCmrScore(), 0.001);
        
        // ED: 1 substitution = 1
        assertEquals(1, result.getEditDistance());
        
        // ED Score: 1.0 - (1 / 3) = 0.666...
        assertEquals(2.0 / 3.0, result.getEdScore(), 0.001);
        
        // Combined: (0.6 * 0.666...) + (0.4 * 0.666...) = 0.666... * 100 = 66.666...
        assertEquals(66.666, result.getCombinedReward(), 0.01);
    }

    @Test
    public void testCombinedRewardUsesExplicitConfigurableParameters() {
        properties.setCmrWeight(1.0);
        properties.setEdWeight(0.0);
        properties.setMaxReward(50.0);
        
        List<Long> predicted = Arrays.asList(1L, 2L, 4L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        
        DualRewardResult result = service.calculateReward(predicted, reference);
        
        // Only CMR should matter: CMR is 2/3. Max reward is 50.
        // Result = (1.0 * 2/3 + 0) * 50 = 33.333
        assertEquals(33.333, result.getCombinedReward(), 0.01);
    }

    @Test
    public void testNoFabricatedReferenceRouteAllowed() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.calculateReward(predicted, null); // reference must be explicitly provided
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.calculateReward(null, predicted);
        });
    }

    @Test
    public void testPerfectMatchMaxReward() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        
        DualRewardResult result = service.calculateReward(predicted, reference);
        
        assertEquals(1.0, result.getCmrScore(), 0.001);
        assertEquals(0, result.getEditDistance());
        assertEquals(1.0, result.getEdScore(), 0.001);
        
        // Both weights sum to 1.0. Max reward is 100.
        assertEquals(100.0, result.getCombinedReward(), 0.001);
    }
}
