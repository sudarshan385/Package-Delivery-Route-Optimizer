package com.routeoptimizer.research.ideeproute.domain;

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
public class HistoricalTaskRepresentation {
    private Long taskId;
    private Long locationId;
    private Long deliveryId;
    private ResearchTaskType taskType;
    private OffsetDateTime timestamp;
    private Integer sequenceOrder;
}
