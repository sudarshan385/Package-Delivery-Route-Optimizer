package com.routeoptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "location_edges", uniqueConstraints = {
        @UniqueConstraint(name = "uq_location_edge_pair", columnNames = {"source_location_id", "destination_location_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationEdge extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}

