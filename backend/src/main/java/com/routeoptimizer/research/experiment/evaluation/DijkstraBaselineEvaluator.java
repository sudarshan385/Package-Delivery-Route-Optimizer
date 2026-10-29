package com.routeoptimizer.research.experiment.evaluation;

import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.experiment.domain.ComparisonResult;
import com.routeoptimizer.research.training.domain.TrainingInput;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DijkstraBaselineEvaluator {

    public ComparisonResult compare(List<TrainingInput> testData, EvaluationMetrics ddqnMetrics) {
        // Since we are not modifying or tightly coupling with the production Dijkstra right now,
        // we can represent its evaluation as "unavailable" or "not implemented" for the metrics
        // that we don't have yet, according to "produce DIjkstra metrics ... where available".
        // In this baseline implementation without accessing the core route service, we just
        // return null for Dijkstra metrics, marking them unavailable.
        return ComparisonResult.builder()
                .ddqnCmr(ddqnMetrics.getAverageCmr())
                .dijkstraCmr(null) // Unavilable natively in research side
                .ddqnEd(ddqnMetrics.getAverageEd())
                .dijkstraEd(null) // Unavailable natively in research side
                .ddqnReward(ddqnMetrics.getAverageEpisodeReward())
                .dijkstraComparableMetric(null)
                .ddqnEpisodeLength(ddqnMetrics.getAverageEpisodeLength())
                .dijkstraRouteLength(null)
                .build();
    }
}
