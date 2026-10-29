package com.routeoptimizer.research.domain;

import com.routeoptimizer.entity.BaseAuditableEntity;
import com.routeoptimizer.entity.Driver;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import com.routeoptimizer.research.domain.HistoricalTask;

@Entity
@Table(name = "research_historical_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalRoute extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "total_distance")
    private Double totalDistance;

    @Column(name = "total_time")
    private Double totalTime;

    @Column(name = "route_date", nullable = false)
    private OffsetDateTime routeDate;

    @OneToMany(mappedBy = "historicalRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HistoricalTask> tasks = new ArrayList<>();
}
