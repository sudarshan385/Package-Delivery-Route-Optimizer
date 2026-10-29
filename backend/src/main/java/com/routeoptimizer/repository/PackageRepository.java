package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<Package, Long> {
    Optional<Package> findByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumber(String trackingNumber);
    boolean existsByTrackingNumberAndIdNot(String trackingNumber, Long id);
    List<Package> findByStatusAndActiveTrue(PackageStatus status);
    List<Package> findByActiveTrue();

    Page<Package> findByStatus(PackageStatus status, Pageable pageable);
    Page<Package> findByPriority(Priority priority, Pageable pageable);
    Page<Package> findByActive(Boolean active, Pageable pageable);
    Page<Package> findByStatusAndActive(PackageStatus status, Boolean active, Pageable pageable);

    Page<Package> findByCustomerId(Long customerId, Pageable pageable);
    Page<Package> findByCustomerIdAndStatus(Long customerId, PackageStatus status, Pageable pageable);
    Page<Package> findByCustomerIdAndPriority(Long customerId, Priority priority, Pageable pageable);
    Page<Package> findByCustomerIdAndActive(Long customerId, Boolean active, Pageable pageable);
    Page<Package> findByCustomerIdAndStatusAndActive(Long customerId, PackageStatus status, Boolean active, Pageable pageable);

    @Query("SELECT COUNT(p) > 0 FROM Package p WHERE (p.sourceLocation.id = :locationId OR p.destinationLocation.id = :locationId) AND p.status IN :statuses AND p.active = true")
    boolean existsActiveByLocationIdAndStatuses(@Param("locationId") Long locationId, @Param("statuses") Collection<PackageStatus> statuses);

    long countByActiveTrue();

    long countByStatusAndActiveTrue(PackageStatus status);

    @Query("SELECT p.status, COUNT(p) FROM Package p WHERE p.active = true GROUP BY p.status")
    List<Object[]> countPackagesByStatus();

    @Query("SELECT p.status, COUNT(p) FROM Package p WHERE p.active = true AND p.createdAt >= :startDate AND p.createdAt <= :endDate GROUP BY p.status")
    List<Object[]> countPackagesByStatusBetween(@Param("startDate") java.time.OffsetDateTime startDate, @Param("endDate") java.time.OffsetDateTime endDate);

    @Query("SELECT COUNT(p) FROM Package p WHERE p.active = true AND p.createdAt >= :startDate AND p.createdAt <= :endDate")
    long countPackagesBetween(@Param("startDate") java.time.OffsetDateTime startDate, @Param("endDate") java.time.OffsetDateTime endDate);
}
