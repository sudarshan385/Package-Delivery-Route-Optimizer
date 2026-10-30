package com.routeoptimizer.entity;

import com.routeoptimizer.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private Package pkg;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id", nullable = false)
    private Location sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private Location destinationLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.CREATED;

    @Column(name = "estimated_distance")
    private Double estimatedDistance;

    @Column(name = "estimated_time")
    private Double estimatedTime;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id")
    private Location hub;

    @Column(name = "hub_selection_basis")
    private String hubSelectionBasis;

    @Column(name = "current_stage", length = 50)
    @Builder.Default
    private String currentStage = "REQUEST_CREATED";

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "return_confirmed_at")
    private OffsetDateTime returnConfirmedAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<DeliveryLeg> legs = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<ShipmentEvent> shipmentEvents = new java.util.ArrayList<>();

    public Package getPackage() {
        return pkg;
    }

    public void setPackage(Package pkg) {
        this.pkg = pkg;
        syncLocationsFromPackage();
    }

    @PrePersist
    @PreUpdate
    public void syncLocationsFromPackage() {
        if (pkg != null) {
            if (this.sourceLocation == null && pkg.getSourceLocation() != null) {
                this.sourceLocation = pkg.getSourceLocation();
            }
            if (this.destinationLocation == null && pkg.getDestinationLocation() != null) {
                this.destinationLocation = pkg.getDestinationLocation();
            }
        }
    }
}
