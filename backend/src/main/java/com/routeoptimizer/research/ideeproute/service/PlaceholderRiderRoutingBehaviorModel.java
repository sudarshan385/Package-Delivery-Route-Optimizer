package com.routeoptimizer.research.ideeproute.service;

import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import com.routeoptimizer.research.ideeproute.domain.RiderPreference;
import com.routeoptimizer.research.ideeproute.masking.PrecedenceAwareAttentionMask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaceholderRiderRoutingBehaviorModel implements RiderRoutingBehaviorModel {

    // Store encoded representations per driver for future DDQN retrieval
    private final Map<Long, List<BehaviorRepresentation>> memory = new HashMap<>();

    @Override
    public BehaviorRepresentation encode(IDeepRouteInput input) {
        if (input == null || input.getTemporalSequence() == null || input.getTemporalSequence().isEmpty()) {
            return BehaviorRepresentation.builder().originalInput(input).build();
        }

        // Generate the precedence-aware mask
        PrecedenceAwareAttentionMask mask = new PrecedenceAwareAttentionMask(input.getTemporalSequence());

        BehaviorRepresentation representation = BehaviorRepresentation.builder()
                .originalInput(input)
                .mask(mask)
                // Note: No actual neural network weights are applied here.
                .build();

        if (input.getDriverId() != null) {
            memory.computeIfAbsent(input.getDriverId(), k -> new ArrayList<>()).add(representation);
        }

        return representation;
    }

    @Override
    public RiderPreference getRiderPreference(Long driverId) {
        List<BehaviorRepresentation> representations = memory.getOrDefault(driverId, new ArrayList<>());
        return RiderPreference.builder()
                .driverId(driverId)
                .encodedSequences(representations)
                .build();
    }
    
    // Test utility method
    public void clearMemory() {
        memory.clear();
    }
}
