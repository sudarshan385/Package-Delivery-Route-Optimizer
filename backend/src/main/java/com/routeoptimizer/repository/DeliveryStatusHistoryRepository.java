package com.routeoptimizer.repository;

import com.routeoptimizer.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {
    List<DeliveryStatusHistory> findByDeliveryIdOrderByCreatedAtDesc(Long deliveryId);

    @org.springframework.data.jpa.repository.Query("SELECT h FROM DeliveryStatusHistory h WHERE h.delivery.id = :deliveryId AND h.newStatus = :status ORDER BY h.createdAt ASC")
    List<DeliveryStatusHistory> findByDeliveryIdAndNewStatus(
            @org.springframework.data.repository.query.Param("deliveryId") Long deliveryId,
            @org.springframework.data.repository.query.Param("status") com.routeoptimizer.enums.DeliveryStatus status);
}
