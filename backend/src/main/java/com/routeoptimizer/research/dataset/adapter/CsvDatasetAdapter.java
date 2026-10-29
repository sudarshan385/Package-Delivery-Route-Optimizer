package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvDatasetAdapter implements DatasetFileAdapter {
    private static final Logger log = LoggerFactory.getLogger(CsvDatasetAdapter.class);

    @Override
    public DatasetFileResult loadRecords(String filePath) {
        List<DatasetRecord> records = new ArrayList<>();
        long invalidCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(Path.of(filePath), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return new DatasetFileResult(records, 0);
            }

            String line;
            long lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split(",", -1);
                // expected format: route_id, rider_id, task_id, pickup_loc, delivery_loc,
                // actual_pickup, actual_delivery, pu_start, pu_end, del_start, del_end, seq
                if (parts.length < 12) {
                    log.warn("Malformed CSV line {}: insufficient columns", lineNumber);
                    invalidCount++;
                    continue;
                }

                try {
                    String routeId = parts[0].trim();
                    Long riderId = parseLongOrNull(parts[1]);
                    Long taskId = parseLongOrNull(parts[2]);
                    Long pickupLoc = parseLongOrNull(parts[3]);
                    Long deliveryLoc = parseLongOrNull(parts[4]);
                    OffsetDateTime actualPu = parseTimeOrNull(parts[5]);
                    OffsetDateTime actualDel = parseTimeOrNull(parts[6]);
                    OffsetDateTime puStart = parseTimeOrNull(parts[7]);
                    OffsetDateTime puEnd = parseTimeOrNull(parts[8]);
                    OffsetDateTime delStart = parseTimeOrNull(parts[9]);
                    OffsetDateTime delEnd = parseTimeOrNull(parts[10]);
                    Integer seq = parseIntegerOrNull(parts[11]);

                    DatasetRecord record = DatasetRecord.builder()
                            .routeId(routeId.isEmpty() ? null : routeId)
                            .riderId(riderId)
                            .taskId(taskId)
                            .pickupLocationId(pickupLoc)
                            .deliveryLocationId(deliveryLoc)
                            .actualPickupTime(actualPu)
                            .actualDeliveryTime(actualDel)
                            .pickupTimeWindowStart(puStart)
                            .pickupTimeWindowEnd(puEnd)
                            .deliveryTimeWindowStart(delStart)
                            .deliveryTimeWindowEnd(delEnd)
                            .sequenceOrder(seq)
                            .build();

                    records.add(record);
                } catch (Exception e) {
                    log.warn("Failed to parse CSV line {}: {}", lineNumber, e.getMessage());
                    invalidCount++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read dataset file: " + filePath, e);
        }

        return new DatasetFileResult(records, invalidCount);
    }

    private Long parseLongOrNull(String val) {
        val = val.trim();
        return val.isEmpty() ? null : Long.parseLong(val);
    }

    private Integer parseIntegerOrNull(String val) {
        val = val.trim();
        return val.isEmpty() ? null : Integer.parseInt(val);
    }

    private OffsetDateTime parseTimeOrNull(String val) {
        val = val.trim();
        if (val.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(val);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format: " + val);
        }
    }
}
