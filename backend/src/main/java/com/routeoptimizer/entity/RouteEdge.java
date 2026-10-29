package com.routeoptimizer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "route_edges", uniqueConstraints = {
        @UniqueConstraint(name = "uq_route_edge_order", columnNames = {"route_id", "edge_order"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "edge_order", nullable = false)
    private Integer edgeOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @Column(nullable = false)
    private Double distance;

    @Column(name = "estimated_time")
    private Double estimatedTime;

    @Column(name = "geometry_geojson", columnDefinition = "TEXT")
    private String geometryGeojson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RouteEdge fromLocationEdge(Route route, int edgeOrder, LocationEdge locationEdge) {
        return RouteEdge.builder()
                .route(route)
                .edgeOrder(edgeOrder)
                .sourceLocation(locationEdge.getSourceLocation())
                .destinationLocation(locationEdge.getDestinationLocation())
                .distance(locationEdge.getDistance())
                .estimatedTime(locationEdge.getEstimatedTime())
                .geometryGeojson(locationEdge.getGeometryGeojson())
                .build();
    }
}
