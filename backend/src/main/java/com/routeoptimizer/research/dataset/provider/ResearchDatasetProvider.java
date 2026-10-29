package com.routeoptimizer.research.dataset.provider;

import com.routeoptimizer.research.dataset.domain.DatasetSplit;
import com.routeoptimizer.research.dataset.domain.DatasetStatistics;
import com.routeoptimizer.research.training.domain.TrainingInput;

import java.util.List;

public interface ResearchDatasetProvider {
    void loadDataset(String filePath);
    List<TrainingInput> getEpisodes(DatasetSplit split);
    DatasetStatistics getStatistics();
}
