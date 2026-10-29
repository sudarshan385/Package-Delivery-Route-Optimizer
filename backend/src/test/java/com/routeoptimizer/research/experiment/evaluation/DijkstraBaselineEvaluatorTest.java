package com.routeoptimizer.research.experiment.evaluation;

import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.experiment.domain.ComparisonResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DijkstraBaselineEvaluatorTest {

    @Test
    void testCompare() {
        DijkstraBaselineEvaluator evaluator = new DijkstraBaselineEvaluator();
        EvaluationMetrics metrics = EvaluationMetrics.builder()
                .averageCmr(0.9)
                .averageEd(1.5)
                .averageEpisodeReward(100.0)
                .averageEpisodeLength(15.0)
                .build();
                
        ComparisonResult result = evaluator.compare(Collections.emptyList(), metrics);
        
        assertEquals(0.9, result.getDdqnCmr());
        assertNull(result.getDijkstraCmr());
        assertEquals(1.5, result.getDdqnEd());
        assertNull(result.getDijkstraEd());
        assertEquals(100.0, result.getDdqnReward());
        assertNull(result.getDijkstraComparableMetric());
        assertEquals(15.0, result.getDdqnEpisodeLength());
        assertNull(result.getDijkstraRouteLength());
    }
}
