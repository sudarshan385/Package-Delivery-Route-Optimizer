package com.routeoptimizer;

import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class JpaDomainModelAndPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationEdgeRepository locationEdgeRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryStatusHistoryRepository deliveryStatusHistoryRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private RouteEdgeRepository routeEdgeRepository;

    @Autowired
    private EntityManager entityManager;

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

    @BeforeEach
    @AfterEach
    void cleanTables() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    @Test
    @DisplayName("1. Verify every JPA entity can be initialized and persisted against V1 schema")
    void testAllEntitiesCanBeInitializedAgainstV1Schema() {
        // 1. User
        User user = userRepository.save(User.builder()
                .username("persisted_user")
                .email("persisted@routeoptimizer.com")
                .password("$2a$10$hashedpassword")
                .fullName("Persisted User")
                .role(Role.DRIVER)
                .build());
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getVersion()).isEqualTo(0L);

        // 2. Driver
        Driver driver = driverRepository.save(Driver.builder()
                .user(user)
                .name("Driver John")
                .phone("1234567890")
                .licenseNumber("LIC-TEST-001")
                .status(DriverStatus.AVAILABLE)
                .build());
        assertThat(driver.getId()).isNotNull();

        // 3. Vehicle
        Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("REG-TEST-100")
                .vehicleType(VehicleType.VAN)
                .capacity(500.0)
                .status(VehicleStatus.AVAILABLE)
                .build());
        assertThat(vehicle.getId()).isNotNull();

        // 4. Locations
        Location locA = locationRepository.save(Location.builder()
                .name("Central Hub A")
                .address("100 Main St")
                .latitude(12.9716)
                .longitude(77.5946)
                .locationType("WAREHOUSE")
                .build());
        Location locB = locationRepository.save(Location.builder()
                .name("Delivery Point B")
                .address("200 Cross Rd")
                .latitude(12.9816)
                .longitude(77.6046)
                .locationType("CUSTOMER")
                .build());
        assertThat(locA.getId()).isNotNull();
        assertThat(locB.getId()).isNotNull();

        // 5. LocationEdge
        LocationEdge edge = locationEdgeRepository.save(LocationEdge.builder()
                .sourceLocation(locA)
                .destinationLocation(locB)
                .distance(12.5)
                .estimatedTime(25.0)
                .build());
        assertThat(edge.getId()).isNotNull();

        // 6. Package
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-TEST-9001")
                .senderName("Alice Sender")
                .receiverName("Bob Receiver")
                .sourceLocation(locA)
                .destinationLocation(locB)
                .weight(5.5)
                .priority(Priority.HIGH)
                .status(PackageStatus.PENDING)
                .build());
        assertThat(pkg.getId()).isNotNull();

        // 7. Delivery
        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg)
                .sourceLocation(locA)
                .destinationLocation(locB)
                .driver(driver)
                .vehicle(vehicle)
                .status(DeliveryStatus.CREATED)
                .estimatedDistance(12.5)
                .estimatedTime(25.0)
                .build());
        assertThat(delivery.getId()).isNotNull();

        // 8. DeliveryStatusHistory
        DeliveryStatusHistory history = deliveryStatusHistoryRepository.save(DeliveryStatusHistory.builder()
                .delivery(delivery)
                .previousStatus(null)
                .newStatus(DeliveryStatus.CREATED)
                .changedByUser(user)
                .reason("Initial order creation")
                .build());
        assertThat(history.getId()).isNotNull();
        assertThat(history.getCreatedAt()).isNotNull();

        // 9. Route
        Route route = routeRepository.save(Route.builder()
                .delivery(delivery)
                .sourceLocation(locA)
                .destinationLocation(locB)
                .versionNumber(1)
                .isCurrent(true)
                .totalDistance(12.5)
                .estimatedTime(25.0)
                .algorithmUsed("DIJKSTRA")
                .build());
        assertThat(route.getId()).isNotNull();

        // 10. RouteStop
        RouteStop stop1 = routeStopRepository.save(RouteStop.builder()
                .route(route)
                .location(locA)
                .stopOrder(1)
                .stopType(StopType.SOURCE)
                .build());
        RouteStop stop2 = routeStopRepository.save(RouteStop.builder()
                .route(route)
                .location(locB)
                .stopOrder(2)
                .stopType(StopType.DESTINATION)
                .build());
        assertThat(stop1.getId()).isNotNull();
        assertThat(stop2.getId()).isNotNull();

        // 11. RouteEdge
        RouteEdge routeEdge = routeEdgeRepository.save(RouteEdge.fromLocationEdge(route, 1, edge));
        assertThat(routeEdge.getId()).isNotNull();
        assertThat(routeEdge.getDistance()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("2. Verify Hibernate schema validation succeeds")
    void testHibernateSchemaValidationSucceeds() {
        assertThat(entityManager).isNotNull();
        assertThat(entityManager.isOpen()).isTrue();
    }

    @Test
    @DisplayName("3. Verify User <-> Driver relationship and uniqueness constraint")
    void testUserDriverRelationship() {
        User user1 = userRepository.save(User.builder()
                .username("driver_usr_1")
                .email("driver1@test.com")
                .password("$2a$10$hashedpassword")
                .role(Role.DRIVER)
                .build());

        Driver driver1 = driverRepository.save(Driver.builder()
                .user(user1)
                .name("Driver 1")
                .licenseNumber("LIC-DRV-1")
                .build());

        assertThat(driver1.getUser().getId()).isEqualTo(user1.getId());

        // Attempting to attach another driver to the same user must violate unique constraint
        Driver duplicateDriver = Driver.builder()
                .user(user1)
                .name("Driver Duplicate")
                .licenseNumber("LIC-DRV-2")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            driverRepository.saveAndFlush(duplicateDriver);
        });
    }

    @Test
    @DisplayName("4. Verify Package <-> Location relationships")
    void testPackageLocationRelationships() {
        Location origin = locationRepository.save(Location.builder()
                .name("Origin Hub")
                .latitude(10.0)
                .longitude(20.0)
                .build());
        Location destination = locationRepository.save(Location.builder()
                .name("Destination Hub")
                .latitude(11.0)
                .longitude(21.0)
                .build());

        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-REL-001")
                .senderName("Sender")
                .receiverName("Receiver")
                .sourceLocation(origin)
                .destinationLocation(destination)
                .weight(10.0)
                .build());

        Package retrieved = packageRepository.findById(pkg.getId()).orElseThrow();
        assertThat(retrieved.getSourceLocation().getId()).isEqualTo(origin.getId());
        assertThat(retrieved.getDestinationLocation().getId()).isEqualTo(destination.getId());
    }

    @Test
    @DisplayName("5. Verify Delivery <-> Package relationship and authoritative location syncing")
    void testDeliveryPackageRelationship() {
        Location loc1 = locationRepository.save(Location.builder().name("Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Loc 2").latitude(11.0).longitude(21.0).build());

        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-DEL-001")
                .senderName("S")
                .receiverName("R")
                .sourceLocation(loc1)
                .destinationLocation(loc2)
                .weight(2.0)
                .build());

        Delivery delivery = new Delivery();
        delivery.setPackage(pkg); // Should auto-populate sourceLocation and destinationLocation from package

        Delivery saved = deliveryRepository.save(delivery);
        assertThat(saved.getPackage().getId()).isEqualTo(pkg.getId());
        assertThat(saved.getSourceLocation().getId()).isEqualTo(loc1.getId());
        assertThat(saved.getDestinationLocation().getId()).isEqualTo(loc2.getId());
    }

    @Test
    @Transactional
    @DisplayName("6. Verify DeliveryStatusHistory <-> Delivery relationship")
    void testDeliveryStatusHistoryDeliveryRelationship() {
        Location loc1 = locationRepository.save(Location.builder().name("Loc H1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Loc H2").latitude(11.0).longitude(21.0).build());
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-HIST-001").senderName("S").receiverName("R")
                .sourceLocation(loc1).destinationLocation(loc2).weight(1.0).build());
        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg).sourceLocation(loc1).destinationLocation(loc2).status(DeliveryStatus.CREATED).build());

        User admin = userRepository.save(User.builder()
                .username("hist_admin").email("hist@admin.com").password("pass").role(Role.ADMIN).build());

        deliveryStatusHistoryRepository.save(DeliveryStatusHistory.builder()
                .delivery(delivery)
                .previousStatus(DeliveryStatus.CREATED)
                .newStatus(DeliveryStatus.ASSIGNED)
                .changedByUser(admin)
                .reason("Driver assigned")
                .build());

        List<DeliveryStatusHistory> historyList = deliveryStatusHistoryRepository.findByDeliveryIdOrderByCreatedAtDesc(delivery.getId());
        assertThat(historyList).hasSize(1);
        assertThat(historyList.get(0).getNewStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(historyList.get(0).getChangedBy().getUsername()).isEqualTo("hist_admin");
    }

    @Test
    @DisplayName("7. Verify Route <-> Delivery relationship")
    void testRouteDeliveryRelationship() {
        Location loc1 = locationRepository.save(Location.builder().name("Route Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Route Loc 2").latitude(11.0).longitude(21.0).build());
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-RTE-001").senderName("S").receiverName("R")
                .sourceLocation(loc1).destinationLocation(loc2).weight(1.0).build());
        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg).sourceLocation(loc1).destinationLocation(loc2).build());

        Route route = routeRepository.save(Route.builder()
                .delivery(delivery)
                .sourceLocation(loc1)
                .destinationLocation(loc2)
                .totalDistance(15.0)
                .versionNumber(1)
                .isCurrent(true)
                .build());

        assertThat(route.getDelivery().getId()).isEqualTo(delivery.getId());
    }

    @Test
    @DisplayName("8. Verify RouteStop <-> Route and Location relationships and order uniqueness")
    void testRouteStopRouteAndLocationRelationships() {
        Location loc1 = locationRepository.save(Location.builder().name("Stop Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Stop Loc 2").latitude(11.0).longitude(21.0).build());
        Route route = routeRepository.save(Route.builder()
                .sourceLocation(loc1).destinationLocation(loc2).totalDistance(10.0).build());

        RouteStop stop1 = routeStopRepository.save(RouteStop.builder()
                .route(route).location(loc1).stopOrder(1).stopType(StopType.SOURCE).build());
        assertThat(stop1.getId()).isNotNull();

        // Duplicate stop_order on same route must violate uq_route_stop_order
        RouteStop duplicateStop = RouteStop.builder()
                .route(route).location(loc2).stopOrder(1).stopType(StopType.DESTINATION).build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            routeStopRepository.saveAndFlush(duplicateStop);
        });
    }

    @Test
    @DisplayName("9. Verify RouteEdge <-> Route and LocationEdge snapshot relationships and order uniqueness")
    void testRouteEdgeRouteAndLocationEdgeRelationships() {
        Location loc1 = locationRepository.save(Location.builder().name("Edge Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Edge Loc 2").latitude(11.0).longitude(21.0).build());
        LocationEdge graphEdge = locationEdgeRepository.save(LocationEdge.builder()
                .sourceLocation(loc1).destinationLocation(loc2).distance(8.0).estimatedTime(12.0).build());

        Route route = routeRepository.save(Route.builder()
                .sourceLocation(loc1).destinationLocation(loc2).totalDistance(8.0).build());

        RouteEdge routeEdge1 = routeEdgeRepository.save(RouteEdge.fromLocationEdge(route, 1, graphEdge));
        assertThat(routeEdge1.getId()).isNotNull();
        assertThat(routeEdge1.getDistance()).isEqualTo(graphEdge.getDistance());

        // Duplicate edge_order on same route must violate uq_route_edge_order
        RouteEdge duplicateEdge = RouteEdge.fromLocationEdge(route, 1, graphEdge);
        assertThrows(DataIntegrityViolationException.class, () -> {
            routeEdgeRepository.saveAndFlush(duplicateEdge);
        });
    }

    @Test
    @DisplayName("10. Verify Route version uniqueness lookup")
    void testRouteVersionUniqueness() {
        Location loc1 = locationRepository.save(Location.builder().name("Ver Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Ver Loc 2").latitude(11.0).longitude(21.0).build());
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-VER-001").senderName("S").receiverName("R")
                .sourceLocation(loc1).destinationLocation(loc2).weight(1.0).build());
        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg).sourceLocation(loc1).destinationLocation(loc2).build());

        routeRepository.save(Route.builder()
                .delivery(delivery).sourceLocation(loc1).destinationLocation(loc2).totalDistance(10.0).versionNumber(1).isCurrent(false).build());
        routeRepository.save(Route.builder()
                .delivery(delivery).sourceLocation(loc1).destinationLocation(loc2).totalDistance(9.0).versionNumber(2).isCurrent(true).build());

        Optional<Route> foundV1 = routeRepository.findByDeliveryIdAndVersionNumber(delivery.getId(), 1);
        Optional<Route> foundV2 = routeRepository.findByDeliveryIdAndVersionNumber(delivery.getId(), 2);
        assertThat(foundV1).isPresent();
        assertThat(foundV2).isPresent();
        assertThat(foundV1.get().getTotalDistance()).isEqualTo(10.0);
        assertThat(foundV2.get().getTotalDistance()).isEqualTo(9.0);
    }

    @Test
    @DisplayName("11. Verify single active route per delivery lookup")
    void testActiveRouteUniqueness() {
        Location loc1 = locationRepository.save(Location.builder().name("Act Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Act Loc 2").latitude(11.0).longitude(21.0).build());
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-ACT-001").senderName("S").receiverName("R")
                .sourceLocation(loc1).destinationLocation(loc2).weight(1.0).build());
        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg).sourceLocation(loc1).destinationLocation(loc2).build());

        routeRepository.save(Route.builder()
                .delivery(delivery).sourceLocation(loc1).destinationLocation(loc2).totalDistance(10.0).versionNumber(1).isCurrent(false).build());
        routeRepository.save(Route.builder()
                .delivery(delivery).sourceLocation(loc1).destinationLocation(loc2).totalDistance(9.0).versionNumber(2).isCurrent(true).build());

        Optional<Route> currentRoute = routeRepository.findByDeliveryIdAndIsCurrentTrue(delivery.getId());
        assertThat(currentRoute).isPresent();
        assertThat(currentRoute.get().getVersionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("12. Verify LocationEdge source/destination pair uniqueness constraint")
    void testLocationEdgePairUniqueness() {
        Location loc1 = locationRepository.save(Location.builder().name("Pair Loc 1").latitude(10.0).longitude(20.0).build());
        Location loc2 = locationRepository.save(Location.builder().name("Pair Loc 2").latitude(11.0).longitude(21.0).build());

        locationEdgeRepository.save(LocationEdge.builder()
                .sourceLocation(loc1).destinationLocation(loc2).distance(5.0).build());

        LocationEdge duplicateEdge = LocationEdge.builder()
                .sourceLocation(loc1).destinationLocation(loc2).distance(7.0).build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            locationEdgeRepository.saveAndFlush(duplicateEdge);
        });
    }

    @Test
    @DisplayName("13. Verify Optimistic locking via @Version on concurrent entity update")
    void testOptimisticLocking() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .registrationNumber("REG-OPT-01")
                .vehicleType(VehicleType.TRUCK)
                .capacity(1000.0)
                .build());
        assertThat(vehicle.getVersion()).isEqualTo(0L);

        // Fetch first instance and detach it
        Vehicle v1 = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        entityManager.detach(v1);

        // Fetch second instance which still has version = 0
        Vehicle v2 = vehicleRepository.findById(vehicle.getId()).orElseThrow();

        // Update first instance (version advances to 1)
        v1.setCapacity(1200.0);
        vehicleRepository.saveAndFlush(v1);

        // Update second instance (fails because its version is 0, but DB has 1)
        v2.setCapacity(1500.0);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            vehicleRepository.saveAndFlush(v2);
        });
    }

    @Test
    @DisplayName("14. Verify all tables contain zero business data after clean up")
    void testNoBusinessDataRemains() {
        for (String table : REQUIRED_TABLES) {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            assertThat(count)
                    .withFailMessage("Table %s should contain 0 records", table)
                    .isEqualTo(0L);
        }
    }
}
