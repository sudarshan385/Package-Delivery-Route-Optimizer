package com.routeoptimizer.research.dataset.amazon;

import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.provider.ResearchDatasetProvider;
import com.routeoptimizer.research.training.domain.TrainingInput;
import com.routeoptimizer.research.tsmtw.domain.TSMTWTask;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AmazonDatasetAdapterProvider implements ResearchDatasetProvider {

    private final AmazonDatasetProvider amazonDatasetProvider = new AmazonDatasetProvider();
    private List<TrainingInput> trainData = new ArrayList<>();
    private List<TrainingInput> valData = new ArrayList<>();
    private List<TrainingInput> testData = new ArrayList<>();
    private boolean loaded = false;
    private final int limit;
    private final long seed;

    public AmazonDatasetAdapterProvider(int limit, long seed) {
        this.limit = limit;
        this.seed = seed;
    }

    @Override
    public void loadDataset(String datasetPath) {
        if (loaded) return;
        Path basePath = Paths.get(datasetPath);
        Path routeFile = basePath.resolve("route_data.json");
        Path packageFile = basePath.resolve("package_data.json");
        Path seqFile = basePath.resolve("actual_sequences.json");

        try {
            List<AmazonRouteRecord> routes = amazonDatasetProvider.loadDataset(routeFile, packageFile, seqFile, limit);
            
            List<TrainingInput> inputs = new ArrayList<>();
            for (AmazonRouteRecord route : routes) {
                if (route.getStops() == null || route.getStops().isEmpty()) continue;
                
                List<TSMTWTask> tasks = new ArrayList<>();
                List<Long> referenceRoute = new ArrayList<>();
                
                for (AmazonStopRecord stop : route.getStops()) {
                    long seqId = stop.getActualSequence() != null ? stop.getActualSequence().longValue() : -1L;
                    if (seqId == -1L) continue;
                    
                    tasks.add(TSMTWTask.builder()
                            .id(seqId)
                            .locationId(seqId)
                            .taskType(com.routeoptimizer.research.domain.ResearchTaskType.DELIVERY) // No fake pickups
                            .build());
                    referenceRoute.add(seqId);
                }
                
                if (tasks.isEmpty()) continue;
                
                inputs.add(TrainingInput.builder()
                        .driverId(null) // No fabricated driver ID
                        .initialLocationId(referenceRoute.get(0))
                        .tasks(tasks)
                        .referenceRoute(referenceRoute)
                        .build());
                        
                if (limit > 0 && inputs.size() >= limit) {
                    break;
                }
            }

            // Deterministic split
            Random random = new Random(seed);
            Collections.shuffle(inputs, random);

            int total = inputs.size();
            int trainEnd = (int) (total * 0.7);
            int valEnd = (int) (total * 0.85);

            trainData = inputs.subList(0, trainEnd);
            valData = inputs.subList(trainEnd, valEnd);
            testData = inputs.subList(valEnd, total);
            
            loaded = true;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load Amazon dataset", e);
        }
    }

    @Override
    public List<TrainingInput> getEpisodes(DatasetSplit split) {
        if (!loaded) throw new IllegalStateException("Dataset not loaded");
        switch (split) {
            case TRAIN: return trainData;
            case VALIDATION: return valData;
            case TEST: return testData;
            default: return Collections.emptyList();
        }
    }
    
    @Override
    public com.routeoptimizer.research.dataset.domain.DatasetStatistics getStatistics() {
        return com.routeoptimizer.research.dataset.domain.DatasetStatistics.builder()
                .totalEpisodes(trainData.size() + valData.size() + testData.size())
                .trainingEpisodesCount(trainData.size())
                .validationEpisodesCount(valData.size())
                .testEpisodesCount(testData.size())
                .build();
    }
}
