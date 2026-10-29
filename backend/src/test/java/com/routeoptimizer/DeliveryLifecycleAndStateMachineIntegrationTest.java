package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.service.DeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class DeliveryLifecycleAndStateMachineIntegrationTest {

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
    private DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;

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
                .username("admin_dispatcher")
                .email("admin@test.com")
                .password("$2a$10$hashedpassword")
                .role(Role.ADMIN)
                .build());

        locA = locationRepository.save(Location.builder().name("Loc A").latitude(12.0).longitude(77.0).build());
        locB = locationRepository.save(Location.builder().name("Loc B").latitude(13.0).longitude(78.0).build());
    }

    private Package createTestPackage(String tracking) {
        return packageRepository.save(Package.builder()
                .trackingNumber(tracking)
                .senderName("Sender")
                .receiverName("Receiver")
                .sourceLocation(locA)
                .destinationLocation(locB)
                .weight(5.0)
                .priority(Priority.MEDIUM)
                .status(PackageStatus.PENDING)
                .build());
    }

    private Driver createTestDriver(String username, String lic) {
        User u = userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .password("pass")
                .role(Role.DRIVER)
                .build());
        return driverRepository.save(Driver.builder()
                .user(u)
                .name("Driver " + username)
                .licenseNumber(lic)
                .status(DriverStatus.AVAILABLE)
                .build());
    }

    private Vehicle createTestVehicle(String reg) {
        return vehicleRepository.save(Vehicle.builder()
                .registrationNumber(reg)
                .vehicleType(VehicleType.VAN)
                .capacity(500.0)
                .status(VehicleStatus.AVAILABLE)
                .build());
    }

    @Test
    @DisplayName("1. Create delivery successfully with correct initial status and package sync")
    void testCreateDeliverySuccessfully() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-001");

        DeliveryResponse response = deliveryService.createDelivery(
                CreateDeliveryRequest.builder().packageId(pkg.getId()).notes("Urgent medical items").build(),
                adminUser.getUsername()
        );

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(response.getSourceLocationId()).isEqualTo(locA.getId());
        assertThat(response.getDestinationLocationId()).isEqualTo(locB.getId());

        // Package status must be PENDING
        Package refreshedPkg = packageRepository.findById(pkg.getId()).orElseThrow();
        assertThat(refreshedPkg.getStatus()).isEqualTo(PackageStatus.PENDING);

        // Status history must be recorded
        List<DeliveryStatusHistoryResponse> history = deliveryService.getDeliveryHistory(response.getId(), adminUser.getUsername());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getNewStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(history.get(0).getPreviousStatus()).isNull();
    }

    @Test
    @DisplayName("2. Reject delivery creation for nonexistent package")
    void testCreateDeliveryNonexistentPackage() {
        setupBaseData();
        assertThrows(ResourceNotFoundException.class, () -> {
            deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(99999L).build(), adminUser.getUsername());
        });
    }

    @Test
    @DisplayName("3. Reject delivery creation when package already has an active delivery")
    void testCreateDeliveryDuplicateActiveDeliveryRejected() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-002");

        deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());

        // Attempting to create a second delivery for the same package must fail
        assertThrows(ConflictException.class, () -> {
            deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        });
    }

    @Test
    @DisplayName("4. Full happy path: CREATED -> ASSIGNED -> IN_PROGRESS -> COMPLETED with package synchronization")
    void testFullHappyPathLifecycle() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-003");
        Driver driver = createTestDriver("happy_drv", "LIC-HAP-1");
        Vehicle vehicle = createTestVehicle("REG-HAP-1");

        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.CREATED);

        // 1. Assign: CREATED -> ASSIGNED
        DeliveryResponse assigned = deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driver.getId()).vehicleId(vehicle.getId()).build(),
                adminUser.getUsername());
        assertThat(assigned.getStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.IN_DELIVERY);

        // 2. Start: ASSIGNED -> IN_PROGRESS
        DeliveryResponse inProgress = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(),
                adminUser.getUsername());
        assertThat(inProgress.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.IN_DELIVERY);

        // 3. Complete: IN_PROGRESS -> COMPLETED
        DeliveryResponse completed = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).reason("Delivered safely").build(),
                adminUser.getUsername());
        assertThat(completed.getStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.DELIVERED);

        // Resources released
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("5. Cancellation from CREATED: CREATED -> CANCELLED")
    void testCancelFromCreated() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-004");
        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());

        DeliveryResponse cancelled = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.CANCELLED).reason("Customer cancelled before assignment").build(),
                adminUser.getUsername());

        assertThat(cancelled.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.PENDING);
    }

    @Test
    @DisplayName("6. Cancellation from ASSIGNED: ASSIGNED -> CANCELLED releases driver and vehicle")
    void testCancelFromAssigned() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-005");
        Driver driver = createTestDriver("cancel_drv", "LIC-CNC-1");
        Vehicle vehicle = createTestVehicle("REG-CNC-1");

        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driver.getId()).vehicleId(vehicle.getId()).build(),
                adminUser.getUsername());

        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        DeliveryResponse cancelled = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.CANCELLED).reason("Weather warning").build(),
                adminUser.getUsername());

        assertThat(cancelled.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.PENDING);
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("7. Failure flow: IN_PROGRESS -> FAILED releases resources and syncs package to FAILED")
    void testFailureFlow() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-006");
        Driver driver = createTestDriver("fail_drv", "LIC-FAL-1");
        Vehicle vehicle = createTestVehicle("REG-FAL-1");

        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driver.getId()).vehicleId(vehicle.getId()).build(),
                adminUser.getUsername());
        deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(),
                adminUser.getUsername());

        DeliveryResponse failed = deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.FAILED).reason("Address unreachable").build(),
                adminUser.getUsername());

        assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(packageRepository.findById(pkg.getId()).orElseThrow().getStatus()).isEqualTo(PackageStatus.FAILED);
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("8. Reject invalid state transitions")
    void testInvalidTransitionsRejected() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-007");
        Driver driver = createTestDriver("inv_drv", "LIC-INV-1");
        Vehicle vehicle = createTestVehicle("REG-INV-1");

        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());

        // CREATED cannot go directly to COMPLETED or IN_PROGRESS or FAILED
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), adminUser.getUsername());
        });
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), adminUser.getUsername());
        });
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.FAILED).build(), adminUser.getUsername());
        });

        // Move to ASSIGNED
        deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driver.getId()).vehicleId(vehicle.getId()).build(),
                adminUser.getUsername());

        // ASSIGNED cannot go directly to COMPLETED or FAILED
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), adminUser.getUsername());
        });
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.FAILED).build(), adminUser.getUsername());
        });

        // Move to IN_PROGRESS
        deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), adminUser.getUsername());

        // Move to COMPLETED
        deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), adminUser.getUsername());

        // From COMPLETED (terminal), cannot go to anything
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), adminUser.getUsername());
        });
        assertThrows(ConflictException.class, () -> {
            deliveryService.updateDeliveryStatus(d.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.CANCELLED).build(), adminUser.getUsername());
        });
    }

    @Test
    @DisplayName("9. DeliveryStatusHistory verifies all transitions and preserves failure isolation")
    void testStatusHistoryVerification() {
        setupBaseData();
        Package pkg = createTestPackage("TRK-008");
        Driver driver = createTestDriver("hist_drv", "LIC-HST-1");
        Vehicle vehicle = createTestVehicle("REG-HST-1");

        DeliveryResponse d = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        deliveryService.assignDelivery(d.getId(),
                AssignDeliveryRequest.builder().driverId(driver.getId()).vehicleId(vehicle.getId()).notes("Assigned by dispatcher").build(),
                adminUser.getUsername());
        deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).reason("Driver started trip").build(),
                adminUser.getUsername());
        deliveryService.updateDeliveryStatus(d.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).reason("Delivered to customer").build(),
                adminUser.getUsername());

        List<DeliveryStatusHistoryResponse> history = deliveryService.getDeliveryHistory(d.getId(), adminUser.getUsername());
        // CREATED, ASSIGNED, IN_PROGRESS, COMPLETED = 4 records
        assertThat(history).hasSize(4);
        assertThat(history.get(0).getNewStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(history.get(1).getNewStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);
        assertThat(history.get(1).getPreviousStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(history.get(2).getNewStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(history.get(2).getPreviousStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(history.get(3).getNewStatus()).isEqualTo(DeliveryStatus.CREATED);
        assertThat(history.get(3).getPreviousStatus()).isNull();
    }
}
