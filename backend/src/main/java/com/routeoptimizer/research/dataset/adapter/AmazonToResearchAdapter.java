package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.amazon.AmazonPackageRecord;
import com.routeoptimizer.research.dataset.amazon.AmazonRouteRecord;
import com.routeoptimizer.research.dataset.amazon.AmazonStopRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

public class AmazonToResearchAdapter {

    @Value
    @Builder
    public static class AdaptedAmazonInput {
        String driverId;
        String routeId;
        List<AdaptedAmazonTask> tasks;
        List<String> actualSequence;
    }

    @Value
    @Builder
    public static class AdaptedAmazonTask {
        String stopId;
        String packageId;
        String taskType;
        OffsetDateTime earliestTime;
        OffsetDateTime latestTime;
        OffsetDateTime actualDeliveryTime;
    }

    public AdaptedAmazonInput adapt(AmazonRouteRecord route) {
        if (route == null || route.getStops() == null) return null;
        
        List<AdaptedAmazonTask> tasks = new ArrayList<>();
        List<String> sequence = new ArrayList<>();
        
        for (AmazonStopRecord stop : route.getStops()) {
            String pkgId = null;
            OffsetDateTime start = null;
            OffsetDateTime end = null;
            if (stop.getPackages() != null && !stop.getPackages().isEmpty()) {
                AmazonPackageRecord pkg = stop.getPackages().get(0);
                pkgId = pkg.getPackageId();
                start = pkg.getTimeWindowStartUtc();
                end = pkg.getTimeWindowEndUtc();
            }
            tasks.add(AdaptedAmazonTask.builder()
                    .stopId(stop.getStopId())
                    .packageId(pkgId)
                    .taskType("DELIVERY")
                    .earliestTime(start)
                    .latestTime(end)
                    .actualDeliveryTime(null) // No fabricated timestamp
                    .build());
            sequence.add(stop.getStopId());
        }
        
        return AdaptedAmazonInput.builder()
                .driverId(null) // No fabricated driver ID
                .routeId(route.getRouteId())
                .tasks(tasks)
                .actualSequence(sequence)
                .build();
    }
}
