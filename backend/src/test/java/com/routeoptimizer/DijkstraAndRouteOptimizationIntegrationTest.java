package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.optimizer.DijkstraRouteOptimizationStrategy;
import com.routeoptimizer.optimizer.OptimizationResult;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.service.DeliveryService;
import com.routeoptimizer.service.RouteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
class DijkstraAndRouteOptimizationIntegrationTest {

    @Autowired
    private DijkstraRouteOptimizationStrategy dijkstraStrategy;

    @Autowired
    private RouteService routeService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationEdgeRepository locationEdgeRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

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
    private User driverUser;
    private Driver driverProfile;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    private void setupUsers() {
        adminUser = userRepository.save(User.builder()
                .username("route_admin")
                .email("route_admin@test.com")
                .password("$2a$10$hashedpassword")
                .role(Role.ADMIN)
                .build());

        driverUser = userRepository.save(User.builder()
                .username("route_driver")
                .email("route_driver@test.com")
                .password("$2a$10$hashedpassword")
                .role(Role.DRIVER)
                .build());

        driverProfile = driverRepository.save(Driver.builder()
                .user(driverUser)
                .name("Route Driver")
                .licenseNumber("LIC-RTE-01")
                .status(DriverStatus.AVAILABLE)
                .build());
    }

    private Location createLocation(String name, double lat, double lon, boolean active) {
        Location loc = Location.builder()
                .name(name)
                .latitude(lat)
                .longitude(lon)
                .build();
        loc.setActive(active);
        return locationRepository.save(loc);
    }

    private LocationEdge createEdge(Location src, Location dst, double dist, double time, boolean active) {
        LocationEdge edge = LocationEdge.builder()
                .sourceLocation(src)
                .destinationLocation(dst)
                .distance(dist)
                .estimatedTime(time)
                .build();
        edge.setActive(active);
        return locationEdgeRepository.save(edge);
    }

    @Test
    @DisplayName("44-50. Dijkstra finds shortest path and chooses lower-distance path over longer alternative")
    void testDijkstraFindsShortestPath() {
        // Graph layout:
        // A -> B (distance: 10, time: 20)
        // B -> D (distance: 10, time: 20)  [Total path A-B-D: dist 20]
        // A -> C (distance: 5,  time: 10)
        // C -> D (distance: 6,  time: 12)  [Total path A-C-D: dist 11] -> Optimal
        Location a = createLocation("Node A", 1.0, 1.0, true);
        Location b = createLocation("Node B", 2.0, 2.0, true);
        Location c = createLocation("Node C", 3.0, 3.0, true);
        Location d = createLocation("Node D", 4.0, 4.0, true);

        createEdge(a, b, 10.0, 20.0, true);
        createEdge(b, d, 10.0, 20.0, true);
        createEdge(a, c, 5.0, 10.0, true);
        createEdge(c, d, 6.0, 12.0, true);

        OptimizationResult result = dijkstraStrategy.optimize(
                OptimizationRequest.builder().startLocationId(a.getId()).destinationLocationId(d.getId()).build()
        );

        assertThat(result.isPathExists()).isTrue();
        assertThat(result.getTotalDistance()).isEqualTo(11.0);
        assertThat(result.getTotalEstimatedTime()).isEqualTo(22.0);
        assertThat(result.getOrderedLocations()).extracting(Location::getName)
                .containsExactly("Node A", "Node C", "Node D");
        assertThat(result.getOrderedEdges()).hasSize(2);
    }

    @Test
    @DisplayName("Dijkstra finds fastest path and chooses minimum time over shorter distance when criterion is FASTEST_ROUTE")
    void testDijkstraFindsFastestPath() {
        // Graph layout:
        // A -> B (distance: 5, time: 20)
        // B -> D (distance: 5, time: 20)  [Total path A-B-D: dist 10, time 40] -> Shortest Distance
        // A -> C (distance: 10, time: 10)
        // C -> D (distance: 10, time: 10) [Total path A-C-D: dist 20, time 20] -> Fastest Route
        Location a = createLocation("Node A", 1.0, 1.0, true);
        Location b = createLocation("Node B", 2.0, 2.0, true);
        Location c = createLocation("Node C", 3.0, 3.0, true);
        Location d = createLocation("Node D", 4.0, 4.0, true);

        createEdge(a, b, 5.0, 20.0, true);
        createEdge(b, d, 5.0, 20.0, true);
        createEdge(a, c, 10.0, 10.0, true);
        createEdge(c, d, 10.0, 10.0, true);

        OptimizationResult shortestResult = dijkstraStrategy.optimize(
                OptimizationRequest.builder().startLocationId(a.getId()).destinationLocationId(d.getId()).criterion("SHORTEST_DISTANCE").build()
        );
        
        assertThat(shortestResult.isPathExists()).isTrue();
        assertThat(shortestResult.getTotalDistance()).isEqualTo(10.0);
        assertThat(shortestResult.getTotalEstimatedTime()).isEqualTo(40.0);
        assertThat(shortestResult.getCriterion()).isEqualTo("SHORTEST_DISTANCE");
        assertThat(shortestResult.getOrderedLocations()).extracting(Location::getName)
                .containsExactly("Node A", "Node B", "Node D");

        OptimizationResult fastestResult = dijkstraStrategy.optimize(
                OptimizationRequest.builder().startLocationId(a.getId()).destinationLocationId(d.getId()).criterion("FASTEST_ROUTE").build()
        );

        assertThat(fastestResult.isPathExists()).isTrue();
        assertThat(fastestResult.getTotalDistance()).isEqualTo(20.0);
        assertThat(fastestResult.getTotalEstimatedTime()).isEqualTo(20.0);
        assertThat(fastestResult.getCriterion()).isEqualTo("FASTEST_ROUTE");
        assertThat(fastestResult.getOrderedLocations()).extracting(Location::getName)
                .containsExactly("Node A", "Node C", "Node D");
    }

    @Test
    @DisplayName("51-52. Dijkstra ignores inactive edges and respects directed edges")
    void testDijkstraIgnoresInactiveEdgesAndRespectsDirection() {
        Location a = createLocation("Directed A", 1.0, 1.0, true);
        Location b = createLocation("Directed B", 2.0, 2.0, true);

        // Edge A -> B is active
        createEdge(a, b, 5.0, 10.0, true);

        // Path A -> B exists
        OptimizationResult forward = dijkstraStrategy.optimize(
                OptimizationRequest.builder().startLocationId(a.getId()).destinationLocationId(b.getId()).build()
        );
        assertThat(forward.getTotalDistance()).isEqualTo(5.0);

        // Reverse path B -> A does NOT exist (directed graph)
        assertThrows(ResourceNotFoundException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(b.getId()).destinationLocationId(a.getId()).build());
        });

        // Now test inactive edge: Create C -> D but set active = false
        Location c = createLocation("Inactive C", 3.0, 3.0, true);
        Location d = createLocation("Inactive D", 4.0, 4.0, true);
        createEdge(c, d, 8.0, 15.0, false);

        assertThrows(ResourceNotFoundException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(c.getId()).destinationLocationId(d.getId()).build());
        });
    }

    @Test
    @DisplayName("53-55. Dijkstra rejects invalid input, inactive locations, and identical start/destination")
    void testDijkstraValidationErrors() {
        Location activeLoc = createLocation("Active Loc", 1.0, 1.0, true);
        Location inactLoc = createLocation("Inactive Loc", 2.0, 2.0, false);

        // Inactive start location
        assertThrows(BadRequestException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(inactLoc.getId()).destinationLocationId(activeLoc.getId()).build());
        });

        // Inactive destination location
        assertThrows(BadRequestException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(activeLoc.getId()).destinationLocationId(inactLoc.getId()).build());
        });

        // Same start and destination
        assertThrows(BadRequestException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(activeLoc.getId()).destinationLocationId(activeLoc.getId()).build());
        });

        // Nonexistent location
        assertThrows(ResourceNotFoundException.class, () -> {
            dijkstraStrategy.optimize(OptimizationRequest.builder().startLocationId(99999L).destinationLocationId(activeLoc.getId()).build());
        });
    }

    @Test
    @DisplayName("56-62. Route persistence: saves Route, ordered RouteStop entries, and snapshot RouteEdge entries")
    void testRoutePersistenceForDelivery() {
        setupUsers();
        Location a = createLocation("Hub Start", 1.0, 1.0, true);
        Location b = createLocation("Hub Mid", 2.0, 2.0, true);
        Location c = createLocation("Hub End", 3.0, 3.0, true);

        createEdge(a, b, 7.5, 15.0, true);
        createEdge(b, c, 4.5, 10.0, true);

        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-RTE-PERSIST")
                .senderName("S").receiverName("R")
                .sourceLocation(a).destinationLocation(c)
                .weight(3.0)
                .build());

        DeliveryResponse delivery = deliveryService.createDelivery(
                CreateDeliveryRequest.builder().packageId(pkg.getId()).build(),
                adminUser.getUsername()
        );

        OptimizationResponse opt = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().deliveryId(delivery.getId()).build(),
                adminUser.getUsername()
        );

        assertThat(opt.getRouteId()).isNotNull();
        assertThat(opt.getVersionNumber()).isEqualTo(1);
        assertThat(opt.getTotalDistance()).isEqualTo(12.0);
        assertThat(opt.getTotalEstimatedTime()).isEqualTo(25.0);

        // Verify Route details
        RouteResponse route = routeService.getRouteById(opt.getRouteId(), adminUser.getUsername());
        assertThat(route.getAlgorithmUsed()).isEqualTo("DIJKSTRA");
        assertThat(route.getIsCurrent()).isTrue();
        assertThat(route.getVersionNumber()).isEqualTo(1);

        // Verify ordered stops
        assertThat(route.getStops()).hasSize(3);
        assertThat(route.getStops().get(0).getStopOrder()).isEqualTo(1);
        assertThat(route.getStops().get(0).getStopType()).isEqualTo(StopType.SOURCE);
        assertThat(route.getStops().get(1).getStopOrder()).isEqualTo(2);
        assertThat(route.getStops().get(1).getStopType()).isEqualTo(StopType.WAYPOINT);
        assertThat(route.getStops().get(2).getStopOrder()).isEqualTo(3);
        assertThat(route.getStops().get(2).getStopType()).isEqualTo(StopType.DESTINATION);

        // Verify ordered edges and snapshots
        assertThat(route.getEdges()).hasSize(2);
        assertThat(route.getEdges().get(0).getEdgeOrder()).isEqualTo(1);
        assertThat(route.getEdges().get(0).getDistance()).isEqualTo(7.5);
        assertThat(route.getEdges().get(1).getEdgeOrder()).isEqualTo(2);
        assertThat(route.getEdges().get(1).getDistance()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("63-68. Route versioning: re-optimization increments version, updates isCurrent, and blocks when IN_PROGRESS")
    void testRouteVersioningAndReoptimizationRules() {
        setupUsers();
        Location a = createLocation("V Start", 1.0, 1.0, true);
        Location b = createLocation("V End", 2.0, 2.0, true);
        createEdge(a, b, 15.0, 30.0, true);

        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-VER-TEST")
                .senderName("S").receiverName("R")
                .sourceLocation(a).destinationLocation(b)
                .weight(1.0)
                .build());

        DeliveryResponse delivery = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());

        // First optimization -> Version 1
        OptimizationResponse v1 = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().deliveryId(delivery.getId()).build(),
                adminUser.getUsername()
        );
        assertThat(v1.getVersionNumber()).isEqualTo(1);

        // Re-optimization before IN_PROGRESS -> Version 2
        OptimizationResponse v2 = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().deliveryId(delivery.getId()).build(),
                adminUser.getUsername()
        );
        assertThat(v2.getVersionNumber()).isEqualTo(2);

        // Check v1 is now isCurrent = false, v2 is isCurrent = true
        RouteResponse r1 = routeService.getRouteById(v1.getRouteId(), adminUser.getUsername());
        RouteResponse r2 = routeService.getRouteById(v2.getRouteId(), adminUser.getUsername());
        assertThat(r1.getIsCurrent()).isFalse();
        assertThat(r2.getIsCurrent()).isTrue();

        // Assign delivery and start delivery (IN_PROGRESS)
        Vehicle veh = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("REG-VER-01")
                .vehicleType(VehicleType.BIKE)
                .capacity(50.0)
                .status(VehicleStatus.AVAILABLE)
                .build());

        deliveryService.assignDelivery(delivery.getId(),
                AssignDeliveryRequest.builder().driverId(driverProfile.getId()).vehicleId(veh.getId()).build(),
                adminUser.getUsername());

        deliveryService.updateDeliveryStatus(delivery.getId(),
                DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(),
                adminUser.getUsername());

        // Attempting re-optimization once IN_PROGRESS must be rejected with ConflictException (409)
        assertThrows(ConflictException.class, () -> {
            routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delivery.getId()).build(), adminUser.getUsername());
        });
    }

    @Test
    @DisplayName("69-72. Authorization: driver access control for routes")
    void testRouteAccessControl() {
        setupUsers();
        Location a = createLocation("Sec A", 1.0, 1.0, true);
        Location b = createLocation("Sec B", 2.0, 2.0, true);
        createEdge(a, b, 10.0, 20.0, true);

        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber("TRK-SEC-01")
                .senderName("S").receiverName("R")
                .sourceLocation(a).destinationLocation(b)
                .weight(1.0)
                .build());

        DeliveryResponse delivery = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(pkg.getId()).build(), adminUser.getUsername());
        OptimizationResponse opt = routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delivery.getId()).build(), adminUser.getUsername());

        // Driver is NOT assigned to this delivery yet -> 403 AccessDenied
        assertThrows(AccessDeniedException.class, () -> {
            routeService.getRouteById(opt.getRouteId(), driverUser.getUsername());
        });

        // Assign delivery to this driver
        Vehicle veh = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("REG-SEC-01").vehicleType(VehicleType.VAN).capacity(500.0).status(VehicleStatus.AVAILABLE).build());
        deliveryService.assignDelivery(delivery.getId(),
                AssignDeliveryRequest.builder().driverId(driverProfile.getId()).vehicleId(veh.getId()).build(),
                adminUser.getUsername());

        // Now driver CAN view the route
        RouteResponse driverView = routeService.getRouteById(opt.getRouteId(), driverUser.getUsername());
        assertThat(driverView.getId()).isEqualTo(opt.getRouteId());
    }

    @Test
    @DisplayName("Targeted A: Standalone optimization request without deliveryId succeeds and skips route persistence")
    void testTargetedA_OptimizationWithoutDeliveryId() {
        setupUsers();
        Location a = createLocation("Standalone A", 1.0, 1.0, true);
        Location b = createLocation("Standalone B", 2.0, 2.0, true);
        createEdge(a, b, 12.0, 24.0, true);

        OptimizationResponse response = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().startLocationId(a.getId()).destinationLocationId(b.getId()).build(),
                adminUser.getUsername()
        );

        assertThat(response.isPathExists()).isTrue();
        assertThat(response.getTotalDistance()).isEqualTo(12.0);
        assertThat(response.getRouteId()).isNull();
        assertThat(response.getVersionNumber()).isNull();

        // Database must NOT contain any persisted Route, RouteStop, or RouteEdge
        assertThat(routeRepository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Targeted B: Optimization with nonexistent deliveryId throws 404 ResourceNotFoundException")
    void testTargetedB_NonexistentDeliveryId() {
        setupUsers();
        assertThrows(ResourceNotFoundException.class, () -> {
            routeService.optimizeAndPersistRoute(
                    OptimizationRequest.builder().deliveryId(999999L).build(),
                    adminUser.getUsername()
            );
        });
    }

    @Test
    @DisplayName("Targeted C-F: Optimization rejected with 409 Conflict for IN_PROGRESS, COMPLETED, FAILED, and CANCELLED deliveries")
    void testTargetedCF_RejectOptimizationForDisallowedDeliveryStatuses() {
        setupUsers();
        Location a = createLocation("Status A", 1.0, 1.0, true);
        Location b = createLocation("Status B", 2.0, 2.0, true);
        createEdge(a, b, 5.0, 10.0, true);

        Vehicle veh = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("REG-STAT-01").vehicleType(VehicleType.VAN).capacity(500.0).status(VehicleStatus.AVAILABLE).build());

        // Test C: IN_PROGRESS
        Package p1 = packageRepository.save(Package.builder().trackingNumber("TRK-STAT-C").senderName("S").receiverName("R").sourceLocation(a).destinationLocation(b).weight(1.0).build());
        DeliveryResponse delC = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p1.getId()).build(), adminUser.getUsername());
        deliveryService.assignDelivery(delC.getId(), AssignDeliveryRequest.builder().driverId(driverProfile.getId()).vehicleId(veh.getId()).build(), adminUser.getUsername());
        deliveryService.updateDeliveryStatus(delC.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), adminUser.getUsername());

        assertThrows(ConflictException.class, () -> {
            routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delC.getId()).build(), adminUser.getUsername());
        });

        // Test D: COMPLETED
        deliveryService.updateDeliveryStatus(delC.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.COMPLETED).build(), adminUser.getUsername());
        assertThrows(ConflictException.class, () -> {
            routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delC.getId()).build(), adminUser.getUsername());
        });

        // Test E: FAILED
        Package p2 = packageRepository.save(Package.builder().trackingNumber("TRK-STAT-E").senderName("S").receiverName("R").sourceLocation(a).destinationLocation(b).weight(1.0).build());
        DeliveryResponse delE = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p2.getId()).build(), adminUser.getUsername());
        deliveryService.assignDelivery(delE.getId(), AssignDeliveryRequest.builder().driverId(driverProfile.getId()).vehicleId(veh.getId()).build(), adminUser.getUsername());
        deliveryService.updateDeliveryStatus(delE.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.IN_PROGRESS).build(), adminUser.getUsername());
        deliveryService.updateDeliveryStatus(delE.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.FAILED).build(), adminUser.getUsername());

        assertThrows(ConflictException.class, () -> {
            routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delE.getId()).build(), adminUser.getUsername());
        });

        // Test F: CANCELLED
        Package p3 = packageRepository.save(Package.builder().trackingNumber("TRK-STAT-F").senderName("S").receiverName("R").sourceLocation(a).destinationLocation(b).weight(1.0).build());
        DeliveryResponse delF = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p3.getId()).build(), adminUser.getUsername());
        deliveryService.updateDeliveryStatus(delF.getId(), DeliveryStatusUpdateRequest.builder().status(DeliveryStatus.CANCELLED).build(), adminUser.getUsername());

        assertThrows(ConflictException.class, () -> {
            routeService.optimizeAndPersistRoute(OptimizationRequest.builder().deliveryId(delF.getId()).build(), adminUser.getUsername());
        });
    }

    @Test
    @DisplayName("Targeted G & H: Optimization allowed for CREATED and ASSIGNED deliveries")
    void testTargetedGH_AllowedOptimizationForCreatedAndAssignedDeliveries() {
        setupUsers();
        Location a = createLocation("Allowed A", 1.0, 1.0, true);
        Location b = createLocation("Allowed B", 2.0, 2.0, true);
        createEdge(a, b, 8.0, 16.0, true);

        Package p = packageRepository.save(Package.builder().trackingNumber("TRK-ALLOW-GH").senderName("S").receiverName("R").sourceLocation(a).destinationLocation(b).weight(1.0).build());
        DeliveryResponse del = deliveryService.createDelivery(CreateDeliveryRequest.builder().packageId(p.getId()).build(), adminUser.getUsername());

        // Target G: CREATED delivery optimization allowed -> creates version 1
        OptimizationResponse optCreated = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().deliveryId(del.getId()).build(),
                adminUser.getUsername()
        );
        assertThat(optCreated.getRouteId()).isNotNull();
        assertThat(optCreated.getVersionNumber()).isEqualTo(1);

        // Assign delivery -> ASSIGNED status
        Vehicle veh = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("REG-ALLOW-01").vehicleType(VehicleType.VAN).capacity(500.0).status(VehicleStatus.AVAILABLE).build());
        deliveryService.assignDelivery(del.getId(), AssignDeliveryRequest.builder().driverId(driverProfile.getId()).vehicleId(veh.getId()).build(), adminUser.getUsername());

        // Target H: ASSIGNED delivery re-optimization allowed -> creates version 2
        OptimizationResponse optAssigned = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder().deliveryId(del.getId()).build(),
                adminUser.getUsername()
        );
        assertThat(optAssigned.getRouteId()).isNotNull();
        assertThat(optAssigned.getVersionNumber()).isEqualTo(2);

        // Old version becomes isCurrent = false, new version isCurrent = true
        RouteResponse v1 = routeService.getRouteById(optCreated.getRouteId(), adminUser.getUsername());
        RouteResponse v2 = routeService.getRouteById(optAssigned.getRouteId(), adminUser.getUsername());
        assertThat(v1.getIsCurrent()).isFalse();
        assertThat(v2.getIsCurrent()).isTrue();
    }

    @Test
    @DisplayName("Part 3 Regression: Stored edge travel time is authoritative in Dijkstra optimization")
    void testAuthoritativeTravelTimePreservation() {
        setupUsers();
        Location a = createLocation("Auth Loc A", 12.9716, 77.5946, true);
        Location b = createLocation("Auth Loc B", 13.0358, 77.5970, true);

        // Edge with explicitly stored travel time of 25.0 minutes for 12.5 km
        LocationEdge edge = createEdge(a, b, 12.5, 25.0, true);
        assertThat(edge.getEstimatedTime()).isEqualTo(25.0);

        OptimizationResult result = dijkstraStrategy.optimize(OptimizationRequest.builder()
                .startLocationId(a.getId())
                .destinationLocationId(b.getId())
                .build());

        assertThat(result.isPathExists()).isTrue();
        assertThat(result.getTotalDistance()).isEqualTo(12.5);
        // Must strictly preserve the authoritative stored travel time (25.0 minutes, NOT transformed to 18.75)
        assertThat(result.getTotalEstimatedTime()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Verify geometry persistence for multi-edge route: Pickup -> Hub -> Destination")
    void testMultiEdgeGeometryPersistenceAndRetrieval() {
        setupUsers();
        Location pickup = createLocation("Pickup Pt", 12.9716, 77.5946, true);
        Location hub = createLocation("Central Hub", 12.9800, 77.6000, true);
        Location dest = createLocation("Customer Dest", 13.0000, 77.6200, true);

        String geom1 = "{\"type\":\"LineString\",\"coordinates\":[[77.5946,12.9716],[77.5970,12.9750],[77.6000,12.9800]]}";
        String geom2 = "{\"type\":\"LineString\",\"coordinates\":[[77.6000,12.9800],[77.6100,12.9900],[77.6200,13.0000]]}";

        LocationEdge e1 = LocationEdge.builder()
                .sourceLocation(pickup)
                .destinationLocation(hub)
                .distance(3.5)
                .estimatedTime(6.0)
                .geometryGeojson(geom1)
                .build();
        e1.setActive(true);
        locationEdgeRepository.save(e1);

        LocationEdge e2 = LocationEdge.builder()
                .sourceLocation(hub)
                .destinationLocation(dest)
                .distance(5.5)
                .estimatedTime(7.5)
                .geometryGeojson(geom2)
                .build();
        e2.setActive(true);
        locationEdgeRepository.save(e2);

        // Create package and delivery
        Package pkg = Package.builder()
                .trackingNumber("TRK-GEOM-001")
                .sourceLocation(pickup)
                .destinationLocation(dest)
                .senderName("Sender")
                .senderPhone("1234567890")
                .receiverName("Receiver")
                .receiverPhone("0987654321")
                .weight(2.0)
                .status(PackageStatus.PENDING)
                .build();
        pkg.setActive(true);
        pkg = packageRepository.save(pkg);

        Delivery delivery = deliveryRepository.save(Delivery.builder()
                .pkg(pkg)
                .sourceLocation(pickup)
                .destinationLocation(dest)
                .status(DeliveryStatus.CREATED)
                .build());

        OptimizationResponse optResponse = routeService.optimizeAndPersistRoute(
                OptimizationRequest.builder()
                        .deliveryId(delivery.getId())
                        .criterion("SHORTEST_DISTANCE")
                        .build(),
                adminUser.getUsername()
        );

        assertThat(optResponse.isPathExists()).isTrue();
        assertThat(optResponse.getTotalDistance()).isEqualTo(9.0);
        assertThat(optResponse.getTotalEstimatedTime()).isEqualTo(13.5);
        assertThat(optResponse.getOrderedEdges()).hasSize(2);
        assertThat(optResponse.getOrderedEdges().get(0).getGeometryGeojson()).isEqualTo(geom1);
        assertThat(optResponse.getOrderedEdges().get(1).getGeometryGeojson()).isEqualTo(geom2);

        RouteResponse savedRoute = routeService.getRouteById(optResponse.getRouteId(), adminUser.getUsername());
        assertThat(savedRoute.getEdges()).hasSize(2);
        assertThat(savedRoute.getEdges().get(0).getGeometryGeojson()).isEqualTo(geom1);
        assertThat(savedRoute.getEdges().get(1).getGeometryGeojson()).isEqualTo(geom2);
    }
}
