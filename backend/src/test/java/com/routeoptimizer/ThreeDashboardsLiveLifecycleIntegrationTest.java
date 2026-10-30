package com.routeoptimizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.AssignLegRequest;
import com.routeoptimizer.dto.CustomerShipmentRequest;
import com.routeoptimizer.entity.Driver;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.entity.Vehicle;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class ThreeDashboardsLiveLifecycleIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private User customerUser;
    private User otherCustomerUser;
    private User adminUser;
    private User driver1User;
    private User driver2User;

    private Driver driver1;
    private Driver driver2;
    private Vehicle vehicle1;
    private Vehicle vehicle2;
    private Location centralHub;

    @BeforeEach
    public void setup() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        // Clean tables in reverse dependency order
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

        // 1. Seed Hub Location in Bengaluru
        centralHub = Location.builder()
                .name("Bengaluru Central Sortation Hub")
                .address("Koramangala 4th Block, Bengaluru")
                .city("Bengaluru")
                .latitude(12.9345)
                .longitude(77.6265)
                .locationType("HUB")
                .build();
        centralHub.setActive(true);
        centralHub.setVersion(0L);
        centralHub = locationRepository.save(centralHub);

        // 2. Seed Users: Customer, Other Customer, Admin, Driver 1, Driver 2
        customerUser = userRepository.save(User.builder()
                .username("test_customer")
                .email("customer@example.com")
                .password(passwordEncoder.encode("pass123"))
                .fullName("Anita Rao")
                .role(Role.USER)
                .isVerified(true)
                .isBlocked(false)
                .build());
        customerUser.setActive(true);

        otherCustomerUser = userRepository.save(User.builder()
                .username("other_customer")
                .email("other@example.com")
                .password(passwordEncoder.encode("pass123"))
                .fullName("Rajesh Kumar")
                .role(Role.USER)
                .isVerified(true)
                .isBlocked(false)
                .build());
        otherCustomerUser.setActive(true);

        adminUser = userRepository.save(User.builder()
                .username("test_admin")
                .email("admin@example.com")
                .password(passwordEncoder.encode("pass123"))
                .fullName("Dispatcher Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build());
        adminUser.setActive(true);

        driver1User = userRepository.save(User.builder()
                .username("driver_one")
                .email("driver1@example.com")
                .password(passwordEncoder.encode("pass123"))
                .fullName("Suresh Pickup")
                .role(Role.DRIVER)
                .isVerified(true)
                .isBlocked(false)
                .build());
        driver1User.setActive(true);

        driver2User = userRepository.save(User.builder()
                .username("driver_two")
                .email("driver2@example.com")
                .password(passwordEncoder.encode("pass123"))
                .fullName("Vikram FinalMile")
                .role(Role.DRIVER)
                .isVerified(true)
                .isBlocked(false)
                .build());
        driver2User.setActive(true);

        // 3. Seed Drivers linked to Driver Users
        driver1 = driverRepository.save(Driver.builder()
                .user(driver1User)
                .name("Suresh Pickup")
                .phone("9876543210")
                .licenseNumber("KA01-2023-0001")
                .status(DriverStatus.AVAILABLE)
                .build());
        driver1.setActive(true);

        driver2 = driverRepository.save(Driver.builder()
                .user(driver2User)
                .name("Vikram FinalMile")
                .phone("9876543211")
                .licenseNumber("KA01-2023-0002")
                .status(DriverStatus.AVAILABLE)
                .build());
        driver2.setActive(true);

        // 4. Seed Vehicles
        vehicle1 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA04EV1001")
                .vehicleType(VehicleType.VAN)
                .capacity(150.0)
                .status(VehicleStatus.AVAILABLE)
                .build());
        vehicle1.setActive(true);

        vehicle2 = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA04EV2002")
                .vehicleType(VehicleType.TRUCK)
                .capacity(500.0)
                .status(VehicleStatus.AVAILABLE)
                .build());
        vehicle2.setActive(true);
    }

    @Test
    @DisplayName("Complete Live 6-Stage Lifecycle across Customer, Admin, and Driver Dashboards")
    @WithMockUser(username = "test_customer", roles = {"USER"})
    public void testFullLiveLifecycleBetweenUserAdminAndDriver() throws Exception {
        // =========================================================================
        // STEP 1: USER submits valid shipment request (POST /api/shipments)
        // =========================================================================
        CustomerShipmentRequest shipmentReq = CustomerShipmentRequest.builder()
                .senderName("Anita Rao")
                .senderPhone("9876500001")
                .senderEmail("customer@example.com")
                .pickupAddress("Indiranagar 100ft Road, Bengaluru")
                .pickupCity("Bengaluru")
                .pickupLatitude(12.9716)
                .pickupLongitude(77.6412)
                .receiverName("Pooja Hegde")
                .receiverPhone("9876500002")
                .receiverEmail("pooja@example.com")
                .deliveryAddress("Electronic City Phase 1, Bengaluru")
                .deliveryCity("Bengaluru")
                .deliveryLatitude(12.8452)
                .deliveryLongitude(77.6602)
                .itemName("Solar Powered Drone Kit")
                .itemCategory("Electronics")
                .description("Fragile precision aerial drone")
                .weight(4.5)
                .declaredValue(45000.0)
                .fragile(true)
                .priority("HIGH")
                .quantity(1)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipmentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.trackingNumber").value(startsWith("PKG-")))
                .andExpect(jsonPath("$.currentStage").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.hubName").value("Bengaluru Central Sortation Hub"))
                .andExpect(jsonPath("$.packageDetails.itemName").value("Solar Powered Drone Kit"))
                .andExpect(jsonPath("$.packageDetails.weight").value(4.5))
                .andExpect(jsonPath("$.legs", hasSize(2)))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].stage").value("REQUEST_CREATED"))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long deliveryId = createdJson.get("id").asLong();
        String trackingNumber = createdJson.get("trackingNumber").asText();
        Long leg1Id = createdJson.get("legs").get(0).get("id").asLong();
        Long leg2Id = createdJson.get("legs").get(1).get("id").asLong();

        // =========================================================================
        // STEP 2: ADMIN sees the exact shipment in dispatch queue (GET /api/shipments)
        // =========================================================================
        mockMvc.perform(get("/api/shipments")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test_admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(deliveryId))
                .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$[0].currentStage").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$[0].legs[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].legs[1].status").value("PENDING"));

        // =========================================================================
        // STEP 3: FINAL_MILE Gating Check (Requirement 6)
        // ADMIN attempting to assign Leg 2 (FINAL_MILE) before Leg 1 completes MUST FAIL
        // =========================================================================
        AssignLegRequest prematureFinalMile = AssignLegRequest.builder()
                .driverId(driver2.getId())
                .vehicleId(vehicle2.getId())
                .notes("Attempting premature final mile")
                .build();

        mockMvc.perform(post("/api/shipments/legs/{legId}/assign", leg2Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test_admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prematureFinalMile)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Package has not yet arrived at Central Hub")));

        // =========================================================================
        // STEP 4: ADMIN assigns eligible pickup driver (driver1) & vehicle (vehicle1) to Leg 1
        // =========================================================================
        AssignLegRequest leg1Assign = AssignLegRequest.builder()
                .driverId(driver1.getId())
                .vehicleId(vehicle1.getId())
                .notes("Sender pickup priority morning slot")
                .build();

        mockMvc.perform(post("/api/shipments/legs/{legId}/assign", leg1Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test_admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leg1Assign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("PICKUP_ASSIGNED"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[1].stage").value("PICKUP_ASSIGNED"));

        // Verify Driver 1 status changed to ON_DELIVERY and Vehicle 1 changed to IN_USE
        Driver d1AfterAssign = driverRepository.findById(driver1.getId()).orElseThrow();
        Vehicle v1AfterAssign = vehicleRepository.findById(vehicle1.getId()).orElseThrow();
        assertThat(d1AfterAssign.getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(v1AfterAssign.getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // =========================================================================
        // STEP 5: DRIVER Task Isolation (Requirement 4)
        // Leg 1 appears ONLY in Driver 1's dashboard, NOT Driver 2
        // =========================================================================
        mockMvc.perform(get("/api/shipments/driver/assigned-legs")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_one").roles("DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(leg1Id))
                .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].senderName").value("Anita Rao"))
                .andExpect(jsonPath("$[0].packageWeight").value(4.5));

        mockMvc.perform(get("/api/shipments/driver/assigned-legs")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_two").roles("DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Unassigned driver cannot transition someone else's leg (Security Isolation)
        mockMvc.perform(patch("/api/shipments/legs/{legId}/status", leg1Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_two").roles("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_TRANSIT"))))
                .andExpect(status().isForbidden());

        // =========================================================================
        // STEP 6: Driver 1 confirms pickup (IN_TRANSIT) -> Milestone 3 (PICKED_UP)
        // =========================================================================
        mockMvc.perform(patch("/api/shipments/legs/{legId}/status", leg1Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_one").roles("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_TRANSIT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("PICKED_UP"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[2].stage").value("PICKED_UP"));

        // =========================================================================
        // STEP 7: Driver 1 arrives at Central Hub (COMPLETED) -> Milestone 4 (HUB_RECEIVED)
        // Releases Driver 1 & Vehicle 1 back to AVAILABLE
        // =========================================================================
        mockMvc.perform(patch("/api/shipments/legs/{legId}/status", leg1Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_one").roles("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("HUB_RECEIVED"))
                .andExpect(jsonPath("$.events", hasSize(4)))
                .andExpect(jsonPath("$.events[3].stage").value("HUB_RECEIVED"));

        Driver d1AfterHub = driverRepository.findById(driver1.getId()).orElseThrow();
        Vehicle v1AfterHub = vehicleRepository.findById(vehicle1.getId()).orElseThrow();
        assertThat(d1AfterHub.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(v1AfterHub.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        // =========================================================================
        // STEP 8: FINAL_MILE Assignment is now permitted (Requirement 6 & 7)
        // ADMIN assigns Driver 2 and Vehicle 2 to Leg 2
        // =========================================================================
        AssignLegRequest leg2Assign = AssignLegRequest.builder()
                .driverId(driver2.getId())
                .vehicleId(vehicle2.getId())
                .notes("Final-mile delivery partner dispatched")
                .build();

        mockMvc.perform(post("/api/shipments/legs/{legId}/assign", leg2Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test_admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leg2Assign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.events", hasSize(5)))
                .andExpect(jsonPath("$.events[4].stage").value("OUT_FOR_DELIVERY"));

        // Driver 2 now sees Leg 2
        mockMvc.perform(get("/api/shipments/driver/assigned-legs")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_two").roles("DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(leg2Id))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].recipientName").value("Pooja Hegde"));

        // =========================================================================
        // STEP 9: Final-Mile Driver delivers parcel (COMPLETED) -> Milestone 6 (DELIVERED)
        // Releases Driver 2 & Vehicle 2 back to AVAILABLE
        // =========================================================================
        mockMvc.perform(patch("/api/shipments/legs/{legId}/status", leg2Id)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("driver_two").roles("DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("DELIVERED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.events", hasSize(6)))
                .andExpect(jsonPath("$.events[5].stage").value("DELIVERED"));

        Driver d2AfterDelivered = driverRepository.findById(driver2.getId()).orElseThrow();
        Vehicle v2AfterDelivered = vehicleRepository.findById(vehicle2.getId()).orElseThrow();
        assertThat(d2AfterDelivered.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(v2AfterDelivered.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        // =========================================================================
        // STEP 10: USER Tracking page displays exact same shipment & full 6-stage timeline (Requirement 8)
        // =========================================================================
        mockMvc.perform(get("/api/shipments/track/{trackingNumber}", trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.currentStage").value("DELIVERED"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.events", hasSize(6)))
                .andExpect(jsonPath("$.events[0].stage").value("REQUEST_CREATED"))
                .andExpect(jsonPath("$.events[1].stage").value("PICKUP_ASSIGNED"))
                .andExpect(jsonPath("$.events[2].stage").value("PICKED_UP"))
                .andExpect(jsonPath("$.events[3].stage").value("HUB_RECEIVED"))
                .andExpect(jsonPath("$.events[4].stage").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.events[5].stage").value("DELIVERED"));

        // Customer's my shipments view
        mockMvc.perform(get("/api/shipments/my")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test_customer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber));

        // Customer Ownership Isolation (Requirement 9): other customer cannot view shipment details by ID
        mockMvc.perform(get("/api/shipments/{id}", deliveryId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("other_customer").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
