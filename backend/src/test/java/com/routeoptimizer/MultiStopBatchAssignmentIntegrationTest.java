package com.routeoptimizer;

import com.routeoptimizer.dto.BatchAssignmentRequest;
import com.routeoptimizer.dto.BatchAssignmentResponse;
import com.routeoptimizer.dto.DeliveryResponse;
import com.routeoptimizer.dto.DeliveryStatusUpdateRequest;
import com.routeoptimizer.dto.MultiStopOptimizationRequest;
import com.routeoptimizer.dto.MultiStopOptimizationResponse;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.service.DeliveryService;
import com.routeoptimizer.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiStopBatchAssignmentIntegrationTest {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private RouteService routeService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationEdgeRepository locationEdgeRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location hub;
    private Location customerA;
    private Location customerB;
    private Vehicle van;
    private Driver driver;
    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        adminUser = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("admin")
                        .password("$2a$10$abcdefghijklmnopqrstuv")
                        .email("admin@test.com")
                        .fullName("Administrator")
                        .role(Role.ADMIN)
                        .isVerified(true)
                        .isBlocked(false)
                        .build())
        );

        regularUser = userRepository.findByUsername("testuser").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("testuser")
                        .password("$2a$10$abcdefghijklmnopqrstuv")
                        .email("testuser@test.com")
                        .fullName("Test Customer")
                        .role(Role.USER)
                        .isVerified(true)
                        .isBlocked(false)
                        .build())
        );

        hub = locationRepository.save(Location.builder()
                .name("Test Central Hub " + System.nanoTime())
                .address("Hub Address")
                .latitude(12.9716)
                .longitude(77.5946)
                .locationType("HUB")
                .build());

        customerA = locationRepository.save(Location.builder()
                .name("Customer A " + System.nanoTime())
                .address("Customer Address A")
                .latitude(12.9800)
                .longitude(77.6000)
                .locationType("CUSTOMER")
                .build());

        customerB = locationRepository.save(Location.builder()
                .name("Customer B " + System.nanoTime())
                .address("Customer Address B")
                .latitude(12.9900)
                .longitude(77.6100)
                .locationType("CUSTOMER")
                .build());

        // Road edges
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(hub).destinationLocation(customerA).distance(5.0).estimatedTime(10.0).build());
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerA).destinationLocation(customerB).distance(4.0).estimatedTime(8.0).build());
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerB).destinationLocation(hub).distance(8.0).estimatedTime(15.0).build());

        // Vehicle capacity: 15.0 kg
        van = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-MS-VAN-" + System.nanoTime())
                .capacity(15.0)
                .vehicleType(VehicleType.VAN)
                .status(VehicleStatus.AVAILABLE)
                .build());

        // Driver
        driver = driverRepository.save(Driver.builder()
                .name("Driver " + System.nanoTime())
                .licenseNumber("LIC-" + System.nanoTime())
                .phone("+919876543210")
                .status(DriverStatus.AVAILABLE)
                .build());
    }

    private Delivery createDelivery(String trackingNum, Location dest, double weightKg) {
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber(trackingNum + "-" + System.nanoTime())
                .senderName("Central Hub Warehouse")
                .receiverName("Customer Receiver")
                .sourceLocation(hub)
                .destinationLocation(dest)
                .weight(weightKg)
                .status(PackageStatus.PENDING)
                .build());

        return deliveryRepository.save(Delivery.builder()
                .pkg(pkg)
                .sourceLocation(hub)
                .destinationLocation(dest)
                .status(DeliveryStatus.CREATED)
                .build());
    }

    @Test
    @DisplayName("1. Batch assignment atomically assigns multi-stop manifest to driver and vehicle")
    void testSuccessfulBatchAssignment() {
        Delivery d1 = createDelivery("PKG-BA-1", customerA, 4.0);
        Delivery d2 = createDelivery("PKG-BA-2", customerB, 5.0);

        // Optimize and persist route
        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .build();
        MultiStopOptimizationResponse optRes = routeService.optimizeAndPersistMultiDeliveryRoute(optReq, "admin");

        // Batch assign
        BatchAssignmentRequest assignReq = BatchAssignmentRequest.builder()
                .routeId(optRes.getRouteId())
                .driverId(driver.getId())
                .vehicleId(van.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .notes("Dispatched for morning run")
                .build();

        BatchAssignmentResponse assignRes = deliveryService.batchAssignDeliveries(assignReq, "admin");

        assertThat(assignRes).isNotNull();
        assertThat(assignRes.getStatus()).isEqualTo("ASSIGNED");
        assertThat(assignRes.getTotalWeightKg()).isEqualTo(9.0);
        assertThat(assignRes.getAssignedDeliveryIds()).containsExactlyInAnyOrder(d1.getId(), d2.getId());

        // Verify Driver and Vehicle statuses
        Driver reloadedDriver = driverRepository.findById(driver.getId()).orElseThrow();
        assertThat(reloadedDriver.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        Vehicle reloadedVehicle = vehicleRepository.findById(van.getId()).orElseThrow();
        assertThat(reloadedVehicle.getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // Verify Deliveries
        Delivery reloadedD1 = deliveryRepository.findById(d1.getId()).orElseThrow();
        Delivery reloadedD2 = deliveryRepository.findById(d2.getId()).orElseThrow();
        assertThat(reloadedD1.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(reloadedD1.getDriver().getId()).isEqualTo(driver.getId());
        assertThat(reloadedD1.getVehicle().getId()).isEqualTo(van.getId());
        assertThat(reloadedD1.getPackage().getStatus()).isEqualTo(PackageStatus.IN_DELIVERY);

        assertThat(reloadedD2.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(reloadedD2.getDriver().getId()).isEqualTo(driver.getId());
        assertThat(reloadedD2.getVehicle().getId()).isEqualTo(van.getId());
    }

    @Test
    @DisplayName("2. Batch assignment rejects payload exceeding vehicle capacity")
    void testBatchAssignmentExceedsVehicleCapacity() {
        // Total 20.0 kg > Van capacity 15.0 kg
        Delivery d1 = createDelivery("PKG-CAP-1", customerA, 10.0);
        Delivery d2 = createDelivery("PKG-CAP-2", customerB, 10.0);

        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();
        MultiStopOptimizationResponse optRes = routeService.optimizeAndPersistMultiDeliveryRoute(optReq, "admin");

        BatchAssignmentRequest assignReq = BatchAssignmentRequest.builder()
                .routeId(optRes.getRouteId())
                .driverId(driver.getId())
                .vehicleId(van.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                deliveryService.batchAssignDeliveries(assignReq, "admin")
        );

        assertThat(ex.getMessage()).contains("exceeds vehicle payload capacity");

        // Verify resources remain AVAILABLE
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicleRepository.findById(van.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("3. Batch assignment rejects unavailable driver or vehicle")
    void testBatchAssignmentRejectsUnavailableResources() {
        driver.setStatus(DriverStatus.ON_DELIVERY);
        driverRepository.save(driver);

        Delivery d1 = createDelivery("PKG-UNAV-1", customerA, 3.0);
        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();
        MultiStopOptimizationResponse optRes = routeService.optimizeAndPersistMultiDeliveryRoute(optReq, "admin");

        BatchAssignmentRequest assignReq = BatchAssignmentRequest.builder()
                .routeId(optRes.getRouteId())
                .driverId(driver.getId())
                .vehicleId(van.getId())
                .deliveryIds(List.of(d1.getId()))
                .build();

        assertThrows(ConflictException.class, () ->
                deliveryService.batchAssignDeliveries(assignReq, "admin")
        );
    }

    @Test
    @DisplayName("4. Completing one delivery does NOT release driver/vehicle until ALL deliveries complete")
    void testDelayedResourceReleaseAcrossManifest() {
        Delivery d1 = createDelivery("PKG-REL-1", customerA, 3.0);
        Delivery d2 = createDelivery("PKG-REL-2", customerB, 4.0);

        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .build();
        MultiStopOptimizationResponse optRes = routeService.optimizeAndPersistMultiDeliveryRoute(optReq, "admin");

        BatchAssignmentRequest assignReq = BatchAssignmentRequest.builder()
                .routeId(optRes.getRouteId())
                .driverId(driver.getId())
                .vehicleId(van.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .build();
        deliveryService.batchAssignDeliveries(assignReq, "admin");

        // Driver progresses Delivery 1: ASSIGNED -> IN_PROGRESS -> COMPLETED
        deliveryService.updateDeliveryStatus(d1.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), "admin");
        deliveryService.updateDeliveryStatus(d1.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), "admin");

        // CRITICAL CHECK: Delivery 1 is COMPLETED, but Delivery 2 is still ASSIGNED!
        // Driver must remain ON_DELIVERY and Vehicle must remain IN_USE!
        Driver checkDriver1 = driverRepository.findById(driver.getId()).orElseThrow();
        Vehicle checkVehicle1 = vehicleRepository.findById(van.getId()).orElseThrow();
        assertThat(checkDriver1.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(checkVehicle1.getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // Driver progresses Delivery 2: ASSIGNED -> IN_PROGRESS -> COMPLETED
        deliveryService.updateDeliveryStatus(d2.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), "admin");
        deliveryService.updateDeliveryStatus(d2.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), "admin");

        // ALL deliveries on the manifest are now terminal!
        // Driver and Vehicle must now be released to AVAILABLE!
        Driver checkDriver2 = driverRepository.findById(driver.getId()).orElseThrow();
        Vehicle checkVehicle2 = vehicleRepository.findById(van.getId()).orElseThrow();
        assertThat(checkDriver2.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(checkVehicle2.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("5. Non-ADMIN role is rejected from batch assignment")
    void testNonAdminCannotBatchAssign() {
        Delivery d1 = createDelivery("PKG-SEC-1", customerA, 2.0);
        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();
        MultiStopOptimizationResponse optRes = routeService.optimizeAndPersistMultiDeliveryRoute(optReq, "admin");

        BatchAssignmentRequest assignReq = BatchAssignmentRequest.builder()
                .routeId(optRes.getRouteId())
                .driverId(driver.getId())
                .vehicleId(van.getId())
                .deliveryIds(List.of(d1.getId()))
                .build();

        assertThrows(AccessDeniedException.class, () ->
                deliveryService.batchAssignDeliveries(assignReq, "testuser")
        );
    }

    @Test
    @DisplayName("6. Loading unassigned eligible deliveries filtered by hub")
    void testGetUnassignedDeliveriesByHub() {
        Delivery unassigned1 = createDelivery("PKG-UNASS-1", customerA, 2.0);
        Delivery unassigned2 = createDelivery("PKG-UNASS-2", customerB, 3.0);

        List<DeliveryResponse> unassigned = deliveryService.getUnassignedDeliveriesByHub(hub.getId());
        List<Long> ids = unassigned.stream().map(DeliveryResponse::getId).toList();

        assertThat(ids).contains(unassigned1.getId(), unassigned2.getId());
    }
}
