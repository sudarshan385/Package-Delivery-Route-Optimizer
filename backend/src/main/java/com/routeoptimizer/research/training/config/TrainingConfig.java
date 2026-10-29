package com.routeoptimizer.research.training.config;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrainingConfig {
    @Builder.Default int episodes = 100;
    @Builder.Default int maxStepsPerEpisode = 100;
    @Builder.Default double discountFactor = 0.99;
    @Builder.Default double learningRate = 0.001;
    @Builder.Default double epsilonStart = 1.0;
    @Builder.Default double epsilonMin = 0.01;
    @Builder.Default double epsilonDecay = 0.995;
    @Builder.Default int batchSize = 32;
    @Builder.Default int minReplaySizeBeforeTraining = 100;
    @Builder.Default int targetNetworkUpdateInterval = 100;

    public TrainingConfig(int episodes, int maxStepsPerEpisode, double discountFactor, double learningRate, double epsilonStart, double epsilonMin, double epsilonDecay, int batchSize, int minReplaySizeBeforeTraining, int targetNetworkUpdateInterval) {
        if (episodes < 0) throw new IllegalArgumentException("episodes cannot be negative");
        if (maxStepsPerEpisode <= 0) throw new IllegalArgumentException("maxStepsPerEpisode must be > 0");
        if (discountFactor < 0 || discountFactor > 1.0) throw new IllegalArgumentException("discountFactor must be between 0 and 1");
        if (learningRate <= 0) throw new IllegalArgumentException("learningRate must be > 0");
        if (epsilonStart < 0 || epsilonStart > 1.0) throw new IllegalArgumentException("epsilonStart must be between 0 and 1");
        if (epsilonMin < 0 || epsilonMin > 1.0) throw new IllegalArgumentException("epsilonMin must be between 0 and 1");
        if (epsilonDecay < 0 || epsilonDecay > 1.0) throw new IllegalArgumentException("epsilonDecay must be between 0 and 1");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be > 0");
        if (minReplaySizeBeforeTraining < 0) throw new IllegalArgumentException("minReplaySizeBeforeTraining cannot be negative");
        if (targetNetworkUpdateInterval <= 0) throw new IllegalArgumentException("targetNetworkUpdateInterval must be > 0");

        this.episodes = episodes;
        this.maxStepsPerEpisode = maxStepsPerEpisode;
        this.discountFactor = discountFactor;
        this.learningRate = learningRate;
        this.epsilonStart = epsilonStart;
        this.epsilonMin = epsilonMin;
        this.epsilonDecay = epsilonDecay;
        this.batchSize = batchSize;
        this.minReplaySizeBeforeTraining = minReplaySizeBeforeTraining;
        this.targetNetworkUpdateInterval = targetNetworkUpdateInterval;
    }
}
