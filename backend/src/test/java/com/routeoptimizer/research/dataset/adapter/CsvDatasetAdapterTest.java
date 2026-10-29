package com.routeoptimizer.research.dataset.adapter;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvDatasetAdapterTest {

    private CsvDatasetAdapter adapter;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        adapter = new CsvDatasetAdapter();
        tempFile = Files.createTempFile("dataset", ".csv");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testParseValidCsv() throws IOException {
        String csvContent = "route_id,rider_id,task_id,pickup_loc,delivery_loc,act_pickup,act_delivery,pu_start,pu_end,del_start,del_end,seq\n" +
                "R1,1,10,100,200,,,2023-10-01T10:00:00Z,2023-10-01T11:00:00Z,,,1\n" +
                "R1,1,11,101,201,,,,,,,2\n";
        Files.writeString(tempFile, csvContent);

        DatasetFileResult result = adapter.loadRecords(tempFile.toString());
        assertEquals(2, result.getRecords().size());
        assertEquals(0, result.getInvalidCount());

        DatasetRecord r1 = result.getRecords().get(0);
        assertEquals("R1", r1.getRouteId());
        assertEquals(1L, r1.getRiderId());
        assertEquals(10L, r1.getTaskId());
        assertEquals(1, r1.getSequenceOrder());
    }

    @Test
    void testMalformedCsv() throws IOException {
        String csvContent = "header1,header2\n" + // Malformed headers, less than 12
                "R1,1,10\n" + // not enough columns
                "R2,abc,def,x,x,x,x,x,x,x,x,x\n"; // bad format

        Files.writeString(tempFile, csvContent);

        DatasetFileResult result = adapter.loadRecords(tempFile.toString());
        assertEquals(0, result.getRecords().size());
        assertEquals(2, result.getInvalidCount());
    }
}
