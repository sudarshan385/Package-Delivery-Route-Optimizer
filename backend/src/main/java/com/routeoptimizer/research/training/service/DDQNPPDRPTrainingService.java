package com.routeoptimizer.research.training.service;

import com.routeoptimizer.research.aper.service.AperBuffer;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.config.TrainingConfig;
import com.routeoptimizer.research.training.domain.TrainingEpisode;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.domain.TrainingMetrics;
import com.routeoptimizer.research.training.environment.DefaultRouteTrainingEnvironment;
import com.routeoptimizer.research.training.environment.RouteTrainingEnvironment;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DDQNPPDRPTrainingService {

    private final TsmTwService tsmTwService;
    private final StateRepresentationService stateRepresentationService;
    private final DDQNService ddqnService;
    private final DualRewardService rewardService;
    private final AperBuffer aperBuffer;

    @org.springframework.beans.factory.annotation.Autowired
    public DDQNPPDRPTrainingService(
            TsmTwService tsmTwService,
            StateRepresentationService stateRepresentationService,
            DDQNService ddqnService,
            DualRewardService rewardService,
            AperBuffer aperBuffer) {
        this.tsmTwService = tsmTwService;
        this.stateRepresentationService = stateRepresentationService;
        this.ddqnService = ddqnService;
        this.rewardService = rewardService;
        this.aperBuffer = aperBuffer;
    }

    public List<TrainingMetrics> train(TrainingInput input, TrainingConfig config) {
        if (input == null) throw new IllegalArgumentException("Training input cannot be null");
        if (input.getTasks() == null || input.getTasks().isEmpty()) throw new IllegalArgumentException("Tasks cannot be null or empty");

        TSMTWRequest request = TSMTWRequest.builder()
                .startLocationId(input.getInitialLocationId())
                .startTime(input.getCurrentTime())
                .tasks(input.getTasks())
                .build();

        TSMTWResult result = tsmTwService.generateCandidates(request);
        var candidateRoutes = result.isFeasible() ? result.getCandidateRoutes() : Collections.<com.routeoptimizer.research.tsmtw.domain.CandidateRoute>emptyList();

        List<TrainingMetrics> metricsList = new ArrayList<>();

        for (int i = 1; i <= config.getEpisodes(); i++) {
            RouteTrainingEnvironment env = new DefaultRouteTrainingEnvironment(
                    input,
                    candidateRoutes,
                    rewardService,
                    stateRepresentationService
            );

            TrainingEpisode episode = new TrainingEpisode(
                    config,
                    env,
                    ddqnService,
                    aperBuffer,
                    i
            );

            TrainingMetrics metrics = episode.run();
            metricsList.add(metrics);
        }

        return metricsList;
    }
}
