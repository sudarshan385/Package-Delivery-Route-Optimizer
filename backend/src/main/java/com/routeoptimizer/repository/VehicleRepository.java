package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Vehicle;
import com.routeoptimizer.enums.VehicleStatus;
import com.routeoptimizer.enums.VehicleType;
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
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long id);
    List<Vehicle> findByStatusAndActiveTrue(VehicleStatus status);
    List<Vehicle> findByActiveTrue();

    Page<Vehicle> findByStatus(VehicleStatus status, Pageable pageable);
    Page<Vehicle> findByVehicleType(VehicleType vehicleType, Pageable pageable);
    Page<Vehicle> findByActive(Boolean active, Pageable pageable);
    Page<Vehicle> findByStatusAndActive(VehicleStatus status, Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdWithLock(@Param("id") Long id);

    long countByActiveTrue();

    long countByStatusAndActiveTrue(VehicleStatus status);
}
