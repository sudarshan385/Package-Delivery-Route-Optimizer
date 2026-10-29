package com.routeoptimizer.repository;

import com.routeoptimizer.entity.LocationEdge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationEdgeRepository extends JpaRepository<LocationEdge, Long> {

    @Query("SELECT e FROM LocationEdge e JOIN FETCH e.sourceLocation JOIN FETCH e.destinationLocation WHERE e.active = true")
    List<LocationEdge> findByActiveTrue();

    List<LocationEdge> findBySourceLocationIdAndActiveTrue(Long sourceLocationId);

    Optional<LocationEdge> findBySourceLocationIdAndDestinationLocationId(Long sourceLocationId, Long destinationLocationId);

    boolean existsBySourceLocationIdAndDestinationLocationId(Long sourceLocationId, Long destinationLocationId);

    boolean existsBySourceLocationIdAndDestinationLocationIdAndIdNot(Long sourceLocationId, Long destinationLocationId, Long id);

    Page<LocationEdge> findByActive(Boolean active, Pageable pageable);

    Page<LocationEdge> findBySourceLocationId(Long sourceLocationId, Pageable pageable);

    Page<LocationEdge> findByDestinationLocationId(Long destinationLocationId, Pageable pageable);

    List<LocationEdge> findBySourceLocationIdOrDestinationLocationId(Long sourceLocationId, Long destinationLocationId);
}
