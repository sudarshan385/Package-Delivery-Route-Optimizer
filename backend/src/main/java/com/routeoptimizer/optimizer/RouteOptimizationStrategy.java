package com.routeoptimizer.optimizer;

import com.routeoptimizer.dto.OptimizationRequest;
import com.routeoptimizer.enums.AlgorithmType;

public interface RouteOptimizationStrategy {
    OptimizationResult optimize(OptimizationRequest request);
    AlgorithmType getAlgorithmType();
}
