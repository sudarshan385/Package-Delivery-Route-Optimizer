package com.routeoptimizer.research.experiment.runner;

import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.provider.ResearchDatasetProvider;
import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.evaluation.service.EvaluationService;
import com.routeoptimizer.research.experiment.domain.ComparisonResult;
import com.routeoptimizer.research.experiment.domain.ExperimentConfiguration;
import com.routeoptimizer.research.experiment.domain.ExperimentResult;
import com.routeoptimizer.research.experiment.evaluation.DijkstraBaselineEvaluator;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.domain.TrainingMetrics;
import com.routeoptimizer.research.training.service.DDQNPPDRPTrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DDQNPPDRPExperimentRunnerTest {

    private ResearchDatasetProvider datasetProvider;
    private DDQNPPDRPTrainingService trainingService;
    private EvaluationService evaluationService;
    private DijkstraBaselineEvaluator dijkstraBaselineEvaluator;
    private DDQNPPDRPExperimentRunner runner;

    @BeforeEach
    void setUp() {
        datasetProvider = Mockito.mock(ResearchDatasetProvider.class);
        trainingService = Mockito.mock(DDQNPPDRPTrainingService.class);
        evaluationService = Mockito.mock(EvaluationService.class);
        dijkstraBaselineEvaluator = Mockito.mock(DijkstraBaselineEvaluator.class);

        runner = new DDQNPPDRPExperimentRunner(datasetProvider, trainingService, evaluationService, dijkstraBaselineEvaluator);
    }

    @Test
    void testSuccessfulExperimentRun() {
        ExperimentConfiguration config = ExperimentConfiguration.builder()
                .datasetPath("dummy.csv")
                .randomSeed(42L)
                .trainingEpisodes(10)
                .maxStepsPerEpisode(20)
                .learningRate(0.001)
                .discountFactor(0.99)
                .epsilonStart(1.0)
                .epsilonMinimum(0.01)
                .epsilonDecay(0.995)
                .batchSize(32)
                .minimumReplaySize(100)
                .targetUpdateInterval(10)
                .build();

        TrainingInput input = TrainingInput.builder().build();
        when(datasetProvider.getEpisodes(DatasetSplit.TRAIN)).thenReturn(List.of(input));
        when(datasetProvider.getEpisodes(DatasetSplit.TEST)).thenReturn(List.of(input));

        TrainingMetrics tm = TrainingMetrics.builder()
                .episodeNumber(1)
                .episodeReward(100.0)
                .episodeSteps(10)
                .epsilonValue(0.5)
                .tdErrorStatistics(0.1)
                .trainingUpdates(5)
                .terminalStatus(true)
                .build();
        when(trainingService.train(any(), any())).thenReturn(List.of(tm));

        EvaluationMetrics evalMetrics = EvaluationMetrics.builder()
                .averageEpisodeReward(50.0)
                .averageCmr(0.9)
                .averageEd(1.1)
                .averageEpisodeLength(10.0)
                .build();
        when(evaluationService.evaluate(any())).thenReturn(evalMetrics);

        ComparisonResult comparison = ComparisonResult.builder()
                .ddqnReward(50.0)
                .build();
        when(dijkstraBaselineEvaluator.compare(any(), any())).thenReturn(comparison);

        ExperimentResult result = runner.runExperiment(config);

        assertNotNull(result);
        assertEquals(1, result.getMetrics().getTotalEpisodes());
        assertEquals(100.0, result.getMetrics().getAverageTrainingReward());
        assertEquals(10.0, result.getMetrics().getAverageEpisodeLength());
        assertEquals(5, result.getMetrics().getTotalTrainingUpdates());
        assertEquals(50.0, result.getMetrics().getEvaluationReward());
    }

    @Test
    void testEmptyDatasetThrowsException() {
        ExperimentConfiguration config = ExperimentConfiguration.builder()
                .datasetPath("dummy.csv")
                .randomSeed(42L)
                .trainingEpisodes(10)
                .maxStepsPerEpisode(20)
                .learningRate(0.001)
                .discountFactor(0.99)
                .epsilonStart(1.0)
                .epsilonMinimum(0.01)
                .epsilonDecay(0.995)
                .batchSize(32)
                .minimumReplaySize(100)
                .targetUpdateInterval(10)
                .build();

        when(datasetProvider.getEpisodes(DatasetSplit.TRAIN)).thenReturn(Collections.emptyList());
        
        assertThrows(IllegalArgumentException.class, () -> runner.runExperiment(config));
    }
}
