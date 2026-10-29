package com.routeoptimizer.research.ideeproute.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RiderPreference {
    private Long driverId;
    private List<BehaviorRepresentation> encodedSequences;
}
