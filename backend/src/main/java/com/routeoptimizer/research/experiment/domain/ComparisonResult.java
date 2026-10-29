package com.routeoptimizer.research.experiment.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ComparisonResult {
    double ddqnCmr;
    Double dijkstraCmr; // Double allows null if unavailable
    double ddqnEd;
    Double dijkstraEd;
    double ddqnReward;
    Double dijkstraComparableMetric;
    double ddqnEpisodeLength;
    Double dijkstraRouteLength;
}
