package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.enums.DeliveryStatus;
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
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    List<Delivery> findByStatusAndActiveTrue(DeliveryStatus status);
    List<Delivery> findByDriverIdAndActiveTrue(Long driverId);
    List<Delivery> findByVehicleIdAndActiveTrue(Long vehicleId);
    List<Delivery> findByPkgCustomerIdAndActiveTrue(Long customerId);
    List<Delivery> findByActiveTrue();
    List<Delivery> findByRouteIdAndActiveTrue(Long routeId);

    @Query("SELECT d FROM Delivery d WHERE d.sourceLocation.id = :hubId AND d.status = :status AND d.active = true AND d.route IS NULL ORDER BY d.id ASC")
    List<Delivery> findEligibleUnassignedByHub(@Param("hubId") Long hubId, @Param("status") DeliveryStatus status);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.route.id = :routeId AND d.id != :deliveryId AND d.status NOT IN :terminalStatuses AND d.active = true")
    boolean existsActiveNonTerminalByRouteIdExcluding(@Param("routeId") Long routeId, @Param("deliveryId") Long deliveryId, @Param("terminalStatuses") Collection<DeliveryStatus> terminalStatuses);

    @Query("SELECT d FROM Delivery d JOIN FETCH d.route r WHERE d.driver.id = :driverId AND d.status IN :activeStatuses AND d.active = true AND r.isCurrent = true ORDER BY d.id ASC")
    List<Delivery> findActiveDeliveriesByDriverId(@Param("driverId") Long driverId, @Param("activeStatuses") Collection<DeliveryStatus> activeStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Delivery d WHERE d.id = :id")
    Optional<Delivery> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.pkg.id = :packageId AND d.status IN :statuses AND d.active = true")
    boolean existsByPackageIdAndStatusInAndActiveTrue(@Param("packageId") Long packageId, @Param("statuses") Collection<DeliveryStatus> statuses);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.driver.id = :driverId AND d.status IN :statuses AND d.active = true")
    boolean existsByDriverIdAndStatusInAndActiveTrue(@Param("driverId") Long driverId, @Param("statuses") Collection<DeliveryStatus> statuses);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE d.vehicle.id = :vehicleId AND d.status IN :statuses AND d.active = true")
    boolean existsByVehicleIdAndStatusInAndActiveTrue(@Param("vehicleId") Long vehicleId, @Param("statuses") Collection<DeliveryStatus> statuses);

    @Query("SELECT COUNT(d) > 0 FROM Delivery d WHERE (d.sourceLocation.id = :locationId OR d.destinationLocation.id = :locationId) AND d.status IN :statuses AND d.active = true")
    boolean existsActiveByLocationIdAndStatuses(@Param("locationId") Long locationId, @Param("statuses") Collection<DeliveryStatus> statuses);

    long countByActiveTrue();

    long countByStatusAndActiveTrue(DeliveryStatus status);

    @Query("SELECT d.status, COUNT(d) FROM Delivery d WHERE d.active = true GROUP BY d.status")
    List<Object[]> countDeliveriesByStatus();

    @Query("SELECT d.status, COUNT(d) FROM Delivery d WHERE d.active = true AND d.createdAt >= :startDate AND d.createdAt <= :endDate GROUP BY d.status")
    List<Object[]> countDeliveriesByStatusBetween(@Param("startDate") java.time.OffsetDateTime startDate, @Param("endDate") java.time.OffsetDateTime endDate);

    @Query("SELECT COUNT(d) FROM Delivery d WHERE d.active = true AND d.createdAt >= :startDate AND d.createdAt <= :endDate")
    long countDeliveriesBetween(@Param("startDate") java.time.OffsetDateTime startDate, @Param("endDate") java.time.OffsetDateTime endDate);

    @Query("SELECT d FROM Delivery d LEFT JOIN FETCH d.pkg LEFT JOIN FETCH d.driver LEFT JOIN FETCH d.vehicle WHERE d.active = true ORDER BY d.id ASC")
    List<Delivery> findAllActiveForReport();
}
