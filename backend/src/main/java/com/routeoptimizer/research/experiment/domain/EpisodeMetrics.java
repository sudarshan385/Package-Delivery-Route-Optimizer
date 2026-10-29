package com.routeoptimizer.research.experiment.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EpisodeMetrics {
    int episodeNumber;
    double totalReward;
    int episodeSteps;
    double epsilon;
    double tdErrorStatistics;
    int trainingUpdateCount;
    boolean terminalStatus;
}
