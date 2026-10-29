package com.routeoptimizer.research.experiment.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExperimentConfiguration {
    String datasetPath;
    long randomSeed;
    int trainingEpisodes;
    int maxStepsPerEpisode;
    double learningRate;
    double discountFactor;
    double epsilonStart;
    double epsilonMinimum;
    double epsilonDecay;
    int batchSize;
    int minimumReplaySize;
    int targetUpdateInterval;

    public void validate() {
        if (datasetPath == null || datasetPath.isEmpty()) throw new IllegalArgumentException("Dataset path is required");
        if (trainingEpisodes <= 0) throw new IllegalArgumentException("Training episodes must be > 0");
        if (maxStepsPerEpisode <= 0) throw new IllegalArgumentException("Max steps per episode must be > 0");
        if (learningRate <= 0 || learningRate > 1) throw new IllegalArgumentException("Learning rate must be in (0, 1]");
        if (discountFactor <= 0 || discountFactor > 1) throw new IllegalArgumentException("Discount factor must be in (0, 1]");
        if (epsilonStart < 0 || epsilonStart > 1) throw new IllegalArgumentException("Epsilon start must be in [0, 1]");
        if (epsilonMinimum < 0 || epsilonMinimum > 1) throw new IllegalArgumentException("Epsilon minimum must be in [0, 1]");
        if (epsilonDecay < 0 || epsilonDecay > 1) throw new IllegalArgumentException("Epsilon decay must be in [0, 1]");
        if (batchSize <= 0) throw new IllegalArgumentException("Batch size must be > 0");
        if (minimumReplaySize <= 0) throw new IllegalArgumentException("Minimum replay size must be > 0");
        if (targetUpdateInterval <= 0) throw new IllegalArgumentException("Target update interval must be > 0");
    }
}
