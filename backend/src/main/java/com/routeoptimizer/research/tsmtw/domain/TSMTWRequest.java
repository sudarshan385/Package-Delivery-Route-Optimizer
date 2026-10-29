package com.routeoptimizer.research.tsmtw.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TSMTWRequest {
    private Long startLocationId;
    private List<TSMTWTask> tasks;
    private OffsetDateTime startTime;
}
