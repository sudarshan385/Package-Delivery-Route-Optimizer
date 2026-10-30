package com.routeoptimizer.research.api.dto;

import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DynamicReplanResponse {
    private boolean feasible;
    private String rejectionReason;
    
    private CandidateRoute initialRoute;
    private CandidateRoute revisedRoute;
    
    private double originalDistance;
    private double originalTime;
    
    private double newDistance;
    private double newTime;
    
    private String calculationSource;
}
