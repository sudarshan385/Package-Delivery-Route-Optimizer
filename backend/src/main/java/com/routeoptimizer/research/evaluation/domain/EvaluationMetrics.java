package com.routeoptimizer.research.evaluation.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EvaluationMetrics {
    double averageEpisodeReward;
    double averageCmr;
    double averageEd;
    double averageEpisodeLength;
    double validActionRate;
}
