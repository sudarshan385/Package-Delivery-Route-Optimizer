package com.routeoptimizer.repository;

import com.routeoptimizer.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {

    List<ShipmentEvent> findByDeliveryIdOrderByEventTimestampAsc(Long deliveryId);

    List<ShipmentEvent> findByPkgIdOrderByEventTimestampAsc(Long packageId);

    List<ShipmentEvent> findByPkgTrackingNumberOrderByEventTimestampAsc(String trackingNumber);
}
