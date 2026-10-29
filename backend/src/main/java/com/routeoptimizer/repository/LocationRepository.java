
package com.routeoptimizer.repository;

import com.routeoptimizer.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    Optional<Location> findByName(String name);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    List<Location> findByActiveTrue();
    Page<Location> findByActive(Boolean active, Pageable pageable);
    Page<Location> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Location> findByActiveAndNameContainingIgnoreCase(Boolean active, String name, Pageable pageable);
    Optional<Location> findFirstByAddressIgnoreCaseAndCityIgnoreCase(String address, String city);
}
