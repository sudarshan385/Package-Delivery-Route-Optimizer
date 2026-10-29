package com.routeoptimizer.research.repository;

import com.routeoptimizer.research.domain.HistoricalRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface HistoricalRouteRepository extends JpaRepository<HistoricalRoute, Long> {
    List<HistoricalRoute> findByDriverId(Long driverId);
    List<HistoricalRoute> findByRouteDateBetween(OffsetDateTime start, OffsetDateTime end);
}
