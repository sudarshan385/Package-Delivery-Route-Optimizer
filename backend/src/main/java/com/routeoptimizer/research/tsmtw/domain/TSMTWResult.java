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
public class TSMTWResult {
    private boolean feasible;
    private List<CandidateRoute> candidateRoutes;
    private String rejectionReason;
}
