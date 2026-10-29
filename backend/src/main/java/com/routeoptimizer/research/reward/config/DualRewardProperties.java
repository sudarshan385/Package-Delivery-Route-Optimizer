package com.routeoptimizer.research.reward.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "research.reward")
public class DualRewardProperties {
    private double cmrWeight = 0.5;
    private double edWeight = 0.5;
    private double maxReward = 100.0;
}
