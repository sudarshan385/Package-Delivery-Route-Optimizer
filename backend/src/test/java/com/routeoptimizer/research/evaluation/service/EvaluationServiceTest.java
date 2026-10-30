package com.routeoptimizer.research.evaluation.service;

import com.routeoptimizer.research.ddqn.network.QNetwork;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.reward.domain.DualRewardResult;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EvaluationServiceTest {

    private EvaluationService evaluationService;
    private TsmTwService tsmTwService;
    private StateRepresentationService stateRepresentationService;
    private DDQNService ddqnService;
    private DualRewardService dualRewardService;
    private QNetwork onlineNetwork;

    @BeforeEach
    void setUp() {
        tsmTwService = Mockito.mock(TsmTwService.class);
        stateRepresentationService = Mockito.mock(StateRepresentationService.class);
        ddqnService = Mockito.mock(DDQNService.class);
        dualRewardService = Mockito.mock(DualRewardService.class);
        onlineNetwork = Mockito.mock(QNetwork.class);

        when(ddqnService.getOnlineNetwork()).thenReturn(onlineNetwork);
        when(tsmTwService.generateCandidates(any(TSMTWRequest.class)))
                .thenReturn(TSMTWResult.builder().feasible(true).candidateRoutes(Collections.emptyList()).build());

        when(stateRepresentationService.buildState(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(com.routeoptimizer.research.state.domain.RouteDecisionState.builder()
                        .validActionMask(com.routeoptimizer.research.state.domain.ValidActionMask.builder().build())
                        .build());

        evaluationService = new EvaluationService(tsmTwService, stateRepresentationService, ddqnService, dualRewardService);
    }

    @Test
    void testEmptyDataset() {
        EvaluationMetrics metrics = evaluationService.evaluate(Collections.emptyList());
        assertEquals(0.0, metrics.getAverageEpisodeReward());
    }

    @Test
    void testEvaluationExecution() {
        TrainingInput input = TrainingInput.builder()
                .driverId(1L)
                .initialLocationId(10L)
                .currentTime(OffsetDateTime.now())
                .tasks(List.of(new com.routeoptimizer.research.tsmtw.domain.TSMTWTask()))
                .referenceRoute(List.of(1L))
                .build();

        // With empty tasks, it's terminal immediately
        // Just checking it runs without crashing
        when(dualRewardService.calculateReward(any(), any()))
                .thenReturn(DualRewardResult.builder().cmrScore(1.0).edScore(2.0).combinedReward(3.0).build());

        EvaluationMetrics metrics = evaluationService.evaluate(List.of(input));
        assertEquals(3.0, metrics.getAverageEpisodeReward()); 
        // 0.0 because with empty tasks, environment might just return 0 reward if it throws exception or doesn't execute
    }
}
