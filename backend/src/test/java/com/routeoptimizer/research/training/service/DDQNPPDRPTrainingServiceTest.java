package com.routeoptimizer.research.training.service;

import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.service.AperBuffer;
import com.routeoptimizer.research.aper.service.PriorityCalculator;
import com.routeoptimizer.research.ddqn.config.DDQNProperties;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.reward.config.DualRewardProperties;
import com.routeoptimizer.research.reward.service.CmrCalculator;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.reward.service.EditDistanceCalculator;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.config.TrainingConfig;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.domain.TrainingMetrics;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DDQNPPDRPTrainingServiceTest {

    private DDQNPPDRPTrainingService trainingService;
    private LocationEdgeRepository locationEdgeRepository;
    private DDQNService ddqnService;
    private AperBuffer aperBuffer;

    @BeforeEach
    void setUp() {
        locationEdgeRepository = Mockito.mock(LocationEdgeRepository.class);
        TsmTwService tsmTwService = new TsmTwService(locationEdgeRepository);
        StateRepresentationService stateRepresentationService = new StateRepresentationService();
        
        DDQNProperties ddqnProperties = new DDQNProperties();
        ddqnProperties.setEpsilon(1.0);
        ddqnProperties.setMinEpsilon(0.01);
        ddqnProperties.setEpsilonDecay(0.995);
        ddqnProperties.setGamma(0.99);
        ddqnProperties.setLearningRate(0.001);
        ddqnProperties.setTargetUpdateFrequency(10);
        ddqnService = new DDQNService(ddqnProperties, new Random(42));
        
        DualRewardProperties rewardProperties = new DualRewardProperties();
        rewardProperties.setCmrWeight(0.5);
        rewardProperties.setEdWeight(0.5);
        rewardProperties.setMaxReward(10.0);
        DualRewardService rewardService = new DualRewardService(
                new CmrCalculator(), new EditDistanceCalculator(), rewardProperties);
                
        AperProperties aperProperties = new AperProperties();
        aperProperties.setHighImpactThreshold(0.5);
        aperProperties.setHighImpactSampleRatio(0.7);
        aperProperties.setCapacity(1000);
        aperBuffer = new AperBuffer(aperProperties, new PriorityCalculator(aperProperties), new Random(42));

        trainingService = new DDQNPPDRPTrainingService(
                tsmTwService, stateRepresentationService, ddqnService, rewardService, aperBuffer
        );
    }

    @Test
    void testTrainingInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> trainingService.train(null, TrainingConfig.builder().build()));
        
        TrainingInput noTasks = TrainingInput.builder().tasks(List.of()).build();
        assertThrows(IllegalArgumentException.class, () -> trainingService.train(noTasks, TrainingConfig.builder().build()));
    }

    @Test
    void testFullTrainingOrchestration() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).locationId(10L).taskType(ResearchTaskType.PICKUP).deliveryId(2L).build();
        TSMTWTask delivery = TSMTWTask.builder().id(2L).locationId(20L).taskType(ResearchTaskType.DELIVERY).deliveryId(2L).build();
        
        TrainingInput input = TrainingInput.builder()
                .driverId(100L)
                .initialLocationId(5L)
                .currentTime(OffsetDateTime.now())
                .tasks(Arrays.asList(pickup, delivery))
                .referenceRoute(Arrays.asList(1L, 2L))
                .build();
                
        TrainingConfig config = TrainingConfig.builder()
                .episodes(2)
                .maxStepsPerEpisode(10)
                .minReplaySizeBeforeTraining(1) // train immediately to verify learning loop
                .batchSize(1)
                .build();

        List<TrainingMetrics> metrics = trainingService.train(input, config);
        
        assertEquals(2, metrics.size());
        
        TrainingMetrics firstEpisode = metrics.get(0);
        assertEquals(1, firstEpisode.getEpisodeNumber());
        assertTrue(firstEpisode.isTerminalStatus());
        assertTrue(firstEpisode.getEpisodeSteps() > 0);
        
        assertTrue(aperBuffer.getHighImpactSize() + aperBuffer.getLowImpactSize() > 0);
        assertTrue(firstEpisode.getTrainingUpdates() > 0);
    }
    
    @Test
    void testMaxStepGuard() {
        TSMTWTask pickup = TSMTWTask.builder().id(1L).locationId(10L).taskType(ResearchTaskType.PICKUP).deliveryId(2L).build();
        TSMTWTask delivery = TSMTWTask.builder().id(2L).locationId(20L).taskType(ResearchTaskType.DELIVERY).deliveryId(2L).build();
        
        TrainingInput input = TrainingInput.builder()
                .driverId(100L)
                .initialLocationId(5L)
                .currentTime(OffsetDateTime.now())
                .tasks(Arrays.asList(pickup, delivery))
                .referenceRoute(Arrays.asList(1L, 2L))
                .build();
                
        TrainingConfig config = TrainingConfig.builder()
                .episodes(1)
                .maxStepsPerEpisode(1) // Too short to finish
                .build();

        List<TrainingMetrics> metrics = trainingService.train(input, config);
        assertEquals(1, metrics.size());
        assertFalse(metrics.get(0).isTerminalStatus());
        assertEquals(1, metrics.get(0).getEpisodeSteps());
    }
}
