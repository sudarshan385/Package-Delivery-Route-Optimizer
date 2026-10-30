package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.security.JwtTokenProvider;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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

    private String adminToken;
    private String driverToken;
    private Long driverProfileId;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    void setupAuthTokens() {
        // Admin
        User admin = User.builder()
                .username("admin_mdata")
                .email("admin@mdata.com")
                .password(passwordEncoder.encode("Secret123!"))
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        admin.setVersion(0L);
        userRepository.save(admin);
        adminToken = jwtTokenProvider.generateTokenForUsername("admin_mdata");

        // Driver
        User driverUser = User.builder()
                .username("driver_mdata")
                .email("driver@mdata.com")
                .password(passwordEncoder.encode("Secret123!"))
                .role(Role.DRIVER)
                .build();
        driverUser.setActive(true);
        driverUser.setVersion(0L);
        User savedDriverUser = userRepository.save(driverUser);
        driverToken = jwtTokenProvider.generateTokenForUsername("driver_mdata");

        Driver driverProfile = Driver.builder()
                .user(savedDriverUser)
                .name("Driver MData")
                .phone("+1234567890")
                .licenseNumber("DL-MDATA-01")
                .status(DriverStatus.AVAILABLE)
                .build();
        driverProfile.setActive(true);
        driverProfile.setVersion(0L);
        Driver savedDriver = driverRepository.save(driverProfile);
        driverProfileId = savedDriver.getId();
    }

    // ==========================================
    // 0. EMPTY DATABASE BEHAVIOR
    // ==========================================
    @Test
    @DisplayName("Empty database returns 200 OK with empty content for all master data endpoints")
    void testEmptyDatabaseBehavior() throws Exception {
        setupAuthTokens();

        mockMvc.perform(get("/api/locations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/edges")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get("/api/packages")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // ==========================================
    // 1. LOCATIONS TESTS
    // ==========================================
    @Test
    @DisplayName("Location CRUD, validation, pagination, and deactivation protection")
    void testLocationOperations() throws Exception {
        setupAuthTokens();

        // 1. Create Location
        LocationRequest createReq = LocationRequest.builder()
                .name("Central Hub")
                .address("100 Main St")
                .latitude(40.7128)
                .longitude(-74.0060)
                .locationType("WAREHOUSE")
                .build();

        String respJson = mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Central Hub")))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn().getResponse().getContentAsString();

        LocationResponse locResp = objectMapper.readValue(respJson, LocationResponse.class);
        Long locId = locResp.getId();

        // 2. Duplicate name validation
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 3. Invalid latitude validation
        LocationRequest invalidLat = LocationRequest.builder()
                .name("Invalid Lat Hub")
                .latitude(95.0) // Invalid
                .longitude(10.0)
                .build();
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLat)))
                .andExpect(status().isBadRequest());

        // 4. Retrieve location by ID
        mockMvc.perform(get("/api/locations/" + locId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(locId.intValue())))
                .andExpect(jsonPath("$.name", is("Central Hub")));

        // 5. Update location
        LocationRequest updateReq = LocationRequest.builder()
                .name("Central Hub Updated")
                .address("100 Main St Suite 200")
                .latitude(40.7128)
                .longitude(-74.0060)
                .locationType("WAREHOUSE")
                .build();

        mockMvc.perform(put("/api/locations/" + locId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Central Hub Updated")));

        // 6. Authorization: DRIVER forbidden to create or update
        mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // 7. Deactivate location
        mockMvc.perform(delete("/api/locations/" + locId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));

        Location updatedLoc = locationRepository.findById(locId).orElseThrow();
        assertThat(updatedLoc.getActive()).isFalse();
    }

    // ==========================================
    // 2. LOCATION EDGES / ROADS TESTS
    // ==========================================
    @Test
    @DisplayName("Edge CRUD, self-loop prevention, duplicate prevention, and distance validation")
    void testLocationEdgeOperations() throws Exception {
        setupAuthTokens();

        Location loc1 = locationRepository.save(Location.builder().name("Loc A").latitude(10.0).longitude(20.0).build());
        loc1.setActive(true);
        loc1.setVersion(0L);
        locationRepository.save(loc1);

        Location loc2 = locationRepository.save(Location.builder().name("Loc B").latitude(11.0).longitude(21.0).build());
        loc2.setActive(true);
        loc2.setVersion(0L);
        locationRepository.save(loc2);

        // 1. Create directed edge A -> B
        LocationEdgeRequest edgeReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .distance(15.5)
                .estimatedTime(25.0)
                .build();

        String resp = mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edgeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.distance", is(15.5)))
                .andReturn().getResponse().getContentAsString();

        LocationEdgeResponse edgeResp = objectMapper.readValue(resp, LocationEdgeResponse.class);
        Long edgeId = edgeResp.getId();

        // 2. Reject duplicate directed edge A -> B
        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edgeReq)))
                .andExpect(status().isConflict());

        // 3. Reverse direction B -> A is distinct and allowed
        LocationEdgeRequest reverseReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc2.getId())
                .destinationLocationId(loc1.getId())
                .distance(16.0)
                .estimatedTime(26.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reverseReq)))
                .andExpect(status().isCreated());

        // 4. Reject invalid self-loop (source == destination)
        LocationEdgeRequest selfLoopReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc1.getId())
                .distance(5.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfLoopReq)))
                .andExpect(status().isBadRequest());

        // 5. Reject invalid distance (<= 0)
        LocationEdgeRequest invalidDistReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .distance(-5.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDistReq)))
                .andExpect(status().isBadRequest());

        // 6. Reject invalid estimated time (< 0)
        LocationEdgeRequest invalidEstTimeReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .distance(10.0)
                .estimatedTime(-5.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEstTimeReq)))
                .andExpect(status().isBadRequest());

        // 7. Reject invalid source
        LocationEdgeRequest invalidSourceReq = LocationEdgeRequest.builder()
                .sourceLocationId(99999L)
                .destinationLocationId(loc2.getId())
                .distance(10.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSourceReq)))
                .andExpect(status().isNotFound());

        // 8. Reject invalid destination
        LocationEdgeRequest invalidDestReq = LocationEdgeRequest.builder()
                .sourceLocationId(loc1.getId())
                .destinationLocationId(99999L)
                .distance(10.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDestReq)))
                .andExpect(status().isNotFound());

        // 9. Reject inactive location
        Location locInactive = locationRepository.save(Location.builder().name("Loc Inactive").latitude(12.0).longitude(22.0).build());
        locInactive.setActive(false);
        locInactive.setVersion(0L);
        locationRepository.save(locInactive);

        LocationEdgeRequest inactiveLocReq = LocationEdgeRequest.builder()
                .sourceLocationId(locInactive.getId())
                .destinationLocationId(loc2.getId())
                .distance(10.0)
                .build();

        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveLocReq)))
                .andExpect(status().isBadRequest());

        // 10. Verify persistence
        mockMvc.perform(get("/api/edges/" + edgeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance", is(15.5)))
                .andExpect(jsonPath("$.estimatedTime", is(25.0)));

        // 11. Deactivate edge
        mockMvc.perform(delete("/api/edges/" + edgeId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    // ==========================================
    // 3. DRIVERS TESTS
    // ==========================================
    @Test
    @DisplayName("Driver CRUD, status management, authorization, and deactivation")
    void testDriverOperations() throws Exception {
        setupAuthTokens();

        // 1. Create Driver
        DriverRequest createReq = DriverRequest.builder()
                .name("Alex Driver")
                .phone("+1999888777")
                .licenseNumber("DL-ALEX-99")
                .status(DriverStatus.AVAILABLE)
                .build();

        String resp = mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.licenseNumber", is("DL-ALEX-99")))
                .andReturn().getResponse().getContentAsString();

        DriverResponse driverResp = objectMapper.readValue(resp, DriverResponse.class);
        Long newDriverId = driverResp.getId();

        // 2. Duplicate license number rejection
        mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 3. Driver viewing own profile (allowed)
        mockMvc.perform(get("/api/drivers/" + driverProfileId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(driverProfileId.intValue())));

        // 4. Driver viewing other driver's profile (forbidden)
        mockMvc.perform(get("/api/drivers/" + newDriverId)
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        // 5. Driver forbidden to list all drivers
        mockMvc.perform(get("/api/drivers")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        // 6. Update driver status
        DriverRequest updateReq = DriverRequest.builder()
                .name("Alex Driver Updated")
                .phone("+1999888000")
                .licenseNumber("DL-ALEX-99")
                .status(DriverStatus.OFF_DUTY)
                .build();

        mockMvc.perform(put("/api/drivers/" + newDriverId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("OFF_DUTY")));

        // 7. Deactivate driver
        mockMvc.perform(delete("/api/drivers/" + newDriverId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    // ==========================================
    // 4. VEHICLES TESTS
    // ==========================================
    @Test
    @DisplayName("Vehicle CRUD, registration uniqueness, status, and role protection")
    void testVehicleOperations() throws Exception {
        setupAuthTokens();

        // 1. Create Vehicle
        VehicleRequest createReq = VehicleRequest.builder()
                .registrationNumber("REG-VAN-101")
                .vehicleType(VehicleType.VAN)
                .capacity(1200.0)
                .status(VehicleStatus.AVAILABLE)
                .build();

        String resp = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.registrationNumber", is("REG-VAN-101")))
                .andReturn().getResponse().getContentAsString();

        VehicleResponse vehicleResp = objectMapper.readValue(resp, VehicleResponse.class);
        Long vehId = vehicleResp.getId();

        // 2. Duplicate registration number rejection
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 3. Driver forbidden to create or update vehicles
        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // 4. Update vehicle
        VehicleRequest updateReq = VehicleRequest.builder()
                .registrationNumber("REG-VAN-101")
                .vehicleType(VehicleType.VAN)
                .capacity(1500.0)
                .status(VehicleStatus.MAINTENANCE)
                .build();

        mockMvc.perform(put("/api/vehicles/" + vehId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity", is(1500.0)))
                .andExpect(jsonPath("$.status", is("MAINTENANCE")));

        // 5. Deactivate vehicle
        mockMvc.perform(delete("/api/vehicles/" + vehId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.status", is("OFFLINE")));
    }

    // ==========================================
    // 5. PACKAGES TESTS
    // ==========================================
    @Test
    @DisplayName("Package CRUD, lifecycle protection, status immutability, and validation")
    void testPackageOperations() throws Exception {
        setupAuthTokens();

        Location loc1 = locationRepository.save(Location.builder().name("Sender Depot").latitude(10.0).longitude(20.0).build());
        loc1.setActive(true);
        loc1.setVersion(0L);
        locationRepository.save(loc1);

        Location loc2 = locationRepository.save(Location.builder().name("Receiver Hub").latitude(11.0).longitude(21.0).build());
        loc2.setActive(true);
        loc2.setVersion(0L);
        locationRepository.save(loc2);

        // 1. Create Package (must start as PENDING)
        PackageRequest createReq = PackageRequest.builder()
                .trackingNumber("TRK-TEST-001")
                .senderName("Alice Sender")
                .senderPhone("111222333")
                .receiverName("Bob Receiver")
                .receiverPhone("444555666")
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .weight(12.5)
                .priority(Priority.HIGH)
                .description("Fragile electronics")
                .build();

        String resp = mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.trackingNumber", is("TRK-TEST-001")))
                .andReturn().getResponse().getContentAsString();

        PackageResponse pkgResp = objectMapper.readValue(resp, PackageResponse.class);
        Long pkgId = pkgResp.getId();

        // 2. Reject package created with non-PENDING status
        PackageRequest invalidStatusReq = PackageRequest.builder()
                .trackingNumber("TRK-TEST-002")
                .senderName("Alice")
                .receiverName("Bob")
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .weight(5.0)
                .status(PackageStatus.DELIVERED) // Not allowed
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStatusReq)))
                .andExpect(status().isBadRequest());

        // 3. Reject source == destination
        PackageRequest sameLocReq = PackageRequest.builder()
                .trackingNumber("TRK-TEST-003")
                .senderName("Alice")
                .receiverName("Bob")
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc1.getId()) // Same
                .weight(5.0)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameLocReq)))
                .andExpect(status().isBadRequest());

        // 4. Reject arbitrary status transition bypassing delivery lifecycle
        PackageRequest statusChangeReq = PackageRequest.builder()
                .senderName("Alice Sender")
                .receiverName("Bob Receiver")
                .sourceLocationId(loc1.getId())
                .destinationLocationId(loc2.getId())
                .weight(12.5)
                .status(PackageStatus.DELIVERED) // Attempt to bypass delivery lifecycle
                .build();

        mockMvc.perform(put("/api/packages/" + pkgId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusChangeReq)))
                .andExpect(status().isBadRequest());

        // 5. Driver forbidden to create or update package
        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // 6. Deactivate package
        mockMvc.perform(delete("/api/packages/" + pkgId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    @Test
    @DisplayName("7. Package creation and validation with City and user-entered locations")
    void testPackageCreationWithCityAndUserEnteredLocations() throws Exception {
        setupAuthTokens();

        // 1. Bengaluru + real pickup + real destination
        PackageRequest blrReq = PackageRequest.builder()
                .city("Bengaluru")
                .pickupLocation("Electronic City Phase 1")
                .deliveryLocation("Whitefield")
                .senderName("Aarav Sharma")
                .senderPhone("9876543210")
                .receiverName("Priya Nair")
                .receiverPhone("9876543211")
                .weight(4.5)
                .priority(Priority.HIGH)
                .description("Tech components")
                .build();

        String blrResp = mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blrReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.city", is("Bengaluru")))
                .andExpect(jsonPath("$.pickupLocation", is("Electronic City Phase 1")))
                .andExpect(jsonPath("$.deliveryLocation", is("Whitefield")))
                .andExpect(jsonPath("$.sourceLocationName", notNullValue()))
                .andExpect(jsonPath("$.destinationLocationName", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        PackageResponse blrPackage = objectMapper.readValue(blrResp, PackageResponse.class);

        // 2. Pune + real pickup + real destination
        PackageRequest puneReq = PackageRequest.builder()
                .city("Pune")
                .pickupLocation("Hinjewadi Phase 1")
                .deliveryLocation("Viman Nagar")
                .senderName("Rohan Kulkarni")
                .receiverName("Sneha Patil")
                .weight(2.0)
                .priority(Priority.MEDIUM)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(puneReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city", is("Pune")))
                .andExpect(jsonPath("$.pickupLocation", is("Hinjewadi Phase 1")))
                .andExpect(jsonPath("$.deliveryLocation", is("Viman Nagar")));

        // 3. Mumbai + real pickup + real destination
        PackageRequest mumbaiReq = PackageRequest.builder()
                .city("Mumbai")
                .pickupLocation("Andheri East")
                .deliveryLocation("Powai")
                .senderName("Vikram Mehta")
                .receiverName("Neha Shah")
                .weight(8.2)
                .priority(Priority.URGENT)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mumbaiReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city", is("Mumbai")))
                .andExpect(jsonPath("$.pickupLocation", is("Andheri East")))
                .andExpect(jsonPath("$.deliveryLocation", is("Powai")));

        // 4. Reject empty city
        PackageRequest emptyCityReq = PackageRequest.builder()
                .city("")
                .pickupLocation("Andheri East")
                .deliveryLocation("Powai")
                .senderName("Vikram")
                .receiverName("Neha")
                .weight(5.0)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyCityReq)))
                .andExpect(status().isBadRequest());

        // 5. Reject empty pickup
        PackageRequest emptyPickupReq = PackageRequest.builder()
                .city("Bengaluru")
                .pickupLocation("   ")
                .deliveryLocation("Whitefield")
                .senderName("Vikram")
                .receiverName("Neha")
                .weight(5.0)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyPickupReq)))
                .andExpect(status().isBadRequest());

        // 6. Reject empty destination
        PackageRequest emptyDestReq = PackageRequest.builder()
                .city("Bengaluru")
                .pickupLocation("Electronic City")
                .deliveryLocation("")
                .senderName("Vikram")
                .receiverName("Neha")
                .weight(5.0)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyDestReq)))
                .andExpect(status().isBadRequest());

        // 7. Reject same pickup and destination
        PackageRequest sameLocReq = PackageRequest.builder()
                .city("Bengaluru")
                .pickupLocation("Electronic City Phase 1")
                .deliveryLocation("electronic city phase 1")
                .senderName("Vikram")
                .receiverName("Neha")
                .weight(5.0)
                .build();

        mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameLocReq)))
                .andExpect(status().isBadRequest());

        // 8. Verify persistence by GET /api/packages/{id}
        mockMvc.perform(get("/api/packages/" + blrPackage.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city", is("Bengaluru")))
                .andExpect(jsonPath("$.pickupLocation", is("Electronic City Phase 1")))
                .andExpect(jsonPath("$.deliveryLocation", is("Whitefield")));
    }
}
