package com.routeoptimizer.research.ddqn;

import com.routeoptimizer.research.ddqn.config.DDQNProperties;
import com.routeoptimizer.research.ddqn.domain.Transition;
import com.routeoptimizer.research.ddqn.exception.NoValidActionException;
import com.routeoptimizer.research.ddqn.network.BaselineDeterministicQNetwork;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DDQNServiceTest {

    private DDQNProperties properties;
    private DDQNService service;
    private Random deterministicRandom;

    @BeforeEach
    void setUp() {
        properties = new DDQNProperties();
        properties.setEpsilon(1.0);
        properties.setMinEpsilon(0.01);
        properties.setEpsilonDecay(0.9);
        properties.setLearningRate(0.1);
        properties.setGamma(0.9);
        properties.setTargetUpdateFrequency(2);

        // Fixed seed for deterministic exploration tests
        deterministicRandom = new Random(100);
        service = new DDQNService(properties, deterministicRandom);
    }

    @Test
    void testNetworksAreSeparate() {
        assertNotNull(service.getOnlineNetwork());
        assertNotNull(service.getTargetNetwork());
        assertNotSame(service.getOnlineNetwork(), service.getTargetNetwork(), "Online and Target networks must be separate objects.");
    }

    @Test
    void testEpsilonGreedyExplorationRespectsMask() {
        // Force explore
        properties.setEpsilon(1.0);
        
        ValidActionMask mask = ValidActionMask.builder()
                .validNextTasks(Map.of(1L, true, 2L, false, 3L, true))
                .build();
        
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        
        // Explore should randomly select 1 or 3, NEVER 2.
        for (int i = 0; i < 50; i++) {
            RouteAction action = service.selectAction(state);
            assertNotEquals(2L, action.getNextTaskId(), "Invalid action was selected during exploration!");
            assertTrue(action.getNextTaskId() == 1L || action.getNextTaskId() == 3L);
        }
    }

    @Test
    void testExploitationSelectsHighestValidQValue() {
        properties.setEpsilon(0.0); // Force exploit
        service = new DDQNService(properties, deterministicRandom); // recreate with epsilon=0
        
        ValidActionMask mask = ValidActionMask.builder()
                .validNextTasks(Map.of(10L, true, 20L, true))
                .build();
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        
        // In our deterministic baseline, Q is initialized to task ID (10 or 20)
        // Therefore, task 20 should have the higher Q value
        RouteAction action = service.selectAction(state);
        
        assertEquals(20L, action.getNextTaskId(), "Exploitation did not select the highest valid Q-value.");
    }

    @Test
    void testNoValidActionThrowsException() {
        ValidActionMask mask = ValidActionMask.builder()
                .validNextTasks(Map.of(1L, false))
                .build();
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        
        assertThrows(NoValidActionException.class, () -> {
            service.selectAction(state);
        });
    }

    @Test
    void testTargetNetworkSynchronization() {
        ValidActionMask mask = ValidActionMask.builder()
                .validNextTasks(Map.of(1L, true))
                .build();
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        RouteAction action = RouteAction.builder().nextTaskId(1L).build();

        service.getOnlineNetwork().update(state, action, 100.0, 1.0);
        
        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) service.getOnlineNetwork();
        BaselineDeterministicQNetwork target = (BaselineDeterministicQNetwork) service.getTargetNetwork();
        
        assertNotEquals(online.getWeights(), target.getWeights());
        
        service.syncTargetNetwork();
        
        assertEquals(online.getWeights(), target.getWeights(), "Target network weights were not synchronized properly.");
    }

    @Test
    void testTerminalTransitionTargetEqualsReward() {
        ValidActionMask mask = ValidActionMask.builder().validNextTasks(Map.of(1L, true)).build();
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        RouteAction action = RouteAction.builder().nextTaskId(1L).build();
        
        Transition transition = Transition.builder()
                .state(state)
                .action(action)
                .reward(50.0)
                .done(true)
                .build();
                
        service.train(transition);
        
        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) service.getOnlineNetwork();
        double updatedQ = online.evaluate(state, action);
        
        // Initial Q was 1.0. Target is 50.0. LR is 0.1
        // newQ = 1.0 + 0.1 * (50.0 - 1.0) = 1.0 + 4.9 = 5.9
        assertEquals(5.9, updatedQ, 0.001);
    }

    @Test
    void testNonTerminalDDQNTarget() {
        // Verify Double DQN logic: target evaluates best online action.
        ValidActionMask mask = ValidActionMask.builder().validNextTasks(Map.of(1L, true, 2L, true)).build();
        RouteDecisionState nextState = RouteDecisionState.builder().validActionMask(mask).build();
        
        // Online network will prefer 2L (since baseline Q = ID).
        // Let's modify the target network so 2L evaluates to a specific value.
        RouteAction bestNextAction = RouteAction.builder().nextTaskId(2L).build();
        service.getTargetNetwork().update(nextState, bestNextAction, 100.0, 1.0);
        
        // Also modify target for 1L to be even higher to ensure standard DQN max is NOT used.
        RouteAction otherNextAction = RouteAction.builder().nextTaskId(1L).build();
        service.getTargetNetwork().update(nextState, otherNextAction, 500.0, 1.0); // If standard DQN, target uses 500!
        
        RouteDecisionState state = RouteDecisionState.builder().validActionMask(mask).build();
        RouteAction action = RouteAction.builder().nextTaskId(1L).build();
        
        Transition transition = Transition.builder()
                .state(state)
                .action(action)
                .reward(10.0)
                .nextState(nextState)
                .done(false)
                .build();
                
        service.train(transition);
        
        // online argmax is 2L (baseline eval = 2). 
        // target eval of 2L is 100.0.
        // gamma is 0.9. reward is 10.
        // DDQN target = 10 + 0.9 * 100 = 100.0
        // Standard DQN would be 10 + 0.9 * 500 = 460.0
        // New Q = 1.0 + 0.1 * (100.0 - 1.0) = 1.0 + 9.9 = 10.9
        
        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) service.getOnlineNetwork();
        double updatedQ = online.evaluate(state, action);
        
        assertEquals(10.9, updatedQ, 0.001, "Target calculation must use Double-DQN principle, not standard DQN.");
    }
}
