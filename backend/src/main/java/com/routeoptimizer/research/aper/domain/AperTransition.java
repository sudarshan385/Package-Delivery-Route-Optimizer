package com.routeoptimizer.research.aper.domain;

import com.routeoptimizer.research.ddqn.domain.Transition;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AperTransition {
    @Builder.Default
    private final String id = UUID.randomUUID().toString();
    
    private final Transition transition;
    
    // Core values for APER prioritization
    private double tdError;
    private double taskComplexity;
    
    // The calculated priority
    private double priority;
    
    // Marks if it's currently considered high impact
    private boolean isHighImpact;

    /**
     * Updates the TD error and recalculates priority using a provided calculator function.
     */
    public void updateTdError(double newTdError, java.util.function.BiFunction<Double, Double, Double> priorityCalculator, double threshold) {
        this.tdError = newTdError;
        this.priority = priorityCalculator.apply(this.tdError, this.taskComplexity);
        this.isHighImpact = this.priority >= threshold;
    }
}
