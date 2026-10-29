package com.routeoptimizer.research.experiment.runner;

import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.provider.ResearchDatasetProvider;
import com.routeoptimizer.research.experiment.domain.ComparisonResult;
import com.routeoptimizer.research.experiment.domain.EpisodeMetrics;
import com.routeoptimizer.research.experiment.domain.ExperimentConfiguration;
import com.routeoptimizer.research.experiment.domain.ExperimentMetrics;
import com.routeoptimizer.research.experiment.domain.ExperimentResult;
import com.routeoptimizer.research.experiment.evaluation.DijkstraBaselineEvaluator;
import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.evaluation.service.EvaluationService;
import com.routeoptimizer.research.training.config.TrainingConfig;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.domain.TrainingMetrics;
import com.routeoptimizer.research.training.service.DDQNPPDRPTrainingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DDQNPPDRPExperimentRunner {

    private final ResearchDatasetProvider datasetProvider;
    private final DDQNPPDRPTrainingService trainingService;
    private final EvaluationService evaluationService;
    private final DijkstraBaselineEvaluator dijkstraBaselineEvaluator;

    public DDQNPPDRPExperimentRunner(
            ResearchDatasetProvider datasetProvider,
            DDQNPPDRPTrainingService trainingService,
            EvaluationService evaluationService,
            DijkstraBaselineEvaluator dijkstraBaselineEvaluator) {
        this.datasetProvider = datasetProvider;
        this.trainingService = trainingService;
        this.evaluationService = evaluationService;
        this.dijkstraBaselineEvaluator = dijkstraBaselineEvaluator;
    }

    public ExperimentResult runExperiment(ExperimentConfiguration config) {
        config.validate();

        // Use random seed
        Random random = new Random(config.getRandomSeed());

        // 1. Load Dataset
        datasetProvider.loadDataset(config.getDatasetPath());
        List<TrainingInput> trainData = datasetProvider.getEpisodes(DatasetSplit.TRAIN);
        List<TrainingInput> testData = datasetProvider.getEpisodes(DatasetSplit.TEST);

        if (trainData == null || trainData.isEmpty()) {
            throw new IllegalArgumentException("No training data found in dataset.");
        }
        
        if (testData == null || testData.isEmpty()) {
            throw new IllegalArgumentException("No test data found in dataset.");
        }

        TrainingConfig trainingConfig = TrainingConfig.builder()
                .episodes(config.getTrainingEpisodes())
                .maxStepsPerEpisode(config.getMaxStepsPerEpisode())
                .batchSize(config.getBatchSize())
                .minReplaySizeBeforeTraining(config.getMinimumReplaySize())
                .build();

        List<EpisodeMetrics> trainingEpisodeMetrics = new ArrayList<>();
        double totalTrainingReward = 0;
        double bestReward = Double.NEGATIVE_INFINITY;
        long totalSteps = 0;
        long totalUpdates = 0;
        double finalEpsilon = config.getEpsilonStart();
        double totalCmr = 0;
        double totalEd = 0;

        // 2. Train
        for (TrainingInput input : trainData) {
            List<TrainingMetrics> metricsList = trainingService.train(input, trainingConfig);
            for (TrainingMetrics tm : metricsList) {
                trainingEpisodeMetrics.add(EpisodeMetrics.builder()
                        .episodeNumber(tm.getEpisodeNumber())
                        .totalReward(tm.getEpisodeReward())
                        .episodeSteps(tm.getEpisodeSteps())
                        .epsilon(tm.getEpsilonValue())
                        .tdErrorStatistics(tm.getTdErrorStatistics())
                        .trainingUpdateCount(tm.getTrainingUpdates())
                        .terminalStatus(tm.isTerminalStatus())
                        .build());

                totalTrainingReward += tm.getEpisodeReward();
                if (tm.getEpisodeReward() > bestReward) bestReward = tm.getEpisodeReward();
                totalSteps += tm.getEpisodeSteps();
                totalUpdates += tm.getTrainingUpdates();
                finalEpsilon = tm.getEpsilonValue();
            }
        }

        int totalEpisodes = trainingEpisodeMetrics.size();

        // 3. Evaluate
        EvaluationMetrics evalMetrics = evaluationService.evaluate(testData);

        // 4. Baseline
        ComparisonResult comparison = dijkstraBaselineEvaluator.compare(testData, evalMetrics);

        // 5. Build Result
        ExperimentMetrics expMetrics = ExperimentMetrics.builder()
                .totalEpisodes(totalEpisodes)
                .averageTrainingReward(totalEpisodes > 0 ? totalTrainingReward / totalEpisodes : 0)
                .bestEpisodeReward(bestReward == Double.NEGATIVE_INFINITY ? 0 : bestReward)
                .averageEpisodeLength(totalEpisodes > 0 ? (double) totalSteps / totalEpisodes : 0)
                .averageCmr(0) 
                .averageEd(0)
                .totalTrainingUpdates(totalUpdates)
                .finalEpsilon(finalEpsilon)
                .evaluationReward(evalMetrics.getAverageEpisodeReward())
                .evaluationCmr(evalMetrics.getAverageCmr())
                .evaluationEd(evalMetrics.getAverageEd())
                .evaluationEpisodeLength(evalMetrics.getAverageEpisodeLength())
                .build();

        return ExperimentResult.builder()
                .configuration(config)
                .metrics(expMetrics)
                .episodeMetrics(trainingEpisodeMetrics)
                .comparison(comparison)
                .build();
    }
}
