package com.routeoptimizer.research.dataset.amazon;

import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.experiment.domain.ExperimentConfiguration;
import com.routeoptimizer.research.experiment.domain.ExperimentResult;
import com.routeoptimizer.research.experiment.runner.DDQNPPDRPExperimentRunner;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.service.DDQNPPDRPTrainingService;
import com.routeoptimizer.research.evaluation.service.EvaluationService;
import com.routeoptimizer.research.experiment.evaluation.DijkstraBaselineEvaluator;
import com.routeoptimizer.research.ddqn.config.DDQNProperties;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.service.AperBuffer;
import com.routeoptimizer.research.aper.service.PriorityCalculator;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.reward.service.DualRewardService;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class AmazonDDQNExperimentTest {

    @Autowired
    private DDQNPPDRPExperimentRunner runner;

    @Autowired
    private TsmTwService tsmTwService;

    @Autowired
    private StateRepresentationService stateRepresentationService;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private DualRewardService rewardService;

    private java.util.concurrent.atomic.AtomicInteger syncTargetNetworkCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private java.util.concurrent.atomic.AtomicInteger aperSampleCounter = new java.util.concurrent.atomic.AtomicInteger(0);


    private DDQNPPDRPTrainingService createFreshTrainingService(long seed) {
        DDQNService ddqnService = org.mockito.Mockito.spy(new DDQNService(new DDQNProperties(), new Random(seed)));
        org.mockito.Mockito.doAnswer(inv -> {
            syncTargetNetworkCounter.incrementAndGet();
            return inv.callRealMethod();
        }).when(ddqnService).syncTargetNetwork();

        AperProperties aperProps = new AperProperties();
        AperBuffer aperBuffer = org.mockito.Mockito.spy(new AperBuffer(aperProps, new PriorityCalculator(aperProps), new Random(seed)));
        org.mockito.Mockito.doAnswer(inv -> {
            aperSampleCounter.incrementAndGet();
            return inv.callRealMethod();
        }).when(aperBuffer).sample(org.mockito.Mockito.anyInt());

        return new DDQNPPDRPTrainingService(tsmTwService, stateRepresentationService, ddqnService, rewardService, aperBuffer);
    }

    private ObjectNode serializeResult(ExperimentResult result, int trainSize, int valSize, int testSize, int totalStops, int totalPackages) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("seed", result.getConfiguration().getRandomSeed());

        ObjectNode dataset = root.putObject("dataset");
        dataset.put("totalRoutes", trainSize + valSize + testSize);
        dataset.put("trainingRoutes", trainSize);
        dataset.put("validationRoutes", valSize);
        dataset.put("testRoutes", testSize);
        dataset.put("totalStops", totalStops);
        dataset.put("totalPackages", totalPackages);

        ObjectNode configNode = root.putObject("configuration");
        configNode.put("trainingEpisodes", result.getConfiguration().getTrainingEpisodes());
        configNode.put("maxSteps", result.getConfiguration().getMaxStepsPerEpisode());
        configNode.put("learningRate", result.getConfiguration().getLearningRate());
        configNode.put("discountFactor", result.getConfiguration().getDiscountFactor());
        configNode.put("epsilonStart", result.getConfiguration().getEpsilonStart());
        configNode.put("epsilonMinimum", result.getConfiguration().getEpsilonMinimum());
        configNode.put("epsilonDecay", result.getConfiguration().getEpsilonDecay());
        configNode.put("batchSize", result.getConfiguration().getBatchSize());
        configNode.put("minimumReplaySize", result.getConfiguration().getMinimumReplaySize());
        configNode.put("targetUpdateInterval", result.getConfiguration().getTargetUpdateInterval());

        ObjectNode training = root.putObject("training");
        training.put("episodes", result.getConfiguration().getTrainingEpisodes());
        training.put("updates", result.getMetrics().getTotalTrainingUpdates());
        training.put("finalEpsilon", result.getMetrics().getFinalEpsilon());
        training.put("averageReward", result.getMetrics().getAverageTrainingReward());
        training.put("bestReward", result.getMetrics().getBestEpisodeReward());
        training.put("averageEpisodeSteps", result.getMetrics().getAverageEpisodeLength());

        ObjectNode testNode = root.putObject("test");
        testNode.put("routes", testSize);
        testNode.put("cmr", result.getMetrics().getEvaluationCmr());
        testNode.put("ed", result.getMetrics().getEvaluationEd());
        testNode.put("averageReward", result.getMetrics().getEvaluationReward());

        ObjectNode dijkstra = root.putObject("dijkstra");
        dijkstra.put("available", false);
        dijkstra.put("reason", "The Amazon Last Mile dataset used for this experiment does not provide an authoritative road-network graph or road-travel-time weights required for a legitimate Dijkstra comparison.");

        return root;
    }

    @Test
    void testDDQNExperimentReproducibilityAndMetrics() {
        String datasetPath = System.getProperty("amazon.dataset.path", "S:/MAJOR PROJECT/research-data/");
        Path basePath = Paths.get(datasetPath);
        Assumptions.assumeTrue(Files.exists(basePath.resolve("route_data.json")), "Dataset not found, skipping experiment.");

        long fixedSeed = 42L;
        
        ExperimentConfiguration config = ExperimentConfiguration.builder()
                .datasetPath(datasetPath)
                .randomSeed(fixedSeed)
                .trainingEpisodes(2) // minimal episodes for controlled execution
                .maxStepsPerEpisode(10) // minimal steps to prevent hanging
                .learningRate(0.001)
                .discountFactor(0.99)
                .epsilonStart(1.0)
                .epsilonMinimum(0.01)
                .epsilonDecay(0.995)
                .batchSize(32)
                .minimumReplaySize(10)
                .targetUpdateInterval(10)
                .build();

        // RUN 1
        AmazonDatasetAdapterProvider provider1 = new AmazonDatasetAdapterProvider(20, fixedSeed);
        DDQNPPDRPExperimentRunner runner1 = new DDQNPPDRPExperimentRunner(
                provider1,
                createFreshTrainingService(fixedSeed),
                (EvaluationService) ReflectionTestUtils.getField(runner, "evaluationService"),
                (DijkstraBaselineEvaluator) ReflectionTestUtils.getField(runner, "dijkstraBaselineEvaluator")
        );
        ExperimentResult result1 = runner1.runExperiment(config);
        
        // RUN 2
        AmazonDatasetAdapterProvider provider2 = new AmazonDatasetAdapterProvider(20, fixedSeed);
        DDQNPPDRPExperimentRunner runner2 = new DDQNPPDRPExperimentRunner(
                provider2,
                createFreshTrainingService(fixedSeed),
                (EvaluationService) ReflectionTestUtils.getField(runner, "evaluationService"),
                (DijkstraBaselineEvaluator) ReflectionTestUtils.getField(runner, "dijkstraBaselineEvaluator")
        );
        ExperimentResult result2 = runner2.runExperiment(config);

        // 1. REPRODUCIBILITY
        assertEquals(result1.getMetrics().getAverageTrainingReward(), result2.getMetrics().getAverageTrainingReward(), 0.0001, "Training reward differs");
        assertEquals(result1.getMetrics().getEvaluationCmr(), result2.getMetrics().getEvaluationCmr(), 0.0001, "CMR differs");
        assertEquals(result1.getMetrics().getEvaluationEd(), result2.getMetrics().getEvaluationEd(), 0.0001, "ED differs");
        assertEquals(result1.getMetrics().getAverageEpisodeLength(), result2.getMetrics().getAverageEpisodeLength(), 0.0001, "Episode length differs");
        assertEquals(result1.getMetrics().getTotalTrainingUpdates(), result2.getMetrics().getTotalTrainingUpdates(), "Update count differs");
        assertEquals(result1.getMetrics().getFinalEpsilon(), result2.getMetrics().getFinalEpsilon(), 0.0001, "Epsilon differs");
        
        // 2. METRIC VALIDATION & REWARD
        assertTrue(result1.getMetrics().getEvaluationCmr() >= 0 && result1.getMetrics().getEvaluationCmr() <= 1.0, "CMR mathematically invalid");
        assertTrue(result1.getMetrics().getEvaluationEd() >= 0, "ED mathematically invalid");

        // 3. TEST-SET ISOLATION
        List<TrainingInput> train = provider1.getEpisodes(DatasetSplit.TRAIN);
        List<TrainingInput> val = provider1.getEpisodes(DatasetSplit.VALIDATION);
        List<TrainingInput> test = provider1.getEpisodes(DatasetSplit.TEST);
        
        System.out.println("Train routes: " + train.size());
        System.out.println("Val routes: " + val.size());
        System.out.println("Test routes: " + test.size());
        
        for (TrainingInput t : test) {
            assertTrue(train.stream().noneMatch(tr -> tr == t), "Test route leaked into training");
            assertTrue(val.stream().noneMatch(vr -> vr == t), "Test route leaked into validation");
        }
        for (TrainingInput v : val) {
            assertTrue(train.stream().noneMatch(tr -> tr == v), "Validation route leaked into training");
        }
        
        // 4. TRAINING EVIDENCE
        assertTrue(result1.getMetrics().getTotalTrainingUpdates() > 0, "No network updates occurred");
        assertTrue(result1.getMetrics().getFinalEpsilon() < 1.0, "Epsilon did not decay");
        assertTrue(syncTargetNetworkCounter.get() > 0, "Target network synchronization did not occur");
        assertTrue(aperSampleCounter.get() > 0, "APER sampling did not occur");

        org.mockito.ArgumentCaptor<List<Long>> predictedCaptor = org.mockito.ArgumentCaptor.captor();
        org.mockito.ArgumentCaptor<List<Long>> referenceCaptor = org.mockito.ArgumentCaptor.captor();
        org.mockito.Mockito.verify(rewardService, org.mockito.Mockito.atLeastOnce())
                .calculateReward(predictedCaptor.capture(), referenceCaptor.capture());
        
        List<List<Long>> references = referenceCaptor.getAllValues();
        assertFalse(references.isEmpty());
        boolean matchFound = false;
        for (List<Long> ref : references) {
            for (TrainingInput t : test) {
                if (t.getReferenceRoute() != null && t.getReferenceRoute().equals(ref)) {
                    matchFound = true;
                    break;
                }
            }
        }
        assertTrue(matchFound, "CMR and ED must use the actual reference route");

        // 5. EXPERIMENT RESULT MODEL & DIJKSTRA Null
        assertNull(result1.getComparison().getDijkstraComparableMetric(), "Unavailable Dijkstra metric must remain null, not zero");
        assertNull(result1.getComparison().getDijkstraRouteLength(), "Unavailable Dijkstra length must remain null, not zero");
        
        // 6. DETERMINISTIC OUTPUT IN RESULT
        assertEquals(fixedSeed, result1.getConfiguration().getRandomSeed());

        // PHASE 6: Result Export & Serialization
        int totalStops = 0;
        int totalPackages = 0;
        for (TrainingInput in : Stream.concat(Stream.concat(train.stream(), val.stream()), test.stream()).toList()) {
            totalStops += in.getTasks().size();
            totalPackages += in.getTasks().stream().filter(t -> "DELIVERY".equals(t.getTaskType().name())).count();
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectNode json1 = serializeResult(result1, train.size(), val.size(), test.size(), totalStops, totalPackages);
        ObjectNode json2 = serializeResult(result2, train.size(), val.size(), test.size(), totalStops, totalPackages);

        String jsonString1;
        String jsonString2;
        try {
            jsonString1 = mapper.writeValueAsString(json1);
            jsonString2 = mapper.writeValueAsString(json2);
            
            System.out.println("==================================================");
            System.out.println("FINAL EXPERIMENT RESULT");
            System.out.println("==================================================");
            System.out.println(jsonString1);
            System.out.println("==================================================");

            Path exportPath = Paths.get("experiment_result.json");
            Files.writeString(exportPath, jsonString1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize or save results", e);
        }

        // Phase 6 Validation Tests
        assertTrue(json1.has("training"));
        assertTrue(json1.get("training").has("averageReward"));
        assertTrue(json1.get("test").has("cmr"));
        assertTrue(json1.get("test").has("ed"));
        
        assertNotNull(json1.get("training").get("averageReward").asDouble());
        assertNotNull(json1.get("test").get("cmr").asDouble());
        assertNotNull(json1.get("test").get("ed").asDouble());
        
        assertFalse(json1.get("dijkstra").get("available").asBoolean());
        assertEquals("The Amazon Last Mile dataset used for this experiment does not provide an authoritative road-network graph or road-travel-time weights required for a legitimate Dijkstra comparison.", json1.get("dijkstra").get("reason").asText());

        assertEquals(jsonString1, jsonString2, "Two identical runs must produce identical serialized results");
    }
}
