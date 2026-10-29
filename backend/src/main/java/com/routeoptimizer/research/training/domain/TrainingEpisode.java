package com.routeoptimizer.research.training.domain;

import com.routeoptimizer.research.aper.service.AperBuffer;
import com.routeoptimizer.research.ddqn.domain.Transition;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.training.config.TrainingConfig;
import com.routeoptimizer.research.training.environment.RouteTrainingEnvironment;

public class TrainingEpisode {

    private final TrainingConfig config;
    private final RouteTrainingEnvironment env;
    private final DDQNService ddqnService;
    private final AperBuffer aperBuffer;
    private final int episodeNumber;

    public TrainingEpisode(
            TrainingConfig config,
            RouteTrainingEnvironment env,
            DDQNService ddqnService,
            AperBuffer aperBuffer,
            int episodeNumber) {
        this.config = config;
        this.env = env;
        this.ddqnService = ddqnService;
        this.aperBuffer = aperBuffer;
        this.episodeNumber = episodeNumber;
    }

    public TrainingMetrics run() {
        int steps = 0;
        double totalReward = 0;
        int updates = 0;
        double tdErrorSum = 0;

        while (!env.isTerminal() && steps < config.getMaxStepsPerEpisode()) {
            RouteDecisionState state = env.getCurrentState();
            RouteAction action;
            try {
                action = ddqnService.selectAction(state);
            } catch (Exception e) {
                // No valid action available
                break;
            }

            env.step(action);
            steps++;

            RouteDecisionState nextState = env.getCurrentState();
            boolean done = env.isTerminal();
            double stepReward = 0;
            
            if (done) {
                stepReward = env.calculateReward().getCombinedReward();
            }
            
            totalReward += stepReward;

            Transition transition = Transition.builder()
                    .state(state)
                    .action(action)
                    .reward(stepReward)
                    .nextState(nextState)
                    .done(done)
                    .build();

            double initialTdError = ddqnService.calculateTdError(transition);
            tdErrorSum += initialTdError;

            // Store in APER
            aperBuffer.store(transition, initialTdError, 1.0); 

            // Train if threshold met
            if (aperBuffer.getHighImpactSize() + aperBuffer.getLowImpactSize() >= config.getMinReplaySizeBeforeTraining()) {
                var batch = aperBuffer.sample(config.getBatchSize());
                for (var b : batch) {
                    double newTdError = ddqnService.train(b.getTransition());
                    aperBuffer.updateTdError(b.getId(), newTdError);
                    updates++;
                }
            }
        }

        return TrainingMetrics.builder()
                .episodeNumber(episodeNumber)
                .episodeSteps(steps)
                .episodeReward(totalReward)
                .terminalStatus(env.isTerminal())
                .averageReward(steps > 0 ? totalReward / steps : 0)
                .tdErrorStatistics(steps > 0 ? tdErrorSum / steps : 0)
                .replaySamples(aperBuffer.getHighImpactSize() + aperBuffer.getLowImpactSize())
                .trainingUpdates(updates)
                .epsilonValue(ddqnService.getCurrentEpsilon())
                .targetNetworkUpdateCount(0) // Internal to DDQNService
                .build();
    }
}
