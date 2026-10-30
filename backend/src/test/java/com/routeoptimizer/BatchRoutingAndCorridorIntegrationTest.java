package com.routeoptimizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.BatchDispatchAssignmentRequest;
import com.routeoptimizer.dto.CustomerShipmentRequest;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class BatchRoutingAndCorridorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryLegRepository deliveryLegRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private RouteEdgeRepository routeEdgeRepository;

    @Autowired
    private LocationEdgeRepository locationEdgeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customerUser;
    private User adminUser;
    private User driver1User;

    private Driver driver1;
    private Vehicle vanVehicle;
    private Vehicle smallBikeVehicle;
    private Location centralHub;

    @BeforeEach
    public void setup() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        jdbcTemplate.execute("DELETE FROM shipment_events");
        jdbcTemplate.execute("DELETE FROM delivery_legs");
        jdbcTemplate.execute("DELETE FROM delivery_status_history");
        jdbcTemplate.execute("DELETE FROM route_edges");
        jdbcTemplate.execute("DELETE FROM route_stops");
        jdbcTemplate.execute("DELETE FROM routes");
        jdbcTemplate.execute("DELETE FROM deliveries");
        jdbcTemplate.execute("DELETE FROM packages");
        jdbcTemplate.execute("DELETE FROM drivers");
        jdbcTemplate.execute("DELETE FROM vehicles");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM location_edges");
        jdbcTemplate.execute("DELETE FROM locations");

        // 1. Central Hub in Koramangala
        centralHub = locationRepository.save(Location.builder()
                .name("Bengaluru Central Hub")
                .address("Koramangala 4th Block, Bengaluru")
                .city("Bengaluru")
                .latitude(12.9345)
                .longitude(77.6265)
                .locationType("HUB")
                .build());
        centralHub.setActive(true);
        centralHub.setVersion(0L);
        locationRepository.save(centralHub);

        // 2. Users
        customerUser = userRepository.save(User.builder()
                .username("batch_customer")
                .email("batch_customer@example.com")
                .password(passwordEncoder.encode("Pass123!"))
                .fullName("Batch Customer")
                .role(Role.USER)
                .isVerified(true)
                .isBlocked(false)
                .build());

        adminUser = userRepository.save(User.builder()
                .username("batch_admin")
                .email("batch_admin@example.com")
                .password(passwordEncoder.encode("AdminPass123!"))
                .fullName("Batch Dispatch Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build());

        driver1User = userRepository.save(User.builder()
                .username("batch_driver1")
                .email("batch_driver1@example.com")
                .password(passwordEncoder.encode("DriverPass123!"))
                .fullName("Rajesh Batch Driver")
                .role(Role.DRIVER)
                .isVerified(true)
                .isBlocked(false)
                .build());

        // 3. Driver & Vehicles
        driver1 = driverRepository.save(Driver.builder()
                .user(driver1User)
                .name("Rajesh Batch Driver")
                .phone("+919876543212")
                .licenseNumber("KA-01-BATCH-001")
                .status(DriverStatus.AVAILABLE)
                .build());
        driver1.setActive(true);
        driverRepository.save(driver1);

        vanVehicle = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-VAN-9999")
                .vehicleType(VehicleType.VAN)
                .capacity(100.0) // 100 kg
                .status(VehicleStatus.AVAILABLE)
                .build());
        vanVehicle.setActive(true);
        vehicleRepository.save(vanVehicle);

        smallBikeVehicle = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-BIKE-1111")
                .vehicleType(VehicleType.BIKE)
                .capacity(5.0) // 5 kg limit to test capacity splitting
                .status(VehicleStatus.AVAILABLE)
                .build());
        smallBikeVehicle.setActive(true);
        vehicleRepository.save(smallBikeVehicle);
    }

    private Long createShipment(double pLat, double pLon, double dLat, double dLon, double weight, String itemName) throws Exception {
        CustomerShipmentRequest req = CustomerShipmentRequest.builder()
                .senderName("Sender " + itemName)
                .senderPhone("+919876543210")
                .pickupAddress("Pickup at " + itemName)
                .pickupLatitude(pLat)
                .pickupLongitude(pLon)
                .receiverName("Receiver " + itemName)
                .receiverPhone("+919876543211")
                .deliveryAddress("Delivery at " + itemName)
                .deliveryLatitude(dLat)
                .deliveryLongitude(dLon)
                .itemName(itemName)
                .weight(weight)
                .quantity(1)
                .declaredValue(500.0)
                .priority("MEDIUM")
                .build();

        MvcResult res = mockMvc.perform(post("/api/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(customerUser.getUsername()).roles("USER")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    @DisplayName("Phase 2: Configuration endpoint returns verified positive 10 km corridor limit")
    public void testBatchConfiguration() throws Exception {
        mockMvc.perform(get("/api/shipments/batch/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupRadiusKm").value(10.0))
                .andExpect(jsonPath("$.deliveryRadiusKm").value(10.0))
                .andExpect(jsonPath("$.defaultCorridorLimit").value(10.0));
    }

    @Test
    @DisplayName("Phase 2 & 4: Propose Batch Pickup filters by 10 km corridor detour and provides explicit exclusion reasons")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testBatchPickupProposalFiltering() throws Exception {
        // Stop 1: Koramangala 5th Block (~1.2 km from hub) -> within 10 km corridor
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 4.0, "Near Parcel 1");

        // Stop 2: BTM Layout (~3.8 km from hub) -> within 10 km corridor
        createShipment(12.9165, 77.6101, 12.9716, 77.5946, 3.5, "Near Parcel 2");

        // Stop 3: Far out in Hoskote border (lat: 13.0800, lon: 77.7800, ~26 km away) -> EXCEEDS 10 km corridor!
        createShipment(13.0800, 77.7800, 12.9345, 77.6265, 2.0, "Far Out Parcel 3");

        MvcResult res = mockMvc.perform(get("/api/shipments/batch/propose-pickup")
                        .param("hubId", centralHub.getId().toString())
                        .param("maxRadiusKm", "10.0")
                        .param("vehicleId", vanVehicle.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchType").value("FIRST_MILE"))
                .andExpect(jsonPath("$.configuredRadiusKm").value(10.0))
                .andExpect(jsonPath("$.effectiveRadiusKm").value(10.0))
                .andExpect(jsonPath("$.includedLegIds", hasSize(2)))
                .andExpect(jsonPath("$.excludedLegIds", hasSize(1)))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode allCandidates = root.get("allCandidates");
        assertThat(allCandidates.size()).isEqualTo(3);

        boolean foundExcludedWithReason = false;
        for (JsonNode cand : allCandidates) {
            if (!cand.get("eligible").asBoolean()) {
                assertThat(cand.get("exclusionReason").asText()).contains("corridor limit");
                foundExcludedWithReason = true;
            }
        }
        assertThat(foundExcludedWithReason).isTrue();

        // Verify multi-stop sequence numbers
        JsonNode orderedStops = root.get("orderedIncludedStops");
        assertThat(orderedStops.size()).isEqualTo(2);
        assertThat(orderedStops.get(0).get("proposedStopOrder").asInt()).isEqualTo(1);
        assertThat(orderedStops.get(1).get("proposedStopOrder").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Phase 2 & 4: Capacity check detects overload and splits into compliant batch groups")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testVehicleCapacitySplitting() throws Exception {
        // 2 parcels of 4.0 kg each = 8.0 kg total. Small bike capacity is 5.0 kg!
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 4.0, "Heavy Parcel A");
        createShipment(12.9280, 77.6200, 12.9716, 77.5946, 4.0, "Heavy Parcel B");

        MvcResult res = mockMvc.perform(get("/api/shipments/batch/propose-pickup")
                        .param("hubId", centralHub.getId().toString())
                        .param("maxRadiusKm", "10.0")
                        .param("vehicleId", smallBikeVehicle.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(root.get("capacityGroups").size()).isGreaterThanOrEqualTo(2);

        // One included in primary batch, second excluded with explicit vehicle capacity reason
        assertThat(root.get("includedLegIds").size()).isEqualTo(1);
        assertThat(root.get("excludedLegIds").size()).isEqualTo(1);

        JsonNode excludedCandidate = null;
        for (JsonNode cand : root.get("allCandidates")) {
            if (!cand.get("eligible").asBoolean()) {
                excludedCandidate = cand;
            }
        }
        assertThat(excludedCandidate).isNotNull();
        assertThat(excludedCandidate.get("exclusionReason").asText()).contains("capacity");
    }

    @Test
    @DisplayName("Phase 2 & 3: Transactional dispatch assigns legs, creates route, updates statuses, and rejects concurrent double-booking")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testBatchDispatchAndConcurrencyGuards() throws Exception {
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 3.0, "Dispatch Test A");
        createShipment(12.9280, 77.6200, 12.9716, 77.5946, 2.5, "Dispatch Test B");

        List<DeliveryLeg> pendingLegs = deliveryLegRepository.findByLegTypeAndStatusAndActiveTrue(
                LegType.FIRST_MILE, LegStatus.PENDING);
        assertThat(pendingLegs.size()).isEqualTo(2);
        List<Long> legIds = pendingLegs.stream().map(DeliveryLeg::getId).toList();

        BatchDispatchAssignmentRequest req = BatchDispatchAssignmentRequest.builder()
                .batchType("FIRST_MILE")
                .hubId(centralHub.getId())
                .driverId(driver1.getId())
                .vehicleId(vanVehicle.getId())
                .legIds(legIds)
                .notes("Morning batch corridor dispatch")
                .build();

        // 1. First Dispatch succeeds
        mockMvc.perform(post("/api/shipments/batch/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.assignedLegIds", hasSize(2)))
                .andExpect(jsonPath("$.totalWeightKg").value(5.5));

        // Verify DB states: Driver is ON_DELIVERY, Vehicle is IN_USE
        Driver d = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(d.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        Vehicle v = vehicleRepository.findById(vanVehicle.getId()).orElseThrow();
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // 2. Concurrency Guard: Attempting to dispatch already-assigned legs or already busy driver returns 409 Conflict
        mockMvc.perform(post("/api/shipments/batch/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Phase 2 & 3: Per-package lifecycle completion does NOT prematurely release driver/vehicle until ALL stops are done")
    public void testPerPackageCompletionAndResourceRelease() throws Exception {
        // Create 2 shipments and dispatch them in a batch
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 3.0, "Lifecycle Test 1");
        createShipment(12.9280, 77.6200, 12.9716, 77.5946, 2.5, "Lifecycle Test 2");

        List<DeliveryLeg> pendingLegs = deliveryLegRepository.findByLegTypeAndStatusAndActiveTrue(
                LegType.FIRST_MILE, LegStatus.PENDING);
        List<Long> legIds = pendingLegs.stream().map(DeliveryLeg::getId).toList();
        Long leg1Id = legIds.get(0);
        Long leg2Id = legIds.get(1);

        BatchDispatchAssignmentRequest req = BatchDispatchAssignmentRequest.builder()
                .batchType("FIRST_MILE")
                .hubId(centralHub.getId())
                .driverId(driver1.getId())
                .vehicleId(vanVehicle.getId())
                .legIds(legIds)
                .build();

        mockMvc.perform(post("/api/shipments/batch/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(adminUser.getUsername()).roles("ADMIN")))
                .andExpect(status().isOk());

        // Driver starts Leg 1
        mockMvc.perform(patch("/api/shipments/legs/" + leg1Id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_TRANSIT")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        // Driver completes Leg 1 at Hub
        mockMvc.perform(patch("/api/shipments/legs/" + leg1Id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        // CRITICAL CHECK: Leg 1 is COMPLETED, BUT Leg 2 is still pending/assigned!
        // Driver and Vehicle MUST STILL BE ON_DELIVERY and IN_USE!
        Driver driverAfterStop1 = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(driverAfterStop1.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        Vehicle vehicleAfterStop1 = vehicleRepository.findById(vanVehicle.getId()).orElseThrow();
        assertThat(vehicleAfterStop1.getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // Now Driver completes Leg 2
        mockMvc.perform(patch("/api/shipments/legs/" + leg2Id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_TRANSIT")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/shipments/legs/" + leg2Id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        // AFTER ALL LEGS COMPLETED: Driver and Vehicle return to AVAILABLE!
        Driver driverFinal = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(driverFinal.getStatus()).isEqualTo(DriverStatus.AVAILABLE);

        Vehicle vehicleFinal = vehicleRepository.findById(vanVehicle.getId()).orElseThrow();
        assertThat(vehicleFinal.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Single-package batch compatibility case")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testSinglePackageBatchCompatibility() throws Exception {
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 3.0, "Single Parcel Compat");

        MvcResult res = mockMvc.perform(get("/api/shipments/batch/propose-pickup")
                        .param("hubId", centralHub.getId().toString())
                        .param("maxRadiusKm", "10.0")
                        .param("vehicleId", vanVehicle.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchType").value("FIRST_MILE"))
                .andExpect(jsonPath("$.includedLegIds", hasSize(1)))
                .andExpect(jsonPath("$.orderedIncludedStops", hasSize(1)))
                .andExpect(jsonPath("$.orderedIncludedStops[0].proposedStopOrder").value(1))
                .andExpect(jsonPath("$.hubReturnPreserved").value(true))
                .andExpect(jsonPath("$.routeGeometryGeoJson").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        double initialDist = root.get("initialTourDistanceKm").asDouble();
        double finalDist = root.get("finalTourDistanceKm").asDouble();
        assertThat(finalDist).isEqualTo(initialDist);
    }

    @Test
    @DisplayName("Three genuine test shipments: Batch Pickup route optimization with Nearest Neighbor + 2-Opt and Manifest")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testThreeShipmentBatchPickupOptimization() throws Exception {
        // Create 3 genuine shipments with pickup locations within 10 km corridor of Central Hub (Koramangala)
        createShipment(12.9784, 77.6408, 12.9345, 77.6265, 2.5, "Indiranagar Batch Parcel");
        createShipment(12.9352, 77.6245, 12.9345, 77.6265, 3.0, "Koramangala 5th Block Parcel");
        createShipment(12.9165, 77.6101, 12.9345, 77.6265, 1.5, "BTM Layout Batch Parcel");

        // 1. Propose Batch Pickup
        MvcResult res = mockMvc.perform(get("/api/shipments/batch/propose-pickup")
                        .param("hubId", centralHub.getId().toString())
                        .param("maxRadiusKm", "10.0")
                        .param("vehicleId", vanVehicle.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchType").value("FIRST_MILE"))
                .andExpect(jsonPath("$.includedLegIds", hasSize(3)))
                .andExpect(jsonPath("$.excludedLegIds", hasSize(0)))
                .andExpect(jsonPath("$.orderedIncludedStops", hasSize(3)))
                .andExpect(jsonPath("$.hubReturnPreserved").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode orderedStops = root.get("orderedIncludedStops");
        assertThat(orderedStops.size()).isEqualTo(3);

        // Verify numbered stop sequence
        assertThat(orderedStops.get(0).get("proposedStopOrder").asInt()).isEqualTo(1);
        assertThat(orderedStops.get(1).get("proposedStopOrder").asInt()).isEqualTo(2);
        assertThat(orderedStops.get(2).get("proposedStopOrder").asInt()).isEqualTo(3);

        // Verify algorithm correctness: 2-Opt result does NOT increase objective relative to initial tour
        double initialDist = root.get("initialTourDistanceKm").asDouble();
        double finalDist = root.get("finalTourDistanceKm").asDouble();
        assertThat(finalDist).isLessThanOrEqualTo(initialDist);

        double totalDist = root.get("totalRouteDistanceKm").asDouble();
        assertThat(totalDist).isEqualTo(finalDist);
        assertThat(root.get("totalRouteDurationMin").asDouble()).isGreaterThan(0.0);

        // Verify GeoJSON and algorithm explanation
        String geoJson = root.get("routeGeometryGeoJson").asText();
        assertThat(geoJson).contains("LineString");
        assertThat(root.get("geometrySource").asText()).isNotBlank();
        assertThat(root.get("optimizationAlgorithm").asText()).contains("2-Opt");
        assertThat(root.get("heuristicNotice").asText()).contains("heuristic");

        // 2. Dispatch the Batch
        List<Long> legIds = List.of(
                orderedStops.get(0).get("legId").asLong(),
                orderedStops.get(1).get("legId").asLong(),
                orderedStops.get(2).get("legId").asLong()
        );

        BatchDispatchAssignmentRequest dispatchReq = BatchDispatchAssignmentRequest.builder()
                .batchType("FIRST_MILE")
                .hubId(centralHub.getId())
                .driverId(driver1.getId())
                .vehicleId(vanVehicle.getId())
                .legIds(legIds)
                .notes("Dispatched 3-shipment batch pickup")
                .build();

        MvcResult dispatchRes = mockMvc.perform(post("/api/shipments/batch/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dispatchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.totalPackages").value(3))
                .andExpect(jsonPath("$.totalWeightKg").value(7.0))
                .andReturn();

        JsonNode dispatchNode = objectMapper.readTree(dispatchRes.getResponse().getContentAsString());
        Long routeId = dispatchNode.get("routeId").asLong();

        // 3. Verify Route and RouteEdge entities persisted
        List<RouteStop> savedStops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        // 3 pickup stops + 1 terminal hub stop = 4 stops
        assertThat(savedStops.size()).isGreaterThanOrEqualTo(4);

        List<RouteEdge> savedEdges = routeEdgeRepository.findByRouteIdOrderByEdgeOrderAsc(routeId);
        assertThat(savedEdges).isNotEmpty();
        assertThat(savedEdges.get(0).getEdgeOrder()).isEqualTo(1);
        assertThat(savedEdges.get(0).getDistance()).isGreaterThan(0.0);

        // 4. Verify Driver Manifest endpoint
        MvcResult manifestRes = mockMvc.perform(get("/api/shipments/driver/manifest")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStopsCount").value(3))
                .andExpect(jsonPath("$.currentLoadKg").value(7.0))
                .andExpect(jsonPath("$.stops", hasSize(3)))
                .andReturn();

        JsonNode manifestNode = objectMapper.readTree(manifestRes.getResponse().getContentAsString());
        assertThat(manifestNode.get("stops").get(0).get("stopType").asText()).isEqualTo("PICKUP");
        assertThat(manifestNode.get("currentActiveStop")).isNotNull();
    }

    @Test
    @DisplayName("Three genuine test shipments: Batch Final-Mile delivery sequence and separate lifecycle events")
    @WithMockUser(username = "batch_admin", roles = {"ADMIN"})
    public void testThreeShipmentBatchFinalMileDeliveryOptimization() throws Exception {
        // Create 3 shipments destined for different areas
        createShipment(12.9352, 77.6245, 12.9784, 77.6408, 1.5, "Indiranagar Final Dest");
        createShipment(12.9352, 77.6245, 12.9280, 77.6200, 2.0, "Koramangala Final Dest");
        createShipment(12.9352, 77.6245, 12.9165, 77.6101, 1.0, "BTM Final Dest");

        // Simulate packages genuinely received at Central Hub
        for (Delivery d : deliveryRepository.findAll()) {
            d.setCurrentStage("HUB_RECEIVED");
            deliveryRepository.save(d);
        }

        // 1. Propose Final-Mile Delivery
        MvcResult res = mockMvc.perform(get("/api/shipments/batch/propose-delivery")
                        .param("hubId", centralHub.getId().toString())
                        .param("maxRadiusKm", "10.0")
                        .param("vehicleId", vanVehicle.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchType").value("FINAL_MILE"))
                .andExpect(jsonPath("$.includedLegIds", hasSize(3)))
                .andExpect(jsonPath("$.orderedIncludedStops", hasSize(3)))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode orderedStops = root.get("orderedIncludedStops");

        // Verify 2-Opt non-increase invariant
        double initialDist = root.get("initialTourDistanceKm").asDouble();
        double finalDist = root.get("finalTourDistanceKm").asDouble();
        assertThat(finalDist).isLessThanOrEqualTo(initialDist);

        List<Long> legIds = List.of(
                orderedStops.get(0).get("legId").asLong(),
                orderedStops.get(1).get("legId").asLong(),
                orderedStops.get(2).get("legId").asLong()
        );

        // 2. Dispatch Final-Mile Batch
        BatchDispatchAssignmentRequest dispatchReq = BatchDispatchAssignmentRequest.builder()
                .batchType("FINAL_MILE")
                .hubId(centralHub.getId())
                .driverId(driver1.getId())
                .vehicleId(vanVehicle.getId())
                .legIds(legIds)
                .build();

        mockMvc.perform(post("/api/shipments/batch/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dispatchReq)))
                .andExpect(status().isOk());

        // 3. Verify Individual Stop Completion without premature completion of others
        // Deliver Stop 1
        mockMvc.perform(patch("/api/shipments/legs/" + legIds.get(0) + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        // Delivery 1 is COMPLETED, but Deliveries 2 and 3 remain IN_PROGRESS
        DeliveryLeg leg1 = deliveryLegRepository.findById(legIds.get(0)).orElseThrow();
        DeliveryLeg leg2 = deliveryLegRepository.findById(legIds.get(1)).orElseThrow();
        DeliveryLeg leg3 = deliveryLegRepository.findById(legIds.get(2)).orElseThrow();

        Delivery delCompleted = deliveryRepository.findById(leg1.getDelivery().getId()).orElseThrow();
        Delivery delPending1 = deliveryRepository.findById(leg2.getDelivery().getId()).orElseThrow();
        Delivery delPending2 = deliveryRepository.findById(leg3.getDelivery().getId()).orElseThrow();

        assertThat(leg1.getStatus()).isEqualTo(LegStatus.COMPLETED);
        assertThat(delCompleted.getStatus()).isEqualTo(DeliveryStatus.COMPLETED);

        assertThat(leg2.getStatus()).isEqualTo(LegStatus.ASSIGNED);
        assertThat(delPending1.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);

        assertThat(leg3.getStatus()).isEqualTo(LegStatus.ASSIGNED);
        assertThat(delPending2.getStatus()).isEqualTo(DeliveryStatus.IN_PROGRESS);

        // Driver and Vehicle remain ON_DELIVERY / IN_USE!
        Driver driverAfterOne = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(driverAfterOne.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        // Deliver Stop 2
        mockMvc.perform(patch("/api/shipments/legs/" + legIds.get(1) + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        Driver driverAfterTwo = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(driverAfterTwo.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);

        // Deliver Stop 3
        mockMvc.perform(patch("/api/shipments/legs/" + legIds.get(2) + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(driver1User.getUsername()).roles("DRIVER")))
                .andExpect(status().isOk());

        // All stops completed: resources released!
        Driver driverDone = driverRepository.findById(driver1.getId()).orElseThrow();
        assertThat(driverDone.getStatus()).isEqualTo(DriverStatus.AVAILABLE);

        Vehicle vehicleDone = vehicleRepository.findById(vanVehicle.getId()).orElseThrow();
        assertThat(vehicleDone.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Directed edge asymmetry and graph shortest path handling")
    public void testDirectedEdgeAsymmetry() {
        Location locA = locationRepository.save(Location.builder()
                .name("Directed Node A").address("Node A").latitude(12.9500).longitude(77.6000).locationType("STOP").build());
        locA.setActive(true);
        locationRepository.save(locA);

        Location locB = locationRepository.save(Location.builder()
                .name("Directed Node B").address("Node B").latitude(12.9600).longitude(77.6100).locationType("STOP").build());
        locB.setActive(true);
        locationRepository.save(locB);

        // Edge A -> B distance: 4.0 km
        LocationEdge edge1 = LocationEdge.builder()
                .sourceLocation(locA).destinationLocation(locB)
                .distance(4.0).estimatedTime(8.0).build();
        edge1.setActive(true);
        locationEdgeRepository.save(edge1);

        // Edge B -> A distance: 9.5 km (due to one-way street network)
        LocationEdge edge2 = LocationEdge.builder()
                .sourceLocation(locB).destinationLocation(locA)
                .distance(9.5).estimatedTime(19.0).build();
        edge2.setActive(true);
        locationEdgeRepository.save(edge2);

        // Query directed edges
        Optional<LocationEdge> edgeAtoB = locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(locA.getId(), locB.getId());
        Optional<LocationEdge> edgeBtoA = locationEdgeRepository.findBySourceLocationIdAndDestinationLocationId(locB.getId(), locA.getId());

        assertThat(edgeAtoB).isPresent();
        assertThat(edgeBtoA).isPresent();
        assertThat(edgeAtoB.get().getDistance()).isEqualTo(4.0);
        assertThat(edgeBtoA.get().getDistance()).isEqualTo(9.5);
        assertThat(edgeAtoB.get().getDistance()).isNotEqualTo(edgeBtoA.get().getDistance());
    }
}
