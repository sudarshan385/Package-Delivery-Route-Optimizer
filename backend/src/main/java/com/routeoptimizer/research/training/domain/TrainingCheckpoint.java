package com.routeoptimizer.research.training.domain;

import com.routeoptimizer.research.training.config.TrainingConfig;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrainingCheckpoint {
    int episode;
    TrainingConfig configuration;
    String networkVersionMetadata;
    TrainingMetrics metrics;
}
