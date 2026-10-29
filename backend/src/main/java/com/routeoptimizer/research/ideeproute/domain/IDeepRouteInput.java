package com.routeoptimizer.research.ideeproute.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IDeepRouteInput {
    private Long driverId;
    private Long routeId;
    private List<HistoricalTaskRepresentation> temporalSequence;
}
