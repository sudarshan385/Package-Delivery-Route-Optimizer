package com.routeoptimizer.research.aper.service;

import com.routeoptimizer.research.aper.config.AperProperties;
import org.springframework.stereotype.Service;

@Service
public class PriorityCalculator {

    private final AperProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public PriorityCalculator(AperProperties properties) {
        this.properties = properties;
    }

    /**
     * Calculates deterministic priority based on TD error and task complexity.
     * Higher TD error implies the network has more to learn from this transition.
     * Higher task complexity implies the transition contains richer routing information.
     */
    public double calculatePriority(double tdError, double taskComplexity) {
        // Absolute TD error is used as magnitude of surprise
        double absTdError = Math.abs(tdError);
        
        // Priority = (w1 * |TD| + w2 * Complexity) ^ alpha
        double rawPriority = (properties.getTdErrorWeight() * absTdError) + 
                             (properties.getComplexityWeight() * taskComplexity);
                             
        // Ensure priority is strictly positive by adding a small epsilon before power
        double epsilon = 1e-5;
        return Math.pow(rawPriority + epsilon, properties.getAlpha());
    }
}
