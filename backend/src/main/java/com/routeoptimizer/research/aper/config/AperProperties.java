package com.routeoptimizer.research.aper.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "research.aper")
public class AperProperties {
    private int capacity = 10000;
    private double alpha = 0.6; // Priority exponent
    private double beta = 0.4;  // Importance sampling exponent (not fully used until training loop, but good for completeness)
    private double tdErrorWeight = 0.7;
    private double complexityWeight = 0.3;
    
    // Threshold to distinguish between high-impact and low-impact transitions
    private double highImpactThreshold = 0.5;
    
    // Ratio of high-impact samples in a batch
    private double highImpactSampleRatio = 0.7; 
}
