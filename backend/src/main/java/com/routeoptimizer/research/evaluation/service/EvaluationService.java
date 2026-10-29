package com.routeoptimizer.research.evaluation.service;

import com.routeoptimizer.research.ddqn.network.QNetwork;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.evaluation.domain.EvaluationMetrics;
import com.routeoptimizer.research.reward.domain.DualRewardResult;
import com.routeoptimizer.research.reward.service.DualRewardService;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.training.environment.DefaultRouteTrainingEnvironment;
import com.routeoptimizer.research.training.environment.RouteTrainingEnvironment;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {

    private final TsmTwService tsmTwService;
    private final StateRepresentationService stateRepresentationService;
    private final DDQNService ddqnService;
    private final DualRewardService rewardService;

    public EvaluationService(
            TsmTwService tsmTwService,
            StateRepresentationService stateRepresentationService,
            DDQNService ddqnService,
            DualRewardService rewardService) {
        this.tsmTwService = tsmTwService;
        this.stateRepresentationService = stateRepresentationService;
        this.ddqnService = ddqnService;
        this.rewardService = rewardService;
    }

    public EvaluationMetrics evaluate(List<TrainingInput> testEpisodes) {
        if (testEpisodes == null || testEpisodes.isEmpty()) {
            return EvaluationMetrics.builder().build();
        }

        double totalReward = 0;
        double totalCmr = 0;
        double totalEd = 0;
        long totalSteps = 0;
        long validActionsChosen = 0;
        long totalActionsChosen = 0;

        for (TrainingInput input : testEpisodes) {
            TSMTWRequest request = TSMTWRequest.builder()
                    .startLocationId(input.getInitialLocationId())
                    .startTime(input.getCurrentTime())
                    .tasks(input.getTasks())
                    .build();

            TSMTWResult result = tsmTwService.generateCandidates(request);
            var candidateRoutes = result.isFeasible() ? result.getCandidateRoutes() : Collections.<com.routeoptimizer.research.tsmtw.domain.CandidateRoute>emptyList();

            RouteTrainingEnvironment env = new DefaultRouteTrainingEnvironment(
                    input,
                    candidateRoutes,
                    rewardService,
                    stateRepresentationService
            );

            int steps = 0;

            QNetwork network = ddqnService.getOnlineNetwork();

            while (!env.isTerminal() && steps < 100) { 
                RouteDecisionState state = env.getCurrentState();
                List<RouteAction> validActions = getValidActions(state);
                
                if (validActions.isEmpty()) {
                    break;
                }
                
                RouteAction bestAction = getBestAction(state, validActions, network);
                env.step(bestAction);
                
                steps++;
                totalSteps++;
                validActionsChosen++; 
                totalActionsChosen++;
            }

            DualRewardResult finalReward = env.calculateReward();
            totalReward += finalReward.getCombinedReward();
            totalCmr += finalReward.getCmrScore();
            totalEd += finalReward.getEdScore();
        }

        int numEpisodes = testEpisodes.size();
        return EvaluationMetrics.builder()
                .averageEpisodeReward(totalReward / numEpisodes)
                .averageCmr(totalCmr / numEpisodes)
                .averageEd(totalEd / numEpisodes)
                .averageEpisodeLength((double) totalSteps / numEpisodes)
                .validActionRate(totalActionsChosen > 0 ? (double) validActionsChosen / totalActionsChosen : 0)
                .build();
    }

    private RouteAction getBestAction(RouteDecisionState state, List<RouteAction> validActions, QNetwork network) {
        RouteAction bestAction = validActions.get(0);
        double maxQ = Double.NEGATIVE_INFINITY;

        for (RouteAction action : validActions) {
            double qValue = network.evaluate(state, action);
            if (qValue > maxQ) {
                maxQ = qValue;
                bestAction = action;
            }
        }
        return bestAction;
    }

    private List<RouteAction> getValidActions(RouteDecisionState state) {
        ValidActionMask mask = state.getValidActionMask();
        List<RouteAction> actions = new ArrayList<>();

        if (mask == null) {
            return actions;
        }

        if (mask.getValidNextTasks() != null) {
            for (Map.Entry<Long, Boolean> entry : mask.getValidNextTasks().entrySet()) {
                if (entry.getValue()) {
                    actions.add(RouteAction.builder().nextTaskId(entry.getKey()).build());
                }
            }
        }

        if (mask.getValidCandidateRoutes() != null) {
            for (Map.Entry<Long, Boolean> entry : mask.getValidCandidateRoutes().entrySet()) {
                if (entry.getValue()) {
                    actions.add(RouteAction.builder().candidateRouteId(entry.getKey()).build());
                }
            }
        }

        return actions;
    }
}
