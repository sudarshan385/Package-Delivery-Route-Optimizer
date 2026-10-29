package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import lombok.Value;

import java.util.List;

@Value
public class DatasetFileResult {
    List<DatasetRecord> records;
    long invalidCount;
}
