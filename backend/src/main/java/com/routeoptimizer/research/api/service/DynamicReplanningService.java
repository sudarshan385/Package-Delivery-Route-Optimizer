package com.routeoptimizer.research.api.service;

import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.research.api.dto.DynamicReplanRequest;
import com.routeoptimizer.research.api.dto.DynamicReplanResponse;
import com.routeoptimizer.research.ddqn.service.DDQNService;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.state.domain.RouteAction;
import com.routeoptimizer.research.state.domain.RouteDecisionState;
import com.routeoptimizer.research.state.service.StateRepresentationService;
import com.routeoptimizer.research.tsmtw.domain.CandidateRoute;
import com.routeoptimizer.research.tsmtw.domain.TSMTWRequest;
import com.routeoptimizer.research.tsmtw.domain.TSMTWResult;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import com.routeoptimizer.research.tsmtw.service.TsmTwService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DynamicReplanningService {

    private final DeliveryRepository deliveryRepository;
    private final TsmTwService tsmTwService;
    private final StateRepresentationService stateRepresentationService;
    private final DDQNService ddqnService;

    @Transactional(readOnly = true)
    public DynamicReplanResponse replan(DynamicReplanRequest request) {
        if (request.getCurrentDeliveryIds() == null || request.getCurrentDeliveryIds().isEmpty()) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .rejectionReason("No initial deliveries provided.")
                    .build();
        }

        List<TSMTWTask> initialTasks = buildTasksFromDeliveries(request.getCurrentDeliveryIds());
        if (initialTasks.isEmpty()) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .rejectionReason("Failed to load initial deliveries.")
                    .build();
        }

        TSMTWRequest initialTsmRequest = TSMTWRequest.builder()
                .startLocationId(request.getDriverLocationId())
                .startTime(OffsetDateTime.now())
                .tasks(initialTasks)
                .build();
        
        TSMTWResult initialResult = tsmTwService.generateCandidates(initialTsmRequest);
        
        if (!initialResult.isFeasible() || initialResult.getCandidateRoutes().isEmpty()) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .rejectionReason("Initial scenario is not feasible: " + initialResult.getRejectionReason())
                    .build();
        }

        CandidateRoute initialRoute = initialResult.getCandidateRoutes().get(0); // Best one

        if (request.getNewDeliveryId() == null) {
            return DynamicReplanResponse.builder()
                    .feasible(true)
                    .initialRoute(initialRoute)
                    .originalDistance(initialRoute.getTotalDistance())
                    .originalTime(initialRoute.getEstimatedTime())
                    .calculationSource("Dijkstra-equivalent TSM-TW")
                    .build();
        }

        List<Long> allDeliveries = new ArrayList<>(request.getCurrentDeliveryIds());
        allDeliveries.add(request.getNewDeliveryId());
        
        List<TSMTWTask> allTasks = buildTasksFromDeliveries(allDeliveries);
        
        TSMTWRequest newTsmRequest = TSMTWRequest.builder()
                .startLocationId(request.getDriverLocationId())
                .startTime(OffsetDateTime.now())
                .tasks(allTasks)
                .build();
                
        TSMTWResult newResult = tsmTwService.generateCandidates(newTsmRequest);

        if (!newResult.isFeasible() || newResult.getCandidateRoutes().isEmpty()) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .initialRoute(initialRoute)
                    .originalDistance(initialRoute.getTotalDistance())
                    .originalTime(initialRoute.getEstimatedTime())
                    .rejectionReason("New request cannot be feasibly routed: " + newResult.getRejectionReason())
                    .build();
        }

        // Use DDQN policy to evaluate the state and pick action
        RouteDecisionState state = stateRepresentationService.buildState(
                null, // driverId not strictly needed for action mask
                request.getDriverLocationId(),
                OffsetDateTime.now(),
                allTasks,
                new HashSet<>(),
                new HashSet<>(),
                newResult.getCandidateRoutes(),
                null
        );

        RouteAction action;
        try {
            action = ddqnService.selectAction(state);
        } catch (Exception e) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .initialRoute(initialRoute)
                    .originalDistance(initialRoute.getTotalDistance())
                    .originalTime(initialRoute.getEstimatedTime())
                    .rejectionReason("DDQN Policy Error: " + e.getMessage())
                    .build();
        }

        if (action.getCandidateRouteId() == null) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .initialRoute(initialRoute)
                    .originalDistance(initialRoute.getTotalDistance())
                    .originalTime(initialRoute.getEstimatedTime())
                    .rejectionReason("DDQN Policy selected an invalid action type for candidate routing.")
                    .build();
        }

        int routeIndex = (int) (action.getCandidateRouteId() - 1);
        if (routeIndex < 0 || routeIndex >= newResult.getCandidateRoutes().size()) {
            return DynamicReplanResponse.builder()
                    .feasible(false)
                    .initialRoute(initialRoute)
                    .originalDistance(initialRoute.getTotalDistance())
                    .originalTime(initialRoute.getEstimatedTime())
                    .rejectionReason("DDQN Policy selected an out-of-bounds route ID: " + action.getCandidateRouteId())
                    .build();
        }

        CandidateRoute revisedRoute = newResult.getCandidateRoutes().get(routeIndex);

        return DynamicReplanResponse.builder()
                .feasible(true)
                .initialRoute(initialRoute)
                .originalDistance(initialRoute.getTotalDistance())
                .originalTime(initialRoute.getEstimatedTime())
                .revisedRoute(revisedRoute)
                .newDistance(revisedRoute.getTotalDistance())
                .newTime(revisedRoute.getEstimatedTime())
                .calculationSource("DDQN Isolated Research Policy")
                .build();
    }

    private List<TSMTWTask> buildTasksFromDeliveries(List<Long> deliveryIds) {
        List<TSMTWTask> tasks = new ArrayList<>();
        long taskIdCounter = 1;
        for (Long dId : deliveryIds) {
            Optional<Delivery> opt = deliveryRepository.findById(dId);
            if (opt.isPresent()) {
                Delivery d = opt.get();
                Package p = d.getPkg();
                
                tasks.add(TSMTWTask.builder()
                        .id(taskIdCounter++)
                        .locationId(p.getSourceLocation().getId())
                        .deliveryId(d.getId())
                        .taskType(ResearchTaskType.PICKUP)
                        .earliestTime(OffsetDateTime.now())
                        .latestTime(OffsetDateTime.now().plusHours(48)) // fake broad time window
                        .build());
                        
                tasks.add(TSMTWTask.builder()
                        .id(taskIdCounter++)
                        .locationId(p.getDestinationLocation().getId())
                        .deliveryId(d.getId())
                        .taskType(ResearchTaskType.DELIVERY)
                        .earliestTime(OffsetDateTime.now())
                        .latestTime(OffsetDateTime.now().plusHours(48))
                        .build());
            }
        }
        return tasks;
    }
}
