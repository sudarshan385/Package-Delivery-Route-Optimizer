package com.routeoptimizer.research.aper;

import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.service.PriorityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PriorityCalculatorTest {

    private PriorityCalculator calculator;
    private AperProperties properties;

    @BeforeEach
    public void setUp() {
        properties = new AperProperties();
        properties.setTdErrorWeight(0.6);
        properties.setComplexityWeight(0.4);
        properties.setAlpha(1.0); // simple linear for testing
        calculator = new PriorityCalculator(properties);
    }

    @Test
    public void testPriorityCalculationIsDeterministic() {
        double tdError = 0.5;
        double complexity = 0.8;

        double p1 = calculator.calculatePriority(tdError, complexity);
        double p2 = calculator.calculatePriority(tdError, complexity);

        assertEquals(p1, p2, "Priority calculation must be deterministic");
    }

    @Test
    public void testHigherImpactTransitionReceivesHigherPriority() {
        double pLow = calculator.calculatePriority(0.1, 0.2);
        double pHigh = calculator.calculatePriority(0.9, 0.8);

        assertTrue(pHigh > pLow, "Higher TD error and complexity should yield higher priority");
    }
    
    @Test
    public void testNegativeTdErrorIsHandledAsAbsoluteMagnitude() {
        double pPositive = calculator.calculatePriority(0.5, 0.5);
        double pNegative = calculator.calculatePriority(-0.5, 0.5);
        
        assertEquals(pPositive, pNegative, 0.0001, "TD error magnitude should be used, regardless of sign");
    }
}
