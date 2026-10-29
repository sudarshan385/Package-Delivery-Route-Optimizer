package com.routeoptimizer.research.experiment.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExperimentMetrics {
    int totalEpisodes;
    double averageTrainingReward;
    double bestEpisodeReward;
    double averageEpisodeLength;
    double averageCmr;
    double averageEd;
    long totalTrainingUpdates;
    double finalEpsilon;
    double evaluationReward;
    double evaluationCmr;
    double evaluationEd;
    double evaluationEpisodeLength;
}
