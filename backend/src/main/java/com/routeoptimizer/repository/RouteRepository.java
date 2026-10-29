package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Route;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findByDeliveryIdOrderByVersionNumberDesc(Long deliveryId);
    Optional<Route> findByDeliveryIdAndIsCurrentTrue(Long deliveryId);
    Optional<Route> findByDeliveryIdAndVersionNumber(Long deliveryId, Integer versionNumber);
    List<Route> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Route r WHERE r.id = :id")
    Optional<Route> findByIdWithLock(@Param("id") Long id);

    long countByActiveTrue();

    long countByIsCurrentTrueAndActiveTrue();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(r.totalDistance), 0.0) FROM Route r WHERE r.isCurrent = true AND r.active = true")
    Double sumDistanceForCurrentRoutes();

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(AVG(r.totalDistance), 0.0) FROM Route r WHERE r.isCurrent = true AND r.active = true")
    Double avgDistanceForCurrentRoutes();

    @org.springframework.data.jpa.repository.Query("SELECT r.algorithmUsed, COUNT(r) FROM Route r WHERE r.isCurrent = true AND r.active = true GROUP BY r.algorithmUsed")
    List<Object[]> countRoutesByAlgorithm();

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Route r JOIN FETCH r.delivery d JOIN FETCH r.sourceLocation s JOIN FETCH r.destinationLocation dest WHERE r.isCurrent = true AND r.active = true ORDER BY r.id ASC")
    List<Route> findAllCurrentRoutesForReport();
}
