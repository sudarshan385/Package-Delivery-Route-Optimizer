package com.routeoptimizer.research.experiment.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExperimentResult {
    ExperimentConfiguration configuration;
    ExperimentMetrics metrics;
    List<EpisodeMetrics> episodeMetrics;
    ComparisonResult comparison;
}
