package com.routeoptimizer.research.dataset.validation;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetValidatorTest {

    private DatasetValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DatasetValidator();
    }

    @Test
    void testValidRecord() {
        DatasetRecord record = DatasetRecord.builder()
                .routeId("R1")
                .riderId(1L)
                .taskId(1L)
                .pickupLocationId(100L)
                .deliveryLocationId(200L)
                .sequenceOrder(1)
                .build();
        assertTrue(validator.isValid(record));
    }

    @Test
    void testMissingRequiredFields() {
        DatasetRecord record1 = DatasetRecord.builder().riderId(1L).taskId(1L).pickupLocationId(100L).deliveryLocationId(200L).sequenceOrder(1).build();
        assertFalse(validator.isValid(record1)); // missing routeId

        DatasetRecord record2 = DatasetRecord.builder().routeId("R1").taskId(1L).pickupLocationId(100L).deliveryLocationId(200L).sequenceOrder(1).build();
        assertFalse(validator.isValid(record2)); // missing riderId

        DatasetRecord record3 = DatasetRecord.builder().routeId("R1").riderId(1L).pickupLocationId(100L).deliveryLocationId(200L).sequenceOrder(1).build();
        assertFalse(validator.isValid(record3)); // missing taskId

        DatasetRecord record4 = DatasetRecord.builder().routeId("R1").riderId(1L).taskId(1L).deliveryLocationId(200L).sequenceOrder(1).build();
        assertFalse(validator.isValid(record4)); // missing pickupLocationId

        DatasetRecord record5 = DatasetRecord.builder().routeId("R1").riderId(1L).taskId(1L).pickupLocationId(100L).sequenceOrder(1).build();
        assertFalse(validator.isValid(record5)); // missing deliveryLocationId

        DatasetRecord record6 = DatasetRecord.builder().routeId("R1").riderId(1L).taskId(1L).pickupLocationId(100L).deliveryLocationId(200L).build();
        assertFalse(validator.isValid(record6)); // missing sequenceOrder
    }

    @Test
    void testInvalidTimeWindows() {
        OffsetDateTime now = OffsetDateTime.now();
        DatasetRecord record = DatasetRecord.builder()
                .routeId("R1").riderId(1L).taskId(1L).pickupLocationId(100L).deliveryLocationId(200L).sequenceOrder(1)
                .pickupTimeWindowStart(now.plusHours(1))
                .pickupTimeWindowEnd(now) // start is after end
                .build();
        
        assertFalse(validator.isValid(record));
    }
}
