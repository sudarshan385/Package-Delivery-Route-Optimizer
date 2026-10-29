package com.routeoptimizer.research.dataset.split;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class DatasetSplitter {

    private final double trainRatio;
    private final double validationRatio;
    private final long seed;

    public DatasetSplitter() {
        this.trainRatio = 0.7;
        this.validationRatio = 0.15;
        this.seed = 42L;
    }

    public DatasetSplitter(double trainRatio, double validationRatio, long seed) {
        this.trainRatio = trainRatio;
        this.validationRatio = validationRatio;
        this.seed = seed;
    }

    public Map<DatasetSplit, List<List<DatasetRecord>>> split(List<List<DatasetRecord>> groupedEpisodes) {
        List<List<DatasetRecord>> shuffled = new ArrayList<>(groupedEpisodes);
        Collections.shuffle(shuffled, new Random(seed));

        int total = shuffled.size();
        int trainCount = (int) (total * trainRatio);
        int valCount = (int) (total * validationRatio);

        List<List<DatasetRecord>> train = new ArrayList<>();
        List<List<DatasetRecord>> val = new ArrayList<>();
        List<List<DatasetRecord>> test = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            if (i < trainCount) {
                train.add(shuffled.get(i));
            } else if (i < trainCount + valCount) {
                val.add(shuffled.get(i));
            } else {
                test.add(shuffled.get(i));
            }
        }

        Map<DatasetSplit, List<List<DatasetRecord>>> result = new EnumMap<>(DatasetSplit.class);
        result.put(DatasetSplit.TRAIN, train);
        result.put(DatasetSplit.VALIDATION, val);
        result.put(DatasetSplit.TEST, test);

        return result;
    }
}
