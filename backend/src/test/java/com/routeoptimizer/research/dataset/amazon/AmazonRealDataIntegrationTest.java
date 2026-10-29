package com.routeoptimizer.research.dataset.amazon;

import com.routeoptimizer.research.dataset.adapter.AmazonRouteDecisionState;
import com.routeoptimizer.research.dataset.adapter.AmazonToResearchAdapter;
import com.routeoptimizer.research.reward.service.CmrCalculator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AmazonRealDataIntegrationTest {

    @Test
    void testRealAmazonDatasetDryRun() throws Exception {
        Path basePath = Paths.get(System.getProperty("amazon.dataset.path", "S:/MAJOR PROJECT/research-data/"));
        Path routeFile = basePath.resolve("route_data.json");
        Path packageFile = basePath.resolve("package_data.json");
        Path seqFile = basePath.resolve("actual_sequences.json");

        Assumptions.assumeTrue(Files.exists(routeFile), "Real route_data.json not found, skipping real data dry run.");
        Assumptions.assumeTrue(Files.exists(packageFile), "Real package_data.json not found.");
        Assumptions.assumeTrue(Files.exists(seqFile), "Real actual_sequences.json not found.");

        AmazonDatasetProvider provider = new AmazonDatasetProvider();
        // Load just 3 routes to avoid processing the whole memory limit
        List<AmazonRouteRecord> routes = provider.loadDataset(routeFile, packageFile, seqFile, 3);
        
        assertFalse(routes.isEmpty(), "Expected at least 1 route from real dataset");
        
        AmazonToResearchAdapter adapter = new AmazonToResearchAdapter();
        
        int totalStops = 0;
        int totalPkgs = 0;

        for (AmazonRouteRecord route : routes) {
            assertNotNull(route.getRouteId());
            AmazonToResearchAdapter.AdaptedAmazonInput adaptedInput = adapter.adapt(route);
            assertNotNull(adaptedInput);
            
            // Validate String preservation
            assertEquals(route.getRouteId(), adaptedInput.getRouteId());
            
            totalStops += route.getStops().size();
            for (AmazonStopRecord stop : route.getStops()) {
                if (stop.getPackages() != null) {
                    totalPkgs += stop.getPackages().size();
                }
            }
            
            // Actual sequences used
            assertEquals(route.getStops().size(), adaptedInput.getActualSequence().size());
            
            // Validate delivery-only
            for (AmazonToResearchAdapter.AdaptedAmazonTask task : adaptedInput.getTasks()) {
                assertEquals("DELIVERY", task.getTaskType());
                assertNull(task.getActualDeliveryTime()); // No fabricated timestamps
            }
            assertNull(adaptedInput.getDriverId()); // No fabricated driver ID
            
            // Validate CMR/ED on String identifiers
            CmrCalculator cmr = new CmrCalculator();
            double score = cmr.calculateCmr(adaptedInput.getActualSequence(), adaptedInput.getActualSequence());
            assertEquals(1.0, score); // Perfect match self-test

            // DDQN State Representation boundary test
            List<AmazonRouteDecisionState.StateTaskString> stateTasks = new ArrayList<>();
            Map<String, Boolean> actionMask = new HashMap<>();
            
            for (AmazonToResearchAdapter.AdaptedAmazonTask task : adaptedInput.getTasks()) {
                stateTasks.add(AmazonRouteDecisionState.StateTaskString.builder()
                        .taskId(task.getStopId())
                        .locationId(task.getStopId())
                        .taskType(task.getTaskType())
                        .earliestTime(task.getEarliestTime())
                        .latestTime(task.getLatestTime())
                        .build());
                        
                actionMask.put(task.getStopId(), true); // All deliveries valid initially
            }
            
            AmazonRouteDecisionState state = AmazonRouteDecisionState.builder()
                    .currentLocationId(adaptedInput.getActualSequence().isEmpty() ? null : adaptedInput.getActualSequence().get(0))
                    .remainingTasks(stateTasks)
                    .validNextTasks(actionMask)
                    .build();
                    
            assertNotNull(state);
            assertTrue(state.getValidNextTasks().get(adaptedInput.getActualSequence().get(0)));
            assertNull(state.getDriverId());
        }
        
        System.out.println("Real routes inspected: " + routes.size());
        System.out.println("Real stops inspected: " + totalStops);
        System.out.println("Real packages inspected: " + totalPkgs);
    }
}
