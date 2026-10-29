package com.routeoptimizer.research.reward.service;

import com.routeoptimizer.research.reward.config.DualRewardProperties;
import com.routeoptimizer.research.reward.domain.DualRewardResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DualRewardService {

    private final CmrCalculator cmrCalculator;
    private final EditDistanceCalculator editDistanceCalculator;
    private final DualRewardProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public DualRewardService(
            CmrCalculator cmrCalculator,
            EditDistanceCalculator editDistanceCalculator,
            DualRewardProperties properties) {
        this.cmrCalculator = cmrCalculator;
        this.editDistanceCalculator = editDistanceCalculator;
        this.properties = properties;
    }

    /**
     * Calculates the dual reward based on CMR and ED against a reference (optimal) route.
     * The reference route must be supplied by the caller (no fabrication or arbitrary ground truths).
     */
    public DualRewardResult calculateReward(List<Long> predictedSequence, List<Long> referenceSequence) {
        if (predictedSequence == null || referenceSequence == null) {
            throw new IllegalArgumentException("Predicted and Reference sequences must be explicitly provided.");
        }

        double cmrScore = cmrCalculator.calculateCmr(predictedSequence, referenceSequence);
        int editDistance = editDistanceCalculator.calculateEditDistance(predictedSequence, referenceSequence);
        
        // Normalize ED to a score between 0.0 and 1.0 (1.0 being identical, i.e., ED=0)
        int maxLength = Math.max(predictedSequence.size(), referenceSequence.size());
        double edScore = 1.0;
        if (maxLength > 0) {
            edScore = Math.max(0.0, 1.0 - ((double) editDistance / maxLength));
        }

        // Calculate combined reward using configurable weights
        double combinedReward = (properties.getCmrWeight() * cmrScore + properties.getEdWeight() * edScore) * properties.getMaxReward();

        return DualRewardResult.builder()
                .cmrScore(cmrScore)
                .editDistance(editDistance)
                .edScore(edScore)
                .combinedReward(combinedReward)
                .build();
    }
}
