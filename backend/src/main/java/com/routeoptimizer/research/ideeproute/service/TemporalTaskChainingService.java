package com.routeoptimizer.research.ideeproute.service;

import com.routeoptimizer.research.domain.HistoricalRoute;
import com.routeoptimizer.research.domain.HistoricalTask;
import com.routeoptimizer.research.ideeproute.domain.HistoricalTaskRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemporalTaskChainingService {

    /**
     * Converts a HistoricalRoute into an IDeepRouteInput.
     * Crucially, this preserves chronological order of historical tasks to model T(t-1) -> T(t) -> T(t+1).
     */
    public IDeepRouteInput extractTemporalSequence(HistoricalRoute route) {
        if (route == null) {
            return null;
        }

        List<HistoricalTaskRepresentation> sequence = route.getTasks().stream()
                // Sort explicitly by sequence order/time to preserve temporal chaining
                .sorted(Comparator.comparing(HistoricalTask::getSequenceOrder))
                .map(this::mapTask)
                .collect(Collectors.toList());

        return IDeepRouteInput.builder()
                .driverId(route.getDriver() != null ? route.getDriver().getId() : null)
                .routeId(route.getId())
                .temporalSequence(sequence)
                .build();
    }

    private HistoricalTaskRepresentation mapTask(HistoricalTask task) {
        return HistoricalTaskRepresentation.builder()
                .taskId(task.getId())
                .locationId(task.getLocation() != null ? task.getLocation().getId() : null)
                .deliveryId(task.getDelivery() != null ? task.getDelivery().getId() : null)
                .taskType(task.getTaskType())
                .timestamp(task.getActualArrivalTime())
                .sequenceOrder(task.getSequenceOrder())
                .build();
    }
}
