package com.routeoptimizer.research.training.domain;

import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TrainingInput {
    Long driverId;
    Long initialLocationId;
    OffsetDateTime currentTime;
    List<TSMTWTask> tasks;
    BehaviorRepresentation riderBehavior;
    List<Long> referenceRoute;
}
