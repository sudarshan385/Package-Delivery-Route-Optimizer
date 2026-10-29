package com.routeoptimizer.repository;

import com.routeoptimizer.entity.RouteEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteEdgeRepository extends JpaRepository<RouteEdge, Long> {
    List<RouteEdge> findByRouteIdOrderByEdgeOrderAsc(Long routeId);
}
