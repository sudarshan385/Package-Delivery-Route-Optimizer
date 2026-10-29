package com.routeoptimizer.research.ideeproute.masking;

import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.HistoricalTaskRepresentation;

import java.util.List;

/**
 * Precedence-aware attention masking.
 * The mask prevents invalid task relationships from being treated as unrestricted candidate relationships.
 * For instance, Delivery(P1) must not be a valid predecessor for Pickup(P1).
 */
public class PrecedenceAwareAttentionMask {

    private final boolean[][] mask;
    private final int size;

    public PrecedenceAwareAttentionMask(List<HistoricalTaskRepresentation> tasks) {
        this.size = tasks.size();
        this.mask = new boolean[size][size];
        
        // Initialize all to valid (true) by default in an attention context, except self (typically false depending on model, let's keep true for now and then restrict)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                this.mask[i][j] = true; 
            }
        }

        // Apply pickup-before-delivery masking
        // Delivery(P1) must not be a valid predecessor for Pickup(P1).
        // i = potential predecessor, j = target task.
        // mask[i][j] = true means i can be attended to when predicting j (or i can precede j).
        for (int i = 0; i < size; i++) {
            HistoricalTaskRepresentation predecessor = tasks.get(i);
            for (int j = 0; j < size; j++) {
                HistoricalTaskRepresentation current = tasks.get(j);
                
                // If the predecessor is a DELIVERY, and the current task is the matching PICKUP,
                // this is INVALID because the delivery cannot precede its own pickup.
                if (predecessor.getDeliveryId() != null && predecessor.getDeliveryId().equals(current.getDeliveryId())) {
                    if (predecessor.getTaskType() == ResearchTaskType.DELIVERY && current.getTaskType() == ResearchTaskType.PICKUP) {
                        this.mask[i][j] = false;
                    }
                }
            }
        }
    }

    /**
     * Returns true if task i is a valid predecessor for task j.
     */
    public boolean isValidPredecessor(int predecessorIndex, int targetIndex) {
        if (predecessorIndex < 0 || predecessorIndex >= size || targetIndex < 0 || targetIndex >= size) {
            return false;
        }
        return mask[predecessorIndex][targetIndex];
    }
    
    public boolean[][] getRawMask() {
        return this.mask;
    }
}
