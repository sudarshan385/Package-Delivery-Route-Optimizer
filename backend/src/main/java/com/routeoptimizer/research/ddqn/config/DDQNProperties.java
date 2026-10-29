package com.routeoptimizer.research.ddqn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "research.ddqn")
public class DDQNProperties {
    private double learningRate = 0.001;
    private double gamma = 0.99;
    private double epsilon = 1.0;
    private double epsilonDecay = 0.995;
    private double minEpsilon = 0.01;
    private int targetUpdateFrequency = 100;
}
