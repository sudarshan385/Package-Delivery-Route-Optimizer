package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.service.DeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class DeliveryAssignmentAndConcurrencyIntegrationTest {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "delivery_status_history",
            "route_edges",
            "route_stops",
            "routes",
            "deliveries",
            "packages",
            "location_edges",
            "locations",
            "vehicles",
            "drivers",
            "users"
    );

    private User adminUser;
    private Location locA;
    private Location locB;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    private void setupBaseData() {
        adminUser = userRepository.save(User.builder()
                .username("admin_ops")
                .email("admin_ops@test.com")
                .password("$2a$10$hashedpassword")
                .role(Role.ADMIN)
                .build());

        locA = locationRepository.save(Location.builder().name("Loc Hub 1").latitude(12.0).longitude(77.0).build());
        locB = locationRepository.save(Location.builder().name("Loc Hub 2").latitude(13.0).longitude(78.0).build());
    }

    private Package createPackage(String trk) {
        return packageRepository.save(Package.builder()
                .trackingNumber(trk)
                .senderName("S")
                .receiverName("R")
                .sourceLocation(locA)
                .destinationLocation(locB)
                .weight(2.0)
                .build());
    }

    private Driver createDriver(String username, String lic, boolean active, DriverStatus status) {
        User u = User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("pass")
                .role(Role.DRIVER)
                .build();
        u.setActive(active);
        u = userRepository.save(u);

        Driver d = Driver.builder()
                .user(u)
                .name("Driver " + username)
                .licenseNumber(lic)
                .status(status)
                .build();
        d.setActive(active);
        return driverRepository.save(d);
    }

    private Vehicle createVehicle(String reg, boolean active, VehicleStatus status) {
        Vehicle v = Vehicle.builder()
                .registrationNumber(reg)
                .vehicleType(VehicleType.TRUCK)
                .capacity(1000.0)
                .status(status)
                .build();
        v.setActive(active);
        return vehicleRepository.save(v);
    }

    @Test
    @DisplayName("21-26. Validate assignment constraints (Available, Inactive, Unavailable)")
    void testAssignmentValidationConstraints() {
        setupBaseData();
        Package p1 = createPackage("TRK-ASN-1");
        DeliveryResponse del = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p1.getId()).build(), adminUser.getUsername());

        // Inactive driver rejected
        Driver inactDriver = createDriver("inact_drv", "LIC-INA", false, DriverStatus.AVAILABLE);
        Vehicle veh1 = createVehicle("REG-ASN-1", true, VehicleStatus.AVAILABLE);
        assertThrows(BadRequestException.class, () -> {
            deliveryService.assignDelivery(del.getId(), AssignDeliveryRequest.builder().driverId(inactDriver.getId()).vehicleId(veh1.getId()).build(), adminUser.getUsername());
        });

        // Unavailable driver rejected (ON_DELIVERY)
        Driver unavailDriver = createDriver("unavail_drv", "LIC-UNA", true, DriverStatus.ON_DELIVERY);
        assertThrows(ConflictException.class, () -> {
            deliveryService.assignDelivery(del.getId(), AssignDeliveryRequest.builder().driverId(unavailDriver.getId()).vehicleId(veh1.getId()).build(), adminUser.getUsername());
        });

        // Inactive vehicle rejected
        Driver validDriver = createDriver("valid_drv", "LIC-VAL", true, DriverStatus.AVAILABLE);
        Vehicle inactVehicle = createVehicle("REG-INA", false, VehicleStatus.AVAILABLE);
        assertThrows(BadRequestException.class, () -> {
            deliveryService.assignDelivery(del.getId(), AssignDeliveryRequest.builder().driverId(validDriver.getId()).vehicleId(inactVehicle.getId()).build(), adminUser.getUsername());
        });

        // Unavailable vehicle rejected (IN_USE)
        Vehicle unavailVehicle = createVehicle("REG-UNA", true, VehicleStatus.IN_USE);
        assertThrows(ConflictException.class, () -> {
            deliveryService.assignDelivery(del.getId(), AssignDeliveryRequest.builder().driverId(validDriver.getId()).vehicleId(unavailVehicle.getId()).build(), adminUser.getUsername());
        });

        // Valid assignment succeeds for Admin
        DeliveryResponse assigned = deliveryService.assignDelivery(del.getId(),
                AssignDeliveryRequest.builder().driverId(validDriver.getId()).vehicleId(veh1.getId()).build(),
                adminUser.getUsername());

        assertThat(assigned.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(driverRepository.findById(validDriver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(vehicleRepository.findById(veh1.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.IN_USE);
    }

    @Test
    @DisplayName("32-35. Concurrency protection: simultaneous threads cannot double-book the same driver")
    void testConcurrentAssignmentCannotDoubleBookDriver() throws Exception {
        setupBaseData();
        Driver sharedDriver = createDriver("shared_driver", "LIC-SHARED", true, DriverStatus.AVAILABLE);

        int concurrentRequests = 10;
        List<DeliveryResponse> deliveries = new ArrayList<>();
        List<Vehicle> vehicles = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Package p = createPackage("TRK-CONC-D-" + i);
            deliveries.add(deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p.getId()).build(), adminUser.getUsername()));
            vehicles.add(createVehicle("REG-CONC-D-" + i, true, VehicleStatus.AVAILABLE));
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CyclicBarrier barrier = new CyclicBarrier(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    deliveryService.assignDelivery(
                            deliveries.get(index).getId(),
                            AssignDeliveryRequest.builder().driverId(sharedDriver.getId()).vehicleId(vehicles.get(index).getId()).build(),
                            adminUser.getUsername()
                    );
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected exception
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly one assignment must succeed, all other 9 must receive 409 Conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(concurrentRequests - 1);
        assertThat(driverRepository.findById(sharedDriver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
    }

    @Test
    @DisplayName("33. Concurrency protection: simultaneous threads cannot double-book the same vehicle")
    void testConcurrentAssignmentCannotDoubleBookVehicle() throws Exception {
        setupBaseData();
        Vehicle sharedVehicle = createVehicle("REG-SHARED", true, VehicleStatus.AVAILABLE);

        int concurrentRequests = 10;
        List<DeliveryResponse> deliveries = new ArrayList<>();
        List<Driver> drivers = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Package p = createPackage("TRK-CONC-V-" + i);
            deliveries.add(deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p.getId()).build(), adminUser.getUsername()));
            drivers.add(createDriver("conc_drv_" + i, "LIC-CONC-V-" + i, true, DriverStatus.AVAILABLE));
        }

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CyclicBarrier barrier = new CyclicBarrier(concurrentRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    deliveryService.assignDelivery(
                            deliveries.get(index).getId(),
                            AssignDeliveryRequest.builder().driverId(drivers.get(index).getId()).vehicleId(sharedVehicle.getId()).build(),
                            adminUser.getUsername()
                    );
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected exception
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly one assignment must succeed, all other 9 must receive 409 Conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(concurrentRequests - 1);
        assertThat(vehicleRepository.findById(sharedVehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.IN_USE);
    }

    @Test
    @DisplayName("40-43. Driver-specific delivery authorization enforcement")
    void testDriverSpecificDeliveryActions() {
        setupBaseData();
        Driver driverA = createDriver("assigned_driver_a", "LIC-DRV-A", true, DriverStatus.AVAILABLE);
        Driver driverB = createDriver("assigned_driver_b", "LIC-DRV-B", true, DriverStatus.AVAILABLE);
        Vehicle veh1 = createVehicle("REG-DRV-1", true, VehicleStatus.AVAILABLE);

        Package p = createPackage("TRK-DRV-AUTH");
        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p.getId()).build(), adminUser.getUsername());

        // Assign to Driver A
        deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driverA.getId()).vehicleId(veh1.getId()).build(),
                adminUser.getUsername());

        // Driver B cannot start Driver A's delivery -> 403 AccessDenied
        assertThrows(AccessDeniedException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), "assigned_driver_b");
        });

        // Driver A can start own delivery -> 200 OK
        DeliveryResponse inProgress = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(),
                "assigned_driver_a");
        assertThat(inProgress.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);

        // Driver B cannot complete Driver A's delivery -> 403 AccessDenied
        assertThrows(AccessDeniedException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), "assigned_driver_b");
        });

        // Driver A cannot cancel delivery -> 403 AccessDenied
        assertThrows(AccessDeniedException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.CANCELLED).build(), "assigned_driver_a");
        });

        // Driver A can complete own delivery -> 200 OK
        DeliveryResponse completed = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).reason("Signed by customer").build(),
                "assigned_driver_a");
        assertThat(completed.getStatus()).isEqualTo(DeliveryStatus.COMPLETED);
    }
}
