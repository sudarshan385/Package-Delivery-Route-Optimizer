package com.routeoptimizer.dto;

import com.routeoptimizer.entity.ShipmentEvent;
import lombok.*;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentEventResponse {
    private Long id;
    private Long deliveryId;
    private Long packageId;
    private String stage;
    private String title;
    private String description;
    private String locationName;
    private Long hubId;
    private String hubName;
    private Long driverId;
    private String driverName;
    private String actorUsername;
    private OffsetDateTime eventTimestamp;

    public static ShipmentEventResponse fromEntity(ShipmentEvent event) {
        if (event == null) return null;
        return ShipmentEventResponse.builder()
                .id(event.getId())
                .deliveryId(event.getDelivery() != null ? event.getDelivery().getId() : null)
                .packageId(event.getPkg() != null ? event.getPkg().getId() : null)
                .stage(event.getStage())
                .title(event.getTitle())
                .description(event.getDescription())
                .locationName(event.getLocationName())
                .hubId(event.getHub() != null ? event.getHub().getId() : null)
                .hubName(event.getHub() != null ? event.getHub().getName() : null)
                .driverId(event.getDriver() != null ? event.getDriver().getId() : null)
                .driverName(event.getDriverName() != null ? event.getDriverName() : 
                        (event.getDriver() != null && event.getDriver().getUser() != null ? event.getDriver().getUser().getFullName() : null))
                .actorUsername(event.getActorUsername())
                .eventTimestamp(event.getEventTimestamp())
                .build();
    }
}
