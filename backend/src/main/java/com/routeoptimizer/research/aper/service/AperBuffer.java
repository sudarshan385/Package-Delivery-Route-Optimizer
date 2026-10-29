package com.routeoptimizer.research.aper.service;

import com.routeoptimizer.research.aper.config.AperProperties;
import com.routeoptimizer.research.aper.domain.AperTransition;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AperBuffer {

    private final AperProperties properties;
    private final PriorityCalculator priorityCalculator;

    // Double-buffer design
    private final Map<String, AperTransition> highImpactBuffer = new ConcurrentHashMap<>();
    private final Map<String, AperTransition> lowImpactBuffer = new ConcurrentHashMap<>();

    private final Random random;

    @org.springframework.beans.factory.annotation.Autowired
    public AperBuffer(AperProperties properties, PriorityCalculator priorityCalculator) {
        this.properties = properties;
        this.priorityCalculator = priorityCalculator;
        this.random = new Random(42); // deterministic for testing unless overridden
    }
    
    // For testing
    public AperBuffer(AperProperties properties, PriorityCalculator priorityCalculator, Random random) {
        this.properties = properties;
        this.priorityCalculator = priorityCalculator;
        this.random = random;
    }

    /**
     * Stores a transition into the appropriate buffer based on calculated priority.
     */
    public AperTransition store(com.routeoptimizer.research.ddqn.domain.Transition transition, double initialTdError, double taskComplexity) {
        double priority = priorityCalculator.calculatePriority(initialTdError, taskComplexity);
        boolean isHighImpact = priority >= properties.getHighImpactThreshold();

        AperTransition aperTransition = AperTransition.builder()
                .transition(transition)
                .tdError(initialTdError)
                .taskComplexity(taskComplexity)
                .priority(priority)
                .isHighImpact(isHighImpact)
                .build();

        enforceCapacity();

        if (isHighImpact) {
            highImpactBuffer.put(aperTransition.getId(), aperTransition);
        } else {
            lowImpactBuffer.put(aperTransition.getId(), aperTransition);
        }

        return aperTransition;
    }

    /**
     * Updates the TD error of an existing transition and potentially moves it between buffers.
     */
    public void updateTdError(String transitionId, double newTdError) {
        AperTransition transition = highImpactBuffer.get(transitionId);
        boolean wasHighImpact = true;
        
        if (transition == null) {
            transition = lowImpactBuffer.get(transitionId);
            wasHighImpact = false;
        }

        if (transition == null) {
            return; // Not found
        }

        // Update values
        transition.updateTdError(newTdError, priorityCalculator::calculatePriority, properties.getHighImpactThreshold());

        // Migrate between buffers if impact status changed
        if (wasHighImpact && !transition.isHighImpact()) {
            highImpactBuffer.remove(transitionId);
            lowImpactBuffer.put(transitionId, transition);
        } else if (!wasHighImpact && transition.isHighImpact()) {
            lowImpactBuffer.remove(transitionId);
            highImpactBuffer.put(transitionId, transition);
        }
    }

    /**
     * Samples a batch of transitions using adaptive prioritized sampling.
     */
    public List<AperTransition> sample(int batchSize) {
        if (batchSize <= 0) return Collections.emptyList();
        
        int totalSize = highImpactBuffer.size() + lowImpactBuffer.size();
        if (totalSize == 0) return Collections.emptyList();

        int effectiveBatchSize = Math.min(batchSize, totalSize);
        
        // Calculate split based on highImpactSampleRatio
        int numHigh = (int) Math.round(effectiveBatchSize * properties.getHighImpactSampleRatio());
        int numLow = effectiveBatchSize - numHigh;

        // Adjust if buffers don't have enough
        if (numHigh > highImpactBuffer.size()) {
            numHigh = highImpactBuffer.size();
            numLow = Math.min(lowImpactBuffer.size(), effectiveBatchSize - numHigh);
        } else if (numLow > lowImpactBuffer.size()) {
            numLow = lowImpactBuffer.size();
            numHigh = Math.min(highImpactBuffer.size(), effectiveBatchSize - numLow);
        }

        List<AperTransition> batch = new ArrayList<>();
        batch.addAll(sampleFromBuffer(highImpactBuffer.values(), numHigh));
        batch.addAll(sampleFromBuffer(lowImpactBuffer.values(), numLow));

        return batch;
    }

    /**
     * Priority-proportional sampling from a specific buffer without replacement.
     */
    private List<AperTransition> sampleFromBuffer(Collection<AperTransition> buffer, int count) {
        if (count <= 0 || buffer.isEmpty()) return Collections.emptyList();
        
        List<AperTransition> available = new ArrayList<>(buffer);
        List<AperTransition> sampled = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (available.isEmpty()) break;

            double totalPriority = available.stream().mapToDouble(AperTransition::getPriority).sum();
            double target = random.nextDouble() * totalPriority;
            double cumulative = 0.0;

            for (int j = 0; j < available.size(); j++) {
                cumulative += available.get(j).getPriority();
                if (cumulative >= target || j == available.size() - 1) {
                    sampled.add(available.remove(j));
                    break;
                }
            }
        }
        return sampled;
    }

    private void enforceCapacity() {
        // Very simplistic capacity enforcement for research prototype
        int total = highImpactBuffer.size() + lowImpactBuffer.size();
        if (total >= properties.getCapacity()) {
            // Remove a random element from low impact buffer first, if possible
            if (!lowImpactBuffer.isEmpty()) {
                String key = lowImpactBuffer.keySet().iterator().next();
                lowImpactBuffer.remove(key);
            } else if (!highImpactBuffer.isEmpty()) {
                String key = highImpactBuffer.keySet().iterator().next();
                highImpactBuffer.remove(key);
            }
        }
    }
    
    public int getHighImpactSize() {
        return highImpactBuffer.size();
    }
    
    public int getLowImpactSize() {
        return lowImpactBuffer.size();
    }
}
