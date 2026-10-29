package com.routeoptimizer.research.tsmtw.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateRoute {
    private List<TSMTWTask> orderedTasks;
    private Double totalDistance;
    private Double estimatedTime;
    private boolean feasible;
    private String rejectionReason;
}
