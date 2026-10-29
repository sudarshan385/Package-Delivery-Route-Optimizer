package com.routeoptimizer.research.ddqn.service;

import com.routeoptimizer.research.ddqn.config.DDQNProperties;
import com.routeoptimizer.research.ddqn.domain.Transition;
import com.routeoptimizer.research.ddqn.network.BaselineDeterministicQNetwork;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.domain.ValidActionMask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DDQNServiceTest {

    private DDQNService ddqnService;
    private DDQNProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DDQNProperties();
        properties.setGamma(0.9);
        properties.setLearningRate(1.0); // 1.0 for exact target overwrite
        properties.setEpsilon(0.0); // 0.0 to ensure argmax exploitation
        ddqnService = new DDQNService(properties, new Random(42));
    }

    private RouteDecisionState createStateWithValidActions(Long... taskIds) {
        Map<Long, Boolean> validNextTasks = new HashMap<>();
        for (Long id : taskIds) {
            validNextTasks.put(id, true);
        }
        ValidActionMask mask = ValidActionMask.builder().validNextTasks(validNextTasks).build();
        return RouteDecisionState.builder().validActionMask(mask).build();
    }

    @Test
    void testDoubleDQNMathematicalTarget() {
        RouteDecisionState currentState = createStateWithValidActions(1L);
        RouteAction currentAction = RouteAction.builder().nextTaskId(1L).build();

        RouteDecisionState nextState = createStateWithValidActions(10L, 20L);
        RouteAction actionA = RouteAction.builder().nextTaskId(10L).build();
        RouteAction actionB = RouteAction.builder().nextTaskId(20L).build();

        // Seed Q-values
        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) ddqnService.getOnlineNetwork();
        BaselineDeterministicQNetwork targetNet = (BaselineDeterministicQNetwork) ddqnService.getTargetNetwork();

        // Online network: Action B (20) > Action A (10)
        online.update(nextState, actionA, 10.0, 1.0);
        online.update(nextState, actionB, 20.0, 1.0);

        // Target network: Action A (100) > Action B (5)
        targetNet.update(nextState, actionA, 100.0, 1.0);
        targetNet.update(nextState, actionB, 5.0, 1.0);

        // Current Q is 0
        online.update(currentState, currentAction, 0.0, 1.0);

        Transition transition = Transition.builder()
                .state(currentState)
                .action(currentAction)
                .reward(50.0)
                .nextState(nextState)
                .done(false)
                .build();

        // DDQN target = reward + gamma * Q_target(nextState, argmax Q_online(nextState, a))
        // online argmax is B. target evaluating B is 5.0.
        // target = 50.0 + 0.9 * 5.0 = 54.5
        // current Q = 0.0, so tdError = 54.5 - 0.0 = 54.5

        double tdError = ddqnService.train(transition);
        
        assertEquals(54.5, tdError, 0.001);
        assertEquals(54.5, online.evaluate(currentState, currentAction), 0.001);
    }

    @Test
    void testTerminalTransition() {
        RouteDecisionState currentState = createStateWithValidActions(1L);
        RouteAction currentAction = RouteAction.builder().nextTaskId(1L).build();

        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) ddqnService.getOnlineNetwork();
        online.update(currentState, currentAction, 10.0, 1.0);

        Transition transition = Transition.builder()
                .state(currentState)
                .action(currentAction)
                .reward(100.0)
                .nextState(currentState) // Should be ignored
                .done(true)
                .build();

        // target = reward = 100.0
        // currentQ = 10.0, tdError = 90.0

        double tdError = ddqnService.train(transition);

        assertEquals(90.0, tdError, 0.001);
        assertEquals(100.0, online.evaluate(currentState, currentAction), 0.001);
    }

    @Test
    void testInvalidActionMask() {
        // State has valid action 10L, but 100L is explicitly false
        Map<Long, Boolean> validNextTasks = new HashMap<>();
        validNextTasks.put(10L, true);
        validNextTasks.put(100L, false); // invalid!
        ValidActionMask mask = ValidActionMask.builder().validNextTasks(validNextTasks).build();
        RouteDecisionState nextState = RouteDecisionState.builder().validActionMask(mask).build();

        RouteDecisionState currentState = createStateWithValidActions(1L);
        RouteAction currentAction = RouteAction.builder().nextTaskId(1L).build();

        BaselineDeterministicQNetwork online = (BaselineDeterministicQNetwork) ddqnService.getOnlineNetwork();
        BaselineDeterministicQNetwork targetNet = (BaselineDeterministicQNetwork) ddqnService.getTargetNetwork();

        RouteAction actionValid = RouteAction.builder().nextTaskId(10L).build();
        RouteAction actionInvalid = RouteAction.builder().nextTaskId(100L).build();

        // Make invalid action have huge Q value
        online.update(nextState, actionInvalid, 1000.0, 1.0);
        online.update(nextState, actionValid, 5.0, 1.0);

        targetNet.update(nextState, actionValid, 20.0, 1.0);

        online.update(currentState, currentAction, 0.0, 1.0);

        Transition transition = Transition.builder()
                .state(currentState)
                .action(currentAction)
                .reward(0.0)
                .nextState(nextState)
                .done(false)
                .build();

        // Should select actionValid (5.0) despite actionInvalid (1000.0) because of mask
        // Target = 0.0 + 0.9 * 20.0 = 18.0
        double tdError = ddqnService.train(transition);

        assertEquals(18.0, tdError, 0.001);
        assertEquals(18.0, online.evaluate(currentState, currentAction), 0.001);
    }
}
