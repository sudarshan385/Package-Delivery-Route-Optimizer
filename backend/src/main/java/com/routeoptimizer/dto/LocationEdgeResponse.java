package com.routeoptimizer.dto;

import com.routeoptimizer.entity.LocationEdge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationEdgeResponse {

    private Long id;
    private Long sourceLocationId;
    private String sourceLocationName;
    private Long destinationLocationId;
    private String destinationLocationName;
    private Double distance;
    private Double estimatedTime;
    private String geometryGeojson;
    private Boolean active;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static LocationEdgeResponse fromEntity(LocationEdge edge) {
        if (edge == null) {
            return null;
        }
        return LocationEdgeResponse.builder()
                .id(edge.getId())
                .sourceLocationId(edge.getSourceLocation() != null ? edge.getSourceLocation().getId() : null)
                .sourceLocationName(edge.getSourceLocation() != null ? edge.getSourceLocation().getName() : null)
                .destinationLocationId(edge.getDestinationLocation() != null ? edge.getDestinationLocation().getId() : null)
                .destinationLocationName(edge.getDestinationLocation() != null ? edge.getDestinationLocation().getName() : null)
                .distance(edge.getDistance())
                .estimatedTime(edge.getEstimatedTime())
                .geometryGeojson(edge.getGeometryGeojson())
                .active(edge.getActive())
                .version(edge.getVersion())
                .createdAt(edge.getCreatedAt())
                .updatedAt(edge.getUpdatedAt())
                .build();
    }
}
