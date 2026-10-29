package com.routeoptimizer.research.reward.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calculates the Complete Match Rate (CMR) between a predicted task sequence and a reference task sequence.
 * CMR measures exact alignment. It can be implemented as the percentage of items in the predicted sequence 
 * that exactly match the reference sequence at the same position, or a binary 1/0 for a complete sequence match.
 * Following typical sequential alignment logic, we'll calculate the ratio of exact positional matches 
 * up to the length of the predicted sequence.
 */
@Service
public class CmrCalculator {

    /**
     * Calculates CMR score (0.0 to 1.0).
     * @param predicted The predicted sequence of task IDs.
     * @param reference The reference (optimal) sequence of task IDs.
     * @return CMR score between 0.0 and 1.0.
     */
    public <T> double calculateCmr(List<T> predicted, List<?> reference) {
        if (predicted == null || reference == null) {
            throw new IllegalArgumentException("Sequences cannot be null");
        }
        
        if (predicted.isEmpty() && reference.isEmpty()) {
            return 1.0;
        }
        
        if (predicted.isEmpty() || reference.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        int minLength = Math.min(predicted.size(), reference.size());
        
        for (int i = 0; i < minLength; i++) {
            if (predicted.get(i).equals(reference.get(i))) {
                matches++;
            }
        }
        
        // CMR is the ratio of matches to the max length of the sequences
        int maxLength = Math.max(predicted.size(), reference.size());
        return (double) matches / maxLength;
    }
}
