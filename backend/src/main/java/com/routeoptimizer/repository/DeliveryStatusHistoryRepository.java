package com.routeoptimizer.repository;

import com.routeoptimizer.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryStatusHistoryRepository extends JpaRepository<DeliveryStatusHistory, Long> {
    @Query("SELECT h FROM DeliveryStatusHistory h WHERE h.delivery.id = :deliveryId ORDER BY h.createdAt DESC, h.id DESC")
    List<DeliveryStatusHistory> findByDeliveryIdOrderByCreatedAtDesc(@Param("deliveryId") Long deliveryId);

    @Query("SELECT h FROM DeliveryStatusHistory h WHERE h.delivery.id = :deliveryId AND h.newStatus = :status ORDER BY h.createdAt ASC, h.id ASC")
    List<DeliveryStatusHistory> findByDeliveryIdAndNewStatus(
            @Param("deliveryId") Long deliveryId,
            @Param("status") com.routeoptimizer.enums.DeliveryStatus status);
}
