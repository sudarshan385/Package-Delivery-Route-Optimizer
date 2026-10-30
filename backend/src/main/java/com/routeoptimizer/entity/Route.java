package com.routeoptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "route")
    @Builder.Default
    private List<Delivery> deliveries = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private Integer versionNumber = 1;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "total_distance", nullable = false)
    private Double totalDistance;

    @Column(name = "estimated_time")
    private Double estimatedTime;

    @Column(name = "algorithm_used", nullable = false, length = 50)
    @Builder.Default
    private String algorithmUsed = "DIJKSTRA";

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "returned_to_hub_at")
    private java.time.OffsetDateTime returnedToHubAt;

    @Column(name = "return_confirmed_by", length = 100)
    private String returnConfirmedBy;

    @Column(name = "return_distance")
    private Double returnDistance;

    @Column(name = "return_estimated_time")
    private Double returnEstimatedTime;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RouteEdge> edges = new ArrayList<>();

    public Integer getRouteVersion() {
        return versionNumber;
    }

    public void setRouteVersion(Integer routeVersion) {
        this.versionNumber = routeVersion;
    }

    public Boolean getIsActive() {
        return getActive();
    }

    public void setIsActive(Boolean active) {
        setActive(active);
    }
}
