package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.security.JwtTokenProvider;
import com.routeoptimizer.security.RateLimiterService;
import com.routeoptimizer.service.RouteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class RoadGraphConnectivityAndMaintenanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

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
    private RouteService routeService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private String adminToken;

    private Location hub;
    private Location locConnectedA;
    private Location locConnectedB;
    private Location locDisconnected;
    private Location locSink;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
        cleanDb();

        // 1. Admin
        admin = userRepository.save(User.builder()
                .username("graph_admin")
                .email("admin@graph.com")
                .password(passwordEncoder.encode("AdminPass123!"))
                .fullName("Graph Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build());
        adminToken = jwtTokenProvider.generateTokenForUsername(admin.getUsername());

        // 2. Hub in Central Bengaluru (MG Road)
        hub = saveLocation("MG Road Central Hub", "MG Road, Bengaluru", 12.9716, 77.5946, "HUB");

        // 3. Connected Destination A (Residency Road)
        locConnectedA = saveLocation("Residency Road Dropoff", "Residency Road, Bengaluru", 12.9750, 77.6050, "CUSTOMER");

        // 4. Connected Destination B (Indiranagar)
        locConnectedB = saveLocation("Indiranagar Dropoff", "Indiranagar 100ft Road, Bengaluru", 12.9780, 77.6400, "CUSTOMER");

        // 5. Disconnected Destination (HSR Layout without edges)
        locDisconnected = saveLocation("HSR Layout Sector 1", "HSR Layout, Bengaluru", 12.9231694, 77.6516854, "CUSTOMER");

        // 6. Sink Destination (Marathahalli with one-way incoming edge only)
        locSink = saveLocation("Marathahalli Sink Node", "Marathahalli, Bengaluru", 12.9552572, 77.6984163, "CUSTOMER");

        // Bi-directional edges between Hub <-> ConnectedA <-> ConnectedB <-> Hub
        createEdge(hub, locConnectedA, 3.5, 8.0);
        createEdge(locConnectedA, hub, 3.5, 8.0);

        createEdge(locConnectedA, locConnectedB, 4.0, 10.0);
        createEdge(locConnectedB, locConnectedA, 4.0, 10.0);

        createEdge(locConnectedB, hub, 6.0, 14.0);
        createEdge(hub, locConnectedB, 6.0, 14.0);

        // One-way incoming edge to Sink Node: Hub -> locSink (NO reverse edge!)
        createEdge(hub, locSink, 8.0, 18.0);
    }

    private Location saveLocation(String name, String address, double lat, double lon, String type) {
        Location loc = Location.builder()
                .name(name)
                .address(address)
                .latitude(lat)
                .longitude(lon)
                .locationType(type)
                .city("Bengaluru")
                .build();
        loc.setActive(true);
        loc.setVersion(0L);
        return locationRepository.save(loc);
    }

    private void createEdge(Location src, Location dst, double dist, double time) {
        LocationEdge edge = LocationEdge.builder()
                .sourceLocation(src)
                .destinationLocation(dst)
                .distance(dist)
                .estimatedTime(time)
                .build();
        edge.setActive(true);
        edge.setVersion(0L);
        locationEdgeRepository.save(edge);
    }

    private Delivery createDelivery(String trk, Location dst, double weight) {
        Package pkg = Package.builder()
                .trackingNumber(trk)
                .senderName("Hub Depot")
                .receiverName("Recipient " + trk)
                .sourceLocation(hub)
                .destinationLocation(dst)
                .weight(weight)
                .priority(Priority.MEDIUM)
                .status(PackageStatus.PENDING)
                .build();
        pkg.setActive(true);
        pkg.setVersion(0L);
        Package savedPkg = packageRepository.save(pkg);

        Delivery delivery = Delivery.builder()
                .pkg(savedPkg)
                .sourceLocation(hub)
                .destinationLocation(dst)
                .status(DeliveryStatus.CREATED)
                .build();
        delivery.setActive(true);
        delivery.setVersion(0L);
        return deliveryRepository.save(delivery);
    }

    @Test
    @DisplayName("1. Connected route: multi-stop optimization succeeds across active directed edges")
    void testConnectedRouteOptimizationSucceeds() {
        Delivery d1 = createDelivery("TRK-CONN-1", locConnectedA, 2.0);
        Delivery d2 = createDelivery("TRK-CONN-2", locConnectedB, 3.0);

        MultiStopOptimizationRequest request = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .departureTime(OffsetDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneOffset.UTC))
                .build();

        MultiStopOptimizationResponse response = routeService.optimizeAndPersistMultiDeliveryRoute(request, admin.getUsername());

        assertThat(response).isNotNull();
        assertThat(response.getRouteId()).isNotNull();
        assertThat(response.getTotalDistance()).isEqualTo(13.5); // 3.5 + 4.0 + 6.0
        assertThat(response.getCustomerStops()).hasSize(2);
        assertThat(response.getReturnToHub()).isTrue();
    }

    @Test
    @DisplayName("2. Disconnected route: single-delivery and multi-stop route optimization cleanly throw ResourceNotFoundException")
    void testDisconnectedComponentThrowsException() {
        // Delivery targeting locDisconnected (no road edges exist to this location)
        Delivery dDisc = createDelivery("TRK-DISC-1", locDisconnected, 2.5);

        // A. Standalone/single optimization: Hub -> locDisconnected
        OptimizationRequest singleReq = OptimizationRequest.builder()
                .startLocationId(hub.getId())
                .destinationLocationId(locDisconnected.getId())
                .deliveryId(dDisc.getId())
                .criterion("SHORTEST_DISTANCE")
                .build();

        ResourceNotFoundException singleEx = assertThrows(ResourceNotFoundException.class, () ->
                routeService.optimizeAndPersistRoute(singleReq, admin.getUsername())
        );
        assertThat(singleEx.getMessage()).contains("No path exists between location");

        // B. Multi-stop optimization including unreachable location: cleanly throws ResourceNotFoundException
        Delivery dConn = createDelivery("TRK-CONN-1", locConnectedA, 1.0);
        MultiStopOptimizationRequest multiReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(dConn.getId(), dDisc.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();

        ResourceNotFoundException multiEx = assertThrows(ResourceNotFoundException.class, () ->
                routeService.optimizeAndPersistMultiDeliveryRoute(multiReq, admin.getUsername())
        );
        assertThat(multiEx.getMessage()).contains("No continuous road tour exists");
    }

    @Test
    @DisplayName("3. One-way sink node: open tour succeeds, but closed tour to sink cleanly fails")
    void testOneWaySinkDestinationRouting() {
        Delivery dSink = createDelivery("TRK-SINK-1", locSink, 4.0);

        // A. Open Tour (returnToHub = false): Driver delivers to sink node and completes tour
        MultiStopOptimizationRequest openReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(dSink.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();

        MultiStopOptimizationResponse openRes = routeService.optimizeAndPersistMultiDeliveryRoute(openReq, admin.getUsername());
        assertThat(openRes).isNotNull();
        assertThat(openRes.getTotalDistance()).isEqualTo(8.0);
        assertThat(openRes.getCustomerStops()).hasSize(1);
        assertThat(openRes.getReturnToHub()).isFalse();

        // B. Closed Tour (returnToHub = true): Driver cannot return from sink to Hub -> must throw ResourceNotFoundException
        MultiStopOptimizationRequest closedReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(dSink.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                routeService.optimizeAndPersistMultiDeliveryRoute(closedReq, admin.getUsername())
        );
    }

    @Test
    @DisplayName("4. OSRM Road-Edge Maintenance Workflow: syncs legitimate road edges with duplicate protection and real geometry")
    void testRoadEdgeSyncMaintenanceWorkflow() throws Exception {
        // Sync edge between Central Hub (MG Road) and HSR Layout
        mockMvc.perform(post("/api/edges/sync")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("sourceId", hub.getId().toString())
                        .param("destinationId", locDisconnected.getId().toString())
                        .param("bidirectional", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sourceLocationId").value(hub.getId()))
                .andExpect(jsonPath("$[0].destinationLocationId").value(locDisconnected.getId()))
                .andExpect(jsonPath("$[0].distance", greaterThan(5.0)))
                .andExpect(jsonPath("$[0].geometryGeojson").isNotEmpty())
                .andExpect(jsonPath("$[1].sourceLocationId").value(locDisconnected.getId()))
                .andExpect(jsonPath("$[1].destinationLocationId").value(hub.getId()))
                .andExpect(jsonPath("$[1].distance", greaterThan(5.0)));

        // Verify duplicate protection: running sync again updates existing edges without creating duplicates
        long countBefore = locationEdgeRepository.count();
        mockMvc.perform(post("/api/edges/sync")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("sourceId", hub.getId().toString())
                        .param("destinationId", locDisconnected.getId().toString())
                        .param("bidirectional", "true"))
                .andExpect(status().isOk());
        long countAfter = locationEdgeRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);

        // Now that the road edge was legitimately imported, routing from Hub to HSR Layout succeeds!
        Delivery dHsr = createDelivery("TRK-HSR-1", locDisconnected, 2.0);
        OptimizationRequest req = OptimizationRequest.builder()
                .startLocationId(hub.getId())
                .destinationLocationId(locDisconnected.getId())
                .deliveryId(dHsr.getId())
                .criterion("SHORTEST_DISTANCE")
                .build();

        OptimizationResponse res = routeService.optimizeAndPersistRoute(req, admin.getUsername());
        assertThat(res).isNotNull();
        assertThat(res.getTotalDistance()).isGreaterThan(5.0);
        assertThat(res.isPathExists()).isTrue();
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void cleanDb() {
        jdbcTemplate.execute("DELETE FROM route_edges");
        jdbcTemplate.execute("DELETE FROM route_stops");
        jdbcTemplate.execute("DELETE FROM delivery_status_history");
        jdbcTemplate.execute("DELETE FROM deliveries");
        jdbcTemplate.execute("DELETE FROM routes");
        jdbcTemplate.execute("DELETE FROM packages");
        jdbcTemplate.execute("DELETE FROM location_edges");
        jdbcTemplate.execute("DELETE FROM locations");
        jdbcTemplate.execute("DELETE FROM vehicles");
        jdbcTemplate.execute("DELETE FROM drivers");
        jdbcTemplate.execute("DELETE FROM users");
    }
}
