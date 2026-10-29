package com.routeoptimizer.research.reward.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DualRewardResult {
    double cmrScore;
    int editDistance;
    double edScore;
    double combinedReward;
}
