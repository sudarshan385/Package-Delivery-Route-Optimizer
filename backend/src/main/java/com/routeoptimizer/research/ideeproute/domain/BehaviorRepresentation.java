package com.routeoptimizer.research.ideeproute.domain;

import com.routeoptimizer.research.ideeproute.masking.PrecedenceAwareAttentionMask;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BehaviorRepresentation {
    private IDeepRouteInput originalInput;
    private PrecedenceAwareAttentionMask mask;
    // In a real deep learning integration, this would contain the embedded tensor representation
}
