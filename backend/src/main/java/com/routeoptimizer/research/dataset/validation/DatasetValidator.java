package com.routeoptimizer.research.dataset.validation;

import com.routeoptimizer.research.dataset.domain.DatasetRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DatasetValidator {
    
    private static final Logger log = LoggerFactory.getLogger(DatasetValidator.class);

    public boolean isValid(DatasetRecord record) {
        if (record.getRouteId() == null || record.getRouteId().trim().isEmpty()) {
            log.warn("Record missing routeId: {}", record);
            return false;
        }
        if (record.getRiderId() == null) {
            log.warn("Record missing riderId: {}", record);
            return false;
        }
        if (record.getTaskId() == null) {
            log.warn("Record missing taskId: {}", record);
            return false;
        }
        if (record.getPickupLocationId() == null) {
            log.warn("Record missing pickupLocationId: {}", record);
            return false;
        }
        if (record.getDeliveryLocationId() == null) {
            log.warn("Record missing deliveryLocationId: {}", record);
            return false;
        }
        if (record.getSequenceOrder() == null) {
            log.warn("Record missing sequenceOrder: {}", record);
            return false;
        }

        // Validations for time windows if present
        if (record.getPickupTimeWindowStart() != null && record.getPickupTimeWindowEnd() != null) {
            if (record.getPickupTimeWindowStart().isAfter(record.getPickupTimeWindowEnd())) {
                log.warn("Record has impossible pickup time window: {}", record);
                return false;
            }
        }

        if (record.getDeliveryTimeWindowStart() != null && record.getDeliveryTimeWindowEnd() != null) {
            if (record.getDeliveryTimeWindowStart().isAfter(record.getDeliveryTimeWindowEnd())) {
                log.warn("Record has impossible delivery time window: {}", record);
                return false;
            }
        }

        return true;
    }
}
