package com.routeoptimizer.research.reward;

import com.routeoptimizer.research.reward.service.EditDistanceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EditDistanceCalculatorTest {

    private EditDistanceCalculator calculator;

    @BeforeEach
    public void setUp() {
        calculator = new EditDistanceCalculator();
    }

    @Test
    public void testIdenticalSequences() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(0, calculator.calculateEditDistance(predicted, reference));
    }

    @Test
    public void testOneInsertion() {
        List<Long> predicted = Arrays.asList(1L, 4L, 2L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(1, calculator.calculateEditDistance(predicted, reference));
    }

    @Test
    public void testOneDeletion() {
        List<Long> predicted = Arrays.asList(1L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(1, calculator.calculateEditDistance(predicted, reference));
    }

    @Test
    public void testOneSubstitution() {
        List<Long> predicted = Arrays.asList(1L, 5L, 3L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(1, calculator.calculateEditDistance(predicted, reference));
    }

    @Test
    public void testMultipleEdits() {
        List<Long> predicted = Arrays.asList(1L, 5L, 4L);
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(2, calculator.calculateEditDistance(predicted, reference));
    }
    
    @Test
    public void testEmptyPredicted() {
        List<Long> predicted = Collections.emptyList();
        List<Long> reference = Arrays.asList(1L, 2L, 3L);
        assertEquals(3, calculator.calculateEditDistance(predicted, reference));
    }
    
    @Test
    public void testEmptyReference() {
        List<Long> predicted = Arrays.asList(1L, 2L, 3L);
        List<Long> reference = Collections.emptyList();
        assertEquals(3, calculator.calculateEditDistance(predicted, reference));
    }
    
    @Test
    public void testDeterministicResults() {
        List<Long> predicted = Arrays.asList(1L, 2L, 4L, 5L);
        List<Long> reference = Arrays.asList(1L, 3L, 4L, 6L);
        
        int result1 = calculator.calculateEditDistance(predicted, reference);
        int result2 = calculator.calculateEditDistance(predicted, reference);
        
        assertEquals(2, result1);
        assertEquals(result1, result2, "ED calculation must be deterministic");
    }
}
