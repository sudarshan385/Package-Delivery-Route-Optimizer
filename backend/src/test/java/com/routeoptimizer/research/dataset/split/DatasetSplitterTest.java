package com.routeoptimizer.research.dataset.split;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetSplitterTest {

    @Test
    void testDeterministicSplitAndNoOverlap() {
        DatasetSplitter splitter = new DatasetSplitter(0.6, 0.2, 123L);

        List<List<DatasetRecord>> groupedEpisodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            List<DatasetRecord> episode = new ArrayList<>();
            episode.add(DatasetRecord.builder().routeId("R" + i).build());
            groupedEpisodes.add(episode);
        }

        Map<DatasetSplit, List<List<DatasetRecord>>> result = splitter.split(groupedEpisodes);

        List<List<DatasetRecord>> train = result.get(DatasetSplit.TRAIN);
        List<List<DatasetRecord>> val = result.get(DatasetSplit.VALIDATION);
        List<List<DatasetRecord>> test = result.get(DatasetSplit.TEST);

        assertEquals(60, train.size());
        assertEquals(20, val.size());
        assertEquals(20, test.size());

        // Check no overlap
        for (List<DatasetRecord> tr : train) {
            assertFalse(val.contains(tr));
            assertFalse(test.contains(tr));
        }

        // Check deterministic (second run should yield exact same)
        Map<DatasetSplit, List<List<DatasetRecord>>> result2 = splitter.split(groupedEpisodes);
        assertEquals(train, result2.get(DatasetSplit.TRAIN));
        assertEquals(val, result2.get(DatasetSplit.VALIDATION));
        assertEquals(test, result2.get(DatasetSplit.TEST));
    }

    private void assertFalse(boolean condition) {
        assertTrue(!condition);
    }
}
