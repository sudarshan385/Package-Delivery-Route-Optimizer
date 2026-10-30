package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.entity.Vehicle;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.security.JwtTokenProvider;
import com.routeoptimizer.security.RateLimiterService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class FinalBackendAcceptanceWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User driverUser;
    private User customerUser;
    private Driver driver;
    private Vehicle vehicle;

    private String adminToken;
    private String driverToken;
    private String customerToken;

    private Long hubId;
    private Long customerLocAId;
    private Long customerLocBId;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }

        // Clean tables in reverse dependency order
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

        // 1. Seed Admin
        admin = userRepository.save(User.builder()
                .username("accept_admin")
                .email("admin@acceptance.com")
                .password(passwordEncoder.encode("AdminPass123!"))
                .fullName("Acceptance Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build());

        // 2. Seed Driver
        driverUser = userRepository.save(User.builder()
                .username("accept_driver")
                .email("driver@acceptance.com")
                .password(passwordEncoder.encode("DriverPass123!"))
                .fullName("Acceptance Driver")
                .role(Role.DRIVER)
                .isVerified(true)
                .isBlocked(false)
                .build());

        driver = driverRepository.save(Driver.builder()
                .user(driverUser)
                .name("Acceptance Driver")
                .licenseNumber("DL-ACCEPT-999")
                .phone("+919123456789")
                .status(DriverStatus.AVAILABLE)
                .build());

        // 3. Seed Customer User
        customerUser = userRepository.save(User.builder()
                .username("accept_customer")
                .email("customer@acceptance.com")
                .password(passwordEncoder.encode("CustomerPass123!"))
                .fullName("Acceptance Customer")
                .role(Role.USER)
                .isVerified(true)
                .isBlocked(false)
                .build());

        // Tokens
        adminToken = jwtTokenProvider.generateTokenForUsername(admin.getUsername());
        driverToken = jwtTokenProvider.generateTokenForUsername(driverUser.getUsername());
        customerToken = jwtTokenProvider.generateTokenForUsername(customerUser.getUsername());

        // 4. Seed Road Network: Hub, CustomerLocA, CustomerLocB
        LocationRequest hubReq = LocationRequest.builder()
                .name("Central Distribution Depot")
                .address("100 Industrial Area, Bengaluru")
                .latitude(12.9716)
                .longitude(77.5946)
                .locationType("HUB")
                .city("Bengaluru")
                .build();
        hubId = saveLocationViaAdmin(hubReq);

        LocationRequest locAReq = LocationRequest.builder()
                .name("Delivery Dropoff Alpha")
                .address("25 Residency Road, Bengaluru")
                .latitude(12.9750)
                .longitude(77.6050)
                .locationType("CUSTOMER")
                .city("Bengaluru")
                .build();
        customerLocAId = saveLocationViaAdmin(locAReq);

        LocationRequest locBReq = LocationRequest.builder()
                .name("Delivery Dropoff Beta")
                .address("42 Indiranagar 100ft Rd, Bengaluru")
                .latitude(12.9780)
                .longitude(77.6400)
                .locationType("CUSTOMER")
                .city("Bengaluru")
                .build();
        customerLocBId = saveLocationViaAdmin(locBReq);

        // Bi-directional road edges: Hub <-> LocA <-> LocB <-> Hub
        createEdgeViaAdmin(hubId, customerLocAId, 3.5, 8.0);
        createEdgeViaAdmin(customerLocAId, hubId, 3.5, 8.0);

        createEdgeViaAdmin(customerLocAId, customerLocBId, 4.0, 10.0);
        createEdgeViaAdmin(customerLocBId, customerLocAId, 4.0, 10.0);

        createEdgeViaAdmin(customerLocBId, hubId, 6.0, 14.0);
        createEdgeViaAdmin(hubId, customerLocBId, 6.0, 14.0);

        // 5. Seed Vehicle (Capacity: 15.0 kg)
        vehicle = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA04-EXP-5555")
                .vehicleType(VehicleType.VAN)
                .capacity(15.0)
                .status(VehicleStatus.AVAILABLE)
                .build());
    }

    private Long saveLocationViaAdmin(LocationRequest req) {
        try {
            String json = mockMvc.perform(post("/api/locations")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readValue(json, LocationResponse.class).getId();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createEdgeViaAdmin(Long srcId, Long dstId, double dist, double time) {
        try {
            LocationEdgeRequest req = LocationEdgeRequest.builder()
                    .sourceLocationId(srcId)
                    .destinationLocationId(dstId)
                    .distance(dist)
                    .estimatedTime(time)
                    .build();
            mockMvc.perform(post("/api/edges")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Complete end-to-end REST/API acceptance workflow across USER, ADMIN, and DRIVER roles")
    void testCompleteBackendAcceptanceWorkflow() throws Exception {
        // =====================================================================
        // STEP 1: USER creates package and retrieves it with fresh API read
        // =====================================================================
        PackageRequest pkg1Req = PackageRequest.builder()
                .trackingNumber("PKG-ACC-001")
                .senderName("Alpha Retailers")
                .receiverName("Customer One")
                .sourceLocationId(hubId)
                .destinationLocationId(customerLocAId)
                .weight(4.0)
                .priority(Priority.HIGH)
                .description("Fragile Electronics")
                .build();

        String pkg1Json = mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pkg1Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.trackingNumber").value("PKG-ACC-001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        Long package1Id = objectMapper.readValue(pkg1Json, PackageResponse.class).getId();

        // Fresh API read by USER
        mockMvc.perform(get("/api/packages/" + package1Id)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(package1Id))
                .andExpect(jsonPath("$.trackingNumber").value("PKG-ACC-001"))
                .andExpect(jsonPath("$.senderName").value("Alpha Retailers"))
                .andExpect(jsonPath("$.weight").value(4.0))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // USER creates a second package for LocB
        PackageRequest pkg2Req = PackageRequest.builder()
                .trackingNumber("PKG-ACC-002")
                .senderName("Beta Wholesalers")
                .receiverName("Customer Two")
                .sourceLocationId(hubId)
                .destinationLocationId(customerLocBId)
                .weight(6.0)
                .priority(Priority.MEDIUM)
                .description("Commercial Supplies")
                .build();

        String pkg2Json = mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pkg2Req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long package2Id = objectMapper.readValue(pkg2Json, PackageResponse.class).getId();

        // =====================================================================
        // STEP 2: ADMIN creates deliveries and performs multi-stop tour optimization
        // =====================================================================
        CreateDeliveryRequest del1Req = CreateDeliveryRequest.builder()
                .packageId(package1Id)
                .notes("Morning Express Dropoff")
                .build();
        String del1Json = mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(del1Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        Long delivery1Id = objectMapper.readValue(del1Json, DeliveryResponse.class).getId();

        CreateDeliveryRequest del2Req = CreateDeliveryRequest.builder()
                .packageId(package2Id)
                .notes("Standard Afternoon Dropoff")
                .build();
        String del2Json = mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(del2Req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        Long delivery2Id = objectMapper.readValue(del2Json, DeliveryResponse.class).getId();

        // ADMIN selects eligible deliveries and calculates multi-stop route
        MultiStopOptimizationRequest optReq = MultiStopOptimizationRequest.builder()
                .hubLocationId(hubId)
                .deliveryIds(List.of(delivery1Id, delivery2Id))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .build();

        String optJson = mockMvc.perform(post("/api/optimization/multi-stop")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(optReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").isNotEmpty())
                .andExpect(jsonPath("$.algorithm").value("MULTI_STOP_2OPT"))
                .andExpect(jsonPath("$.totalDistance").value(13.5)) // Hub->LocA (3.5) + LocA->LocB (4.0) + LocB->Hub (6.0)
                .andExpect(jsonPath("$.customerStops", hasSize(2)))
                .andExpect(jsonPath("$.customerStops[0].customerStopNumber").value(1))
                .andExpect(jsonPath("$.customerStops[1].customerStopNumber").value(2))
                .andReturn().getResponse().getContentAsString();

        MultiStopOptimizationResponse optRes = objectMapper.readValue(optJson, MultiStopOptimizationResponse.class);
        Long routeId = optRes.getRouteId();

        // =====================================================================
        // STEP 3: Vehicle capacity validation and batch resource assignment
        // =====================================================================
        // Test capacity guard: Van capacity is 15.0 kg. Attempt batch assign to low-capacity bike (5.0 kg < 10.0 kg payload)
        Vehicle bike = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA04-BIKE-101")
                .vehicleType(VehicleType.BIKE)
                .capacity(5.0)
                .status(VehicleStatus.AVAILABLE)
                .build());

        BatchAssignmentRequest invalidCapacityReq = BatchAssignmentRequest.builder()
                .routeId(routeId)
                .driverId(driver.getId())
                .vehicleId(bike.getId())
                .deliveryIds(List.of(delivery1Id, delivery2Id))
                .build();

        mockMvc.perform(post("/api/deliveries/batch-assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidCapacityReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("exceeds vehicle payload capacity")));

        // Execute legitimate batch assignment with valid deliveries (4.0 kg + 6.0 kg = 10.0 kg <= 15.0 kg)
        BatchAssignmentRequest validAssignReq = BatchAssignmentRequest.builder()
                .routeId(routeId)
                .driverId(driver.getId())
                .vehicleId(vehicle.getId())
                .deliveryIds(List.of(delivery1Id, delivery2Id))
                .notes("Dispatched for priority delivery")
                .build();

        mockMvc.perform(post("/api/deliveries/batch-assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validAssignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.totalWeightKg").value(10.0))
                .andExpect(jsonPath("$.assignedDeliveryIds", hasSize(2)));

        // Verify resource statuses
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // =====================================================================
        // STEP 4: DRIVER accesses manifest and executes delivery lifecycle
        // =====================================================================
        mockMvc.perform(get("/api/deliveries/manifest/driver")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId").value(routeId))
                .andExpect(jsonPath("$.totalDistance").value(13.5))
                .andExpect(jsonPath("$.stops", hasSize(4))) // HUB origin, Stop 1, Stop 2, HUB return
                .andExpect(jsonPath("$.stops[1].customerStopNumber").value(1))
                .andExpect(jsonPath("$.stops[1].deliveryId").value(delivery1Id))
                .andExpect(jsonPath("$.stops[2].customerStopNumber").value(2))
                .andExpect(jsonPath("$.stops[2].deliveryId").value(delivery2Id))
                .andExpect(jsonPath("$.isCompleted").value(false));

        // Driver starts Delivery 1: ASSIGNED -> IN_PROGRESS -> COMPLETED
        DeliveryStatusUpdateRequest startD1 = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.IN_PROGRESS)
                .reason("Driver departed from Hub to Alpha Dropoff")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + delivery1Id + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startD1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        DeliveryStatusUpdateRequest completeD1 = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.COMPLETED)
                .reason("Package delivered to Customer One")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + delivery1Id + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeD1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // =====================================================================
        // STEP 5: Verify DELAYED resource release (Driver still on tour!)
        // =====================================================================
        // Delivery 1 is COMPLETED, but Delivery 2 is still ASSIGNED!
        // Driver must remain ON_DELIVERY and Vehicle must remain IN_USE!
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // Driver starts and completes Delivery 2
        DeliveryStatusUpdateRequest startD2 = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.IN_PROGRESS)
                .reason("Driver departed to Beta Dropoff")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + delivery2Id + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startD2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        DeliveryStatusUpdateRequest completeD2 = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.COMPLETED)
                .reason("Package delivered to Customer Two")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + delivery2Id + "/status")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeD2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // =====================================================================
        // STEP 6: Final resource release check across all route deliveries
        // =====================================================================
        // ALL deliveries on the route have reached terminal state (COMPLETED)
        // Driver and Vehicle are now released back to AVAILABLE!
        assertThat(driverRepository.findById(driver.getId()).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        // Packages are marked DELIVERED
        assertThat(packageRepository.findById(package1Id).orElseThrow().getStatus()).isEqualTo(PackageStatus.DELIVERED);
        assertThat(packageRepository.findById(package2Id).orElseThrow().getStatus()).isEqualTo(PackageStatus.DELIVERED);
    }
}
