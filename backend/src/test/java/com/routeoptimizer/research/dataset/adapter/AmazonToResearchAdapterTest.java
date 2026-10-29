package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.amazon.AmazonPackageRecord;
import com.routeoptimizer.research.dataset.amazon.AmazonRouteRecord;
import com.routeoptimizer.research.dataset.amazon.AmazonStopRecord;
import com.routeoptimizer.research.reward.service.CmrCalculator;
import com.routeoptimizer.research.reward.service.EditDistanceCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmazonToResearchAdapterTest {

    @Test
    void testAdapterRequirements() {
        AmazonPackageRecord pkg = AmazonPackageRecord.builder()
                .packageId("PackageID_String")
                .timeWindowStartUtc(null)
                .timeWindowEndUtc(null)
                .build();
                
        AmazonStopRecord stop = AmazonStopRecord.builder()
                .stopId("StopID_String")
                .packages(List.of(pkg))
                .build();
                
        AmazonRouteRecord route = AmazonRouteRecord.builder()
                .routeId("RouteID_String")
                .stops(List.of(stop))
                .build();
                
        AmazonToResearchAdapter adapter = new AmazonToResearchAdapter();
        AmazonToResearchAdapter.AdaptedAmazonInput input = adapter.adapt(route);
        
        // 1. Amazon String RouteID preserved
        assertEquals("RouteID_String", input.getRouteId());
        
        // 2. Amazon String StopID preserved
        assertEquals("StopID_String", input.getTasks().get(0).getStopId());
        
        // 3. Amazon String PackageID preserved
        assertEquals("PackageID_String", input.getTasks().get(0).getPackageId());
        
        // 5. Delivery-only Amazon task does not create fake pickup
        assertEquals(1, input.getTasks().size());
        assertEquals("DELIVERY", input.getTasks().get(0).getTaskType());
        
        // 6. Missing time window remains null
        assertNull(input.getTasks().get(0).getEarliestTime());
        
        // 7. i-DeepRoute (or input) does not receive fabricated driver identity
        assertNull(input.getDriverId());
        
        // 8. i-DeepRoute (or input) does not receive fabricated timestamps
        assertNull(input.getTasks().get(0).getActualDeliveryTime());
        
        // 4. Amazon stop sequence consumed by CMR/ED without numeric conversion
        CmrCalculator cmr = new CmrCalculator();
        EditDistanceCalculator ed = new EditDistanceCalculator();
        
        List<String> pred = List.of("StopID_String");
        List<String> ref = input.getActualSequence();
        
        assertEquals(1.0, cmr.calculateCmr(pred, ref));
        assertEquals(0, ed.calculateEditDistance(pred, ref));
    }
}
