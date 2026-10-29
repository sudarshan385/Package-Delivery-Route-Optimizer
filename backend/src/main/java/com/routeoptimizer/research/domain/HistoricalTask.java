package com.routeoptimizer.research.domain;

import com.routeoptimizer.entity.BaseAuditableEntity;
import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Location;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "research_historical_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalTask extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "historical_route_id", nullable = false)
    private HistoricalRoute historicalRoute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    private ResearchTaskType taskType;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "time_window_start")
    private OffsetDateTime timeWindowStart;

    @Column(name = "time_window_end")
    private OffsetDateTime timeWindowEnd;

    @Column(name = "actual_arrival_time")
    private OffsetDateTime actualArrivalTime;
}
