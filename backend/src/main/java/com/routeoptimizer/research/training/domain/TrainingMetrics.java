package com.routeoptimizer.research.training.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrainingMetrics {
    int episodeNumber;
    double episodeReward;
    int episodeSteps;
    boolean terminalStatus;
    double averageReward;
    double tdErrorStatistics;
    int replaySamples;
    int trainingUpdates;
    double epsilonValue;
    int targetNetworkUpdateCount;
}
