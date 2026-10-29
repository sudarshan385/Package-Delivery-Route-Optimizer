package com.routeoptimizer.research.repository;

import com.routeoptimizer.research.domain.HistoricalTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoricalTaskRepository extends JpaRepository<HistoricalTask, Long> {
    List<HistoricalTask> findByHistoricalRouteIdOrderBySequenceOrderAsc(Long historicalRouteId);
    List<HistoricalTask> findByDeliveryId(Long deliveryId);
}
