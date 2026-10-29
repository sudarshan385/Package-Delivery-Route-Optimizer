package com.routeoptimizer.research.reward;

import com.routeoptimizer.research.reward.service.CmrCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CmrCalculatorTest {

    private CmrCalculator calculator;

    @BeforeEach
    public void setUp() {
        calculator = new CmrCalculator();
    }

    @Test
    public void testIdenticalPredictedReferenceRoutePerfectCmr() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(1.0, calculator.calculateCmr(predicted, reference));
    }

    @Test
    public void testCompletelyDifferentRoute() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Arrays.asList(4L, 5L, 6L);
        assertEquals(0.0, calculator.calculateCmr(predicted, reference));
    }

    @Test
    public void testPartialRouteAlignment() {
        List<Long> predicted = Arrays.asList(1L, 5L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        // 2 out of 3 match
        assertEquals(2.0 / 3.0, calculator.calculateCmr(predicted, reference), 0.001);
    }

    @Test
    public void testEmptyPredictedRoute() {
        List<Long> predicted = Collections.emptyList();
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(0.0, calculator.calculateCmr(predicted, reference));
    }

    @Test
    public void testEmptyReferenceRoute() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Collections.emptyList();
        assertEquals(0.0, calculator.calculateCmr(predicted, reference));
    }

    @Test
    public void testBothEmpty() {
        List<Long> predicted = Collections.emptyList();
        List<Long> reference = Collections.emptyList();
        assertEquals(1.0, calculator.calculateCmr(predicted, reference));
    }

    @Test
    public void testDifferentLengths() {
        List<Long> predicted = Arrays.asList(1L, 2L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L, 4L);
        // 2 matches out of max length 4
        assertEquals(0.5, calculator.calculateCmr(predicted, reference), 0.001);
    }
}
