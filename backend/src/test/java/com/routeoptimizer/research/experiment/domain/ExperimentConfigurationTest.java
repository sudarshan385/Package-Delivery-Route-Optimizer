package com.routeoptimizer.research.experiment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExperimentConfigurationTest {

    @Test
    void testValidConfiguration() {
        ExperimentConfiguration config = ExperimentConfiguration.builder()
                .datasetPath("dummy.csv")
                .randomSeed(42L)
                .trainingEpisodes(100)
                .maxStepsPerEpisode(50)
                .learningRate(0.001)
                .discountFactor(0.99)
                .epsilonStart(1.0)
                .epsilonMinimum(0.01)
                .epsilonDecay(0.995)
                .batchSize(32)
                .minimumReplaySize(100)
                .targetUpdateInterval(10)
                .build();
                
        assertDoesNotThrow(config::validate);
    }

    @Test
    void testInvalidConfiguration() {
        ExperimentConfiguration config = ExperimentConfiguration.builder()
                .datasetPath("") // Invalid
                .randomSeed(42L)
                .trainingEpisodes(-1) // Invalid
                .maxStepsPerEpisode(50)
                .learningRate(2.0) // Invalid
                .discountFactor(0.99)
                .epsilonStart(1.0)
                .epsilonMinimum(0.01)
                .epsilonDecay(0.995)
                .batchSize(32)
                .minimumReplaySize(100)
                .targetUpdateInterval(10)
                .build();
                
        assertThrows(IllegalArgumentException.class, config::validate);
    }
}
