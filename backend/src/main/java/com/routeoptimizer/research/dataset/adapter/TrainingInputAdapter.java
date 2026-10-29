package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import com.routeoptimizer.research.domain.ResearchTaskType;
import com.routeoptimizer.research.ideeproute.domain.BehaviorRepresentation;
import com.routeoptimizer.research.ideeproute.domain.HistoricalTaskRepresentation;
import com.routeoptimizer.research.ideeproute.domain.IDeepRouteInput;
import com.routeoptimizer.research.ideeproute.service.RiderRoutingBehaviorModel;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TrainingInputAdapter {

    private final RiderRoutingBehaviorModel behaviorModel;

    public TrainingInputAdapter(RiderRoutingBehaviorModel behaviorModel) {
        this.behaviorModel = behaviorModel;
    }

    public TrainingInput toTrainingInput(List<DatasetRecord> episodeRecords) {
        if (episodeRecords == null || episodeRecords.isEmpty()) {
            throw new IllegalArgumentException("Cannot create TrainingInput from empty records");
        }

        // Sort by sequence order to form the reference route
        episodeRecords.sort(Comparator.comparingInt(DatasetRecord::getSequenceOrder));

        Long driverId = episodeRecords.get(0).getRiderId();
        Long initialLocationId = episodeRecords.get(0).getPickupLocationId();
        
        OffsetDateTime currentTime = episodeRecords.get(0).getPickupTimeWindowStart();
        if (currentTime == null) {
            currentTime = episodeRecords.get(0).getActualPickupTime();
        }
        if (currentTime == null) {
            currentTime = OffsetDateTime.now();
        }

        List<TSMTWTask> tasks = new ArrayList<>();
        List<Long> referenceRoute = new ArrayList<>();
        List<HistoricalTaskRepresentation> temporalSequence = new ArrayList<>();
        
        long virtualTaskId = 1;
        long routeIdHash = episodeRecords.get(0).getRouteId() != null ? episodeRecords.get(0).getRouteId().hashCode() : 0L;

        for (DatasetRecord record : episodeRecords) {
            long pickupId = virtualTaskId++;
            long deliveryId = virtualTaskId++;

            // Create Pickup Task
            TSMTWTask pickupTask = TSMTWTask.builder()
                    .id(pickupId)
                    .locationId(record.getPickupLocationId())
                    .deliveryId(deliveryId)
                    .taskType(ResearchTaskType.PICKUP)
                    .earliestTime(record.getPickupTimeWindowStart())
                    .latestTime(record.getPickupTimeWindowEnd())
                    .build();

            // Create Delivery Task
            TSMTWTask deliveryTask = TSMTWTask.builder()
                    .id(deliveryId)
                    .locationId(record.getDeliveryLocationId())
                    .deliveryId(null)
                    .taskType(ResearchTaskType.DELIVERY)
                    .earliestTime(record.getDeliveryTimeWindowStart())
                    .latestTime(record.getDeliveryTimeWindowEnd())
                    .build();

            tasks.add(pickupTask);
            tasks.add(deliveryTask);
            
            // Add to reference route (pickup then delivery)
            referenceRoute.add(pickupId);
            referenceRoute.add(deliveryId);

            // Add to temporal sequence for i-DeepRoute
            temporalSequence.add(HistoricalTaskRepresentation.builder()
                    .taskId(pickupId)
                    .locationId(record.getPickupLocationId())
                    .deliveryId(deliveryId)
                    .taskType(ResearchTaskType.PICKUP)
                    .timestamp(record.getActualPickupTime() != null ? record.getActualPickupTime() : currentTime)
                    .sequenceOrder(record.getSequenceOrder() * 2 - 1)
                    .build());

            temporalSequence.add(HistoricalTaskRepresentation.builder()
                    .taskId(deliveryId)
                    .locationId(record.getDeliveryLocationId())
                    .deliveryId(null)
                    .taskType(ResearchTaskType.DELIVERY)
                    .timestamp(record.getActualDeliveryTime() != null ? record.getActualDeliveryTime() : currentTime.plusMinutes(30))
                    .sequenceOrder(record.getSequenceOrder() * 2)
                    .build());
        }

        IDeepRouteInput iDeepRouteInput = IDeepRouteInput.builder()
                .driverId(driverId)
                .routeId((long) Math.abs(routeIdHash))
                .temporalSequence(temporalSequence)
                .build();

        BehaviorRepresentation riderBehavior = behaviorModel.encode(iDeepRouteInput);

        return TrainingInput.builder()
                .driverId(driverId)
                .initialLocationId(initialLocationId)
                .currentTime(currentTime)
                .tasks(tasks)
                .riderBehavior(riderBehavior)
                .referenceRoute(referenceRoute)
                .build();
    }
}
