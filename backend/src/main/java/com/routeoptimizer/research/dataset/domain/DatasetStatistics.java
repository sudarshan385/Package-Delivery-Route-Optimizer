package com.routeoptimizer.research.dataset.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DatasetStatistics {
    long totalRecords;
    long totalEpisodes;
    long totalRiders;
    long totalTasks;
    long trainingEpisodesCount;
    long validationEpisodesCount;
    long testEpisodesCount;
    long invalidRecordsCount;
}
