package com.routeoptimizer.research.tsmtw.domain;

import com.routeoptimizer.research.domain.ResearchTaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TSMTWTask {
    private Long id;
    private Long locationId;
    private Long deliveryId;
    private ResearchTaskType taskType;
    private OffsetDateTime earliestTime;
    private OffsetDateTime latestTime;
}
