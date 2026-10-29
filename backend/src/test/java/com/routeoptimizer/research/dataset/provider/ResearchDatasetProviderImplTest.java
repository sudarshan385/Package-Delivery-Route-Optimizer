package com.routeoptimizer.research.dataset.provider;

import com.routeoptimizer.research.dataset.adapter.DatasetFileAdapter;
import com.routeoptimizer.research.dataset.adapter.DatasetFileResult;
import com.routeoptimizer.research.dataset.adapter.TrainingInputAdapter;
import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.split.DatasetSplitter;
import com.routeoptimizer.research.dataset.validation.DatasetValidator;
import com.routeoptimizer.research.training.domain.TrainingInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResearchDatasetProviderImplTest {

    private ResearchDatasetProviderImpl provider;
    private DatasetFileAdapter fileAdapter;
    private DatasetValidator validator;
    private DatasetSplitter splitter;
    private TrainingInputAdapter inputAdapter;

    @BeforeEach
    void setUp() {
        fileAdapter = Mockito.mock(DatasetFileAdapter.class);
        validator = Mockito.mock(DatasetValidator.class);
        splitter = Mockito.mock(DatasetSplitter.class);
        inputAdapter = Mockito.mock(TrainingInputAdapter.class);
        provider = new ResearchDatasetProviderImpl(fileAdapter, validator, splitter, inputAdapter);
    }

    @Test
    void testLoadDatasetAndStatistics() {
        DatasetRecord r1 = DatasetRecord.builder().routeId("R1").riderId(1L).build();
        DatasetRecord r2 = DatasetRecord.builder().routeId("R2").riderId(2L).build();

        when(fileAdapter.loadRecords("dummy.csv")).thenReturn(new DatasetFileResult(List.of(r1, r2), 1));
        when(validator.isValid(any())).thenReturn(true);

        when(splitter.split(any())).thenReturn(Map.of(
                DatasetSplit.TRAIN, List.of(List.of(r1)),
                DatasetSplit.VALIDATION, List.of(List.of(r2)),
                DatasetSplit.TEST, List.of()
        ));

        TrainingInput t1 = TrainingInput.builder().build();
        TrainingInput t2 = TrainingInput.builder().build();

        when(inputAdapter.toTrainingInput(List.of(r1))).thenReturn(t1);
        when(inputAdapter.toTrainingInput(List.of(r2))).thenReturn(t2);

        provider.loadDataset("dummy.csv");

        assertEquals(1, provider.getEpisodes(DatasetSplit.TRAIN).size());
        assertEquals(1, provider.getEpisodes(DatasetSplit.VALIDATION).size());
        assertEquals(0, provider.getEpisodes(DatasetSplit.TEST).size());

        var stats = provider.getStatistics();
        assertEquals(3, stats.getTotalRecords()); // 2 valid + 1 invalid
        assertEquals(2, stats.getTotalEpisodes());
        assertEquals(2, stats.getTotalRiders());
        assertEquals(1, stats.getInvalidRecordsCount()); // 1 format failure
    }
}
