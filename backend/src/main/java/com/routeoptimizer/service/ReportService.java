package com.routeoptimizer.service;

import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.DeliveryStatusHistory;
import com.routeoptimizer.entity.Route;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.DeliveryStatusHistoryRepository;
import com.routeoptimizer.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;
    private final RouteRepository routeRepository;

    private static final String DELIVERY_CSV_HEADER = "delivery_id,package_tracking_number,driver,vehicle,status,created_at,dispatch_time,completion_time\n";
    private static final String ROUTE_CSV_HEADER = "route_id,delivery_id,source,destination,distance_km,algorithm,created_at\n";

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public String generateDeliveryReportCsv() {
        StringBuilder csv = new StringBuilder(DELIVERY_CSV_HEADER);
        List<Delivery> deliveries = deliveryRepository.findAllActiveForReport();

        for (Delivery d : deliveries) {
            String deliveryId = String.valueOf(d.getId());
            String trackingNumber = d.getPackage() != null ? d.getPackage().getTrackingNumber() : "";
            String driver = d.getDriver() != null ? d.getDriver().getName() : "UNASSIGNED";
            String vehicle = d.getVehicle() != null ? d.getVehicle().getRegistrationNumber() : "UNASSIGNED";
            String status = d.getStatus() != null ? d.getStatus().name() : "";
            String createdAt = d.getCreatedAt() != null ? d.getCreatedAt().toString() : "";

            // Query dispatch timestamp from delivery status history
            String dispatchTime = "";
            List<DeliveryStatusHistory> inProgressHistory = deliveryStatusHistoryRepository
                    .findByDeliveryIdAndNewStatus(d.getId(), DeliveryStatus.IN_PROGRESS);
            if (!inProgressHistory.isEmpty()) {
                dispatchTime = inProgressHistory.get(0).getCreatedAt().toString();
            }

            String completionTime = d.getCompletedAt() != null ? d.getCompletedAt().toString() : "";

            csv.append(escapeCsvField(deliveryId)).append(",")
                    .append(escapeCsvField(trackingNumber)).append(",")
                    .append(escapeCsvField(driver)).append(",")
                    .append(escapeCsvField(vehicle)).append(",")
                    .append(escapeCsvField(status)).append(",")
                    .append(escapeCsvField(createdAt)).append(",")
                    .append(escapeCsvField(dispatchTime)).append(",")
                    .append(escapeCsvField(completionTime)).append("\n");
        }

        return csv.toString();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public String generateRouteReportCsv() {
        StringBuilder csv = new StringBuilder(ROUTE_CSV_HEADER);
        List<Route> routes = routeRepository.findAllCurrentRoutesForReport();

        for (Route r : routes) {
            String routeId = String.valueOf(r.getId());
            String deliveryId = r.getDelivery() != null ? String.valueOf(r.getDelivery().getId()) : "";
            String source = r.getSourceLocation() != null ? r.getSourceLocation().getName() : "";
            String destination = r.getDestinationLocation() != null ? r.getDestinationLocation().getName() : "";
            String distanceKm = r.getTotalDistance() != null ? String.valueOf(r.getTotalDistance()) : "0.0";
            String algorithm = r.getAlgorithmUsed() != null ? r.getAlgorithmUsed() : "";
            String createdAt = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "";

            csv.append(escapeCsvField(routeId)).append(",")
                    .append(escapeCsvField(deliveryId)).append(",")
                    .append(escapeCsvField(source)).append(",")
                    .append(escapeCsvField(destination)).append(",")
                    .append(escapeCsvField(distanceKm)).append(",")
                    .append(escapeCsvField(algorithm)).append(",")
                    .append(escapeCsvField(createdAt)).append("\n");
        }

        return csv.toString();
    }

    /**
     * Sanitizes and escapes a CSV field value:
     * 1. Protects against CSV Formula Injection (=, +, -, @, tab, CR).
     * 2. Handles null values safely.
     * 3. Quotes fields containing commas, quotes, or newlines with RFC 4180 escaping.
     */
    public static String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }

        String field = value;

        // Prevent formula injection: prefix with single quote if first character is formula trigger
        if (!field.isEmpty()) {
            char firstChar = field.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
                field = "'" + field;
            }
        }

        // If field contains comma, quote, or newline, escape double quotes and wrap in quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            field = "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
    }
}
