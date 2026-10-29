package com.routeoptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateStopEvaluation {
    private Long legId;
    private Long deliveryId;
    private String trackingNumber;
    private Double packageWeightKg;
    private Long locationId;
    private String locationName;
    private Double latitude;
    private Double longitude;
    private String address;
    private String senderName;
    private String receiverName;
    private String contactPhone;
    private Double directDistanceToHubKm;
    private Double detourKm;
    private boolean eligible;
    private String exclusionReason;
    private Integer proposedStopOrder;
    private Double cumulativeDistanceKm;
    private Double estimatedArrivalMinutes;
}
