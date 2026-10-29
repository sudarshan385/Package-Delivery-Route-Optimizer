package com.routeoptimizer.research.dataset.provider;

import com.routeoptimizer.research.dataset.adapter.DatasetFileAdapter;
import com.routeoptimizer.research.dataset.adapter.TrainingInputAdapter;
import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.domain.DatasetStatistics;
import com.routeoptimizer.research.dataset.split.DatasetSplitter;
import com.routeoptimizer.research.dataset.validation.DatasetValidator;
import com.routeoptimizer.research.training.domain.TrainingInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ResearchDatasetProviderImpl implements ResearchDatasetProvider {
    private final DatasetFileAdapter fileAdapter;
    private final DatasetValidator validator;
    private final DatasetSplitter splitter;
    private final TrainingInputAdapter inputAdapter;

    private List<TrainingInput> trainEpisodes = new ArrayList<>();
    private List<TrainingInput> validationEpisodes = new ArrayList<>();
    private List<TrainingInput> testEpisodes = new ArrayList<>();
    private DatasetStatistics statistics;

    public ResearchDatasetProviderImpl(DatasetFileAdapter fileAdapter, DatasetValidator validator, DatasetSplitter splitter, TrainingInputAdapter inputAdapter) {
        this.fileAdapter = fileAdapter;
        this.validator = validator;
        this.splitter = splitter;
        this.inputAdapter = inputAdapter;
    }

    @Override
    public void loadDataset(String filePath) {
        var rawResult = fileAdapter.loadRecords(filePath);
        List<DatasetRecord> allRecords = rawResult.getRecords();
        long invalidFormatCount = rawResult.getInvalidCount();

        List<DatasetRecord> validRecords = new ArrayList<>();
        long validationFailureCount = 0;

        for (DatasetRecord record : allRecords) {
            if (validator.isValid(record)) {
                validRecords.add(record);
            } else {
                validationFailureCount++;
            }
        }

        Map<String, List<DatasetRecord>> groupedByRoute = validRecords.stream()
                .collect(Collectors.groupingBy(DatasetRecord::getRouteId));

        List<List<DatasetRecord>> episodes = new ArrayList<>(groupedByRoute.values());

        Map<DatasetSplit, List<List<DatasetRecord>>> splitResult = splitter.split(episodes);

        this.trainEpisodes = splitResult.getOrDefault(DatasetSplit.TRAIN, List.of()).stream()
                .map(inputAdapter::toTrainingInput)
                .collect(Collectors.toList());

        this.validationEpisodes = splitResult.getOrDefault(DatasetSplit.VALIDATION, List.of()).stream()
                .map(inputAdapter::toTrainingInput)
                .collect(Collectors.toList());

        this.testEpisodes = splitResult.getOrDefault(DatasetSplit.TEST, List.of()).stream()
                .map(inputAdapter::toTrainingInput)
                .collect(Collectors.toList());

        long totalEpisodes = episodes.size();
        long totalRiders = validRecords.stream().map(DatasetRecord::getRiderId).distinct().count();
        
        this.statistics = DatasetStatistics.builder()
                .totalRecords(validRecords.size() + validationFailureCount + invalidFormatCount)
                .totalEpisodes(totalEpisodes)
                .totalRiders(totalRiders)
                .totalTasks(validRecords.size())
                .trainingEpisodesCount(this.trainEpisodes.size())
                .validationEpisodesCount(this.validationEpisodes.size())
                .testEpisodesCount(this.testEpisodes.size())
                .invalidRecordsCount(validationFailureCount + invalidFormatCount)
                .build();
    }

    @Override
    public List<TrainingInput> getEpisodes(DatasetSplit split) {
        return switch (split) {
            case TRAIN -> trainEpisodes;
            case VALIDATION -> validationEpisodes;
            case TEST -> testEpisodes;
        };
    }

    @Override
    public DatasetStatistics getStatistics() {
        return statistics;
    }
}
