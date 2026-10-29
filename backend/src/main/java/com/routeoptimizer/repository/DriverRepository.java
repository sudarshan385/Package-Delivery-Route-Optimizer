package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.enums.DriverStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {
    Optional<Driver> findByUserId(Long userId);
    Optional<Driver> findByLicenseNumber(String licenseNumber);
    boolean existsByLicenseNumber(String licenseNumber);
    boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);
    boolean existsByUserId(Long userId);
    boolean existsByUserIdAndIdNot(Long userId, Long id);
    List<Driver> findByStatusAndActiveTrue(DriverStatus status);
    List<Driver> findByActiveTrue();

    Page<Driver> findByStatus(DriverStatus status, Pageable pageable);
    Page<Driver> findByActive(Boolean active, Pageable pageable);
    Page<Driver> findByStatusAndActive(DriverStatus status, Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Driver d WHERE d.id = :id")
    Optional<Driver> findByIdWithLock(@Param("id") Long id);

    long countByActiveTrue();

    long countByStatusAndActiveTrue(DriverStatus status);
}
