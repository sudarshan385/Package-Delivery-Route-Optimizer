package com.routeoptimizer.repository;

import com.routeoptimizer.entity.DeliveryLeg;
import com.routeoptimizer.enums.LegStatus;
import com.routeoptimizer.enums.LegType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryLegRepository extends JpaRepository<DeliveryLeg, Long> {

    List<DeliveryLeg> findByDeliveryIdOrderByLegOrderAsc(Long deliveryId);

    List<DeliveryLeg> findByDriverIdAndStatusInAndActiveTrue(Long driverId, Collection<LegStatus> statuses);

    List<DeliveryLeg> findByDriverIdAndActiveTrue(Long driverId);

    List<DeliveryLeg> findByLegTypeAndStatusAndActiveTrue(LegType legType, LegStatus status);

    Optional<DeliveryLeg> findByDeliveryIdAndLegType(Long deliveryId, LegType legType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DeliveryLeg l WHERE l.id = :id")
    Optional<DeliveryLeg> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COUNT(l) > 0 FROM DeliveryLeg l WHERE l.driver.id = :driverId AND l.status IN :statuses AND l.active = true")
    boolean existsByDriverIdAndStatusInAndActiveTrue(@Param("driverId") Long driverId, @Param("statuses") Collection<LegStatus> statuses);

    @Query("SELECT COUNT(l) > 0 FROM DeliveryLeg l WHERE l.vehicle.id = :vehicleId AND l.status IN :statuses AND l.active = true")
    boolean existsByVehicleIdAndStatusInAndActiveTrue(@Param("vehicleId") Long vehicleId, @Param("statuses") Collection<LegStatus> statuses);

    @Query("SELECT COUNT(l) > 0 FROM DeliveryLeg l WHERE l.driver.id = :driverId AND l.status IN :statuses AND l.id <> :excludeLegId AND l.active = true")
    boolean existsByDriverIdAndStatusInAndIdNotAndActiveTrue(@Param("driverId") Long driverId, @Param("statuses") Collection<LegStatus> statuses, @Param("excludeLegId") Long excludeLegId);

    @Query("SELECT COUNT(l) > 0 FROM DeliveryLeg l WHERE l.vehicle.id = :vehicleId AND l.status IN :statuses AND l.id <> :excludeLegId AND l.active = true")
    boolean existsByVehicleIdAndStatusInAndIdNotAndActiveTrue(@Param("vehicleId") Long vehicleId, @Param("statuses") Collection<LegStatus> statuses, @Param("excludeLegId") Long excludeLegId);

    List<DeliveryLeg> findByRouteIdOrderByLegOrderAsc(Long routeId);
}
