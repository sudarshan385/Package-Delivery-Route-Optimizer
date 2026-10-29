package com.routeoptimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.dto.*;
import com.routeoptimizer.enums.*;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.security.JwtTokenProvider;
import com.routeoptimizer.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class BackendEndToEndAndAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PackageRepository packageRepository;

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

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        for (String table : REQUIRED_TABLES) {
            com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
        }
    }

    // ==========================================
    // 1. EMPTY DATABASE DASHBOARD, ANALYTICS & REPORTS
    // ==========================================
    @Test
    @DisplayName("Empty database returns zero metrics for dashboard and analytics, and header-only reports")
    void testEmptyDatabaseMetricsAndReports() throws Exception {
        // Initial setup creates first ADMIN
        SetupRequest setupReq = SetupRequest.builder()
                .username("admin_e2e")
                .email("admin_e2e@routeoptimizer.com")
                .password("Password123!")
                .fullName("System Administrator")
                .build();

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setupReq)))
                .andExpect(status().isCreated());

        String adminToken = jwtTokenProvider.generateTokenForUsername("admin_e2e");

        // 1. Dashboard empty verification
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPackages", is(0)))
                .andExpect(jsonPath("$.totalDeliveries", is(0)))
                .andExpect(jsonPath("$.completedDeliveries", is(0)))
                .andExpect(jsonPath("$.failedDeliveries", is(0)))
                .andExpect(jsonPath("$.cancelledDeliveries", is(0)))
                .andExpect(jsonPath("$.availableDrivers", is(0)))
                .andExpect(jsonPath("$.driversOnDelivery", is(0)))
                .andExpect(jsonPath("$.availableVehicles", is(0)))
                .andExpect(jsonPath("$.vehiclesInUse", is(0)))
                .andExpect(jsonPath("$.totalRoutes", is(0)))
                .andExpect(jsonPath("$.totalRouteDistance", is(0.0)));

        // 2. Analytics empty verification
        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageStats.totalPackages", is(0)))
                .andExpect(jsonPath("$.deliveryStats.totalDeliveries", is(0)))
                .andExpect(jsonPath("$.deliveryStats.completionRate", is(0.0)))
                .andExpect(jsonPath("$.driverUtilization.totalDrivers", is(0)))
                .andExpect(jsonPath("$.vehicleUtilization.totalVehicles", is(0)))
                .andExpect(jsonPath("$.routeStats.totalRoutes", is(0)));

        // 3. Delivery CSV Report empty verification (header only)
        String deliveryCsv = mockMvc.perform(get("/api/reports/deliveries")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"deliveries_report.csv\"")))
                .andReturn().getResponse().getContentAsString();

        assertThat(deliveryCsv.trim()).isEqualTo(
                "delivery_id,package_tracking_number,driver,vehicle,status,created_at,dispatch_time,completion_time");

        // 4. Route CSV Report empty verification (header only)
        String routeCsv = mockMvc.perform(get("/api/reports/routes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"routes_report.csv\"")))
                .andReturn().getResponse().getContentAsString();

        assertThat(routeCsv.trim()).isEqualTo(
                "route_id,delivery_id,source,destination,distance_km,algorithm,created_at");
    }

    // ==========================================
    // 2. AUTHORIZATION & SECURITY
    // ==========================================
    @Test
    @DisplayName("Authorization: DRIVER is forbidden from analytics and reports, unauthenticated gets 401")
    void testAuthorization() throws Exception {
        // Setup admin
        SetupRequest setupReq = SetupRequest.builder()
                .username("admin_auth")
                .email("admin_auth@routeoptimizer.com")
                .password("Password123!")
                .build();
        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setupReq)))
                .andExpect(status().isCreated());

        String adminToken = jwtTokenProvider.generateTokenForUsername("admin_auth");

        // Create driver user
        CreateUserRequest driverUserReq = CreateUserRequest.builder()
                .username("driver_auth")
                .email("driver_auth@test.com")
                .password("Password123!")
                .role(Role.DRIVER)
                .licenseNumber("DL-AUTH-001")
                .build();
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driverUserReq)))
                .andExpect(status().isCreated());

        String driverToken = jwtTokenProvider.generateTokenForUsername("driver_auth");

        // Unauthenticated -> 401
        mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analytics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/deliveries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/routes")).andExpect(status().isUnauthorized());

        // DRIVER:
        // Dashboard -> allowed for operational overview
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isOk());

        // Analytics -> forbidden
        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        // Delivery Report -> forbidden
        mockMvc.perform(get("/api/reports/deliveries")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());

        // Route Report -> forbidden
        mockMvc.perform(get("/api/reports/routes")
                        .header("Authorization", "Bearer " + driverToken))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 3. CSV FORMULA INJECTION & ESCAPING TEST
    // ==========================================
    @Test
    @DisplayName("CSV Formula Injection prevention and RFC 4180 delimiter escaping")
    void testCsvEscapingAndFormulaInjection() {
        // Formula injection prefixes (=, +, -, @, tab, CR)
        assertThat(ReportService.escapeCsvField("=cmd|' /C calc'!A0")).isEqualTo("'=cmd|' /C calc'!A0");
        assertThat(ReportService.escapeCsvField("+1+2")).isEqualTo("'+1+2");
        assertThat(ReportService.escapeCsvField("-5-5")).isEqualTo("'-5-5");
        assertThat(ReportService.escapeCsvField("@SUM(12)")).isEqualTo("'@SUM(12)");
        assertThat(ReportService.escapeCsvField("@SUM(1,2)")).isEqualTo("\"'@SUM(1,2)\"");

        // Delimiter escaping (commas, quotes, newlines)
        assertThat(ReportService.escapeCsvField("Hello, World")).isEqualTo("\"Hello, World\"");
        assertThat(ReportService.escapeCsvField("He said \"Hello\"")).isEqualTo("\"He said \"\"Hello\"\"\"");
        assertThat(ReportService.escapeCsvField("Line1\nLine2")).isEqualTo("\"Line1\nLine2\"");

        // Null and normal string
        assertThat(ReportService.escapeCsvField(null)).isEqualTo("");
        assertThat(ReportService.escapeCsvField("Normal Text")).isEqualTo("Normal Text");
    }

    // ==========================================
    // 4. REAL 20-STEP END-TO-END WORKFLOW
    // ==========================================
    @Test
    @DisplayName("Complete 20-step real end-to-end backend delivery, optimization, dashboard, and reporting workflow")
    void testCompleteBackendWorkflow() throws Exception {
        // Step 1: Fresh database verified by cleanDatabase()

        // Step 2: Initial admin setup
        SetupRequest setupReq = SetupRequest.builder()
                .username("master_admin")
                .email("admin@routeoptimizer.com")
                .password("SecureAdmin123!")
                .fullName("Master Administrator")
                .build();

        mockMvc.perform(post("/api/auth/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setupReq)))
                .andExpect(status().isCreated());

        String adminToken = jwtTokenProvider.generateTokenForUsername("master_admin");

        // Step 3: Create locations (Warehouse Depot, Transit Hub, Customer Address)
        LocationRequest depotReq = LocationRequest.builder()
                .name("Main Depot")
                .address("1 Logistics Parkway")
                .latitude(40.7128)
                .longitude(-74.0060)
                .locationType("WAREHOUSE")
                .build();
        String depotJson = mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depotReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long depotId = objectMapper.readValue(depotJson, LocationResponse.class).getId();

        LocationRequest hubReq = LocationRequest.builder()
                .name("Midtown Hub")
                .address("50 Central Blvd")
                .latitude(40.7300)
                .longitude(-73.9950)
                .locationType("HUB")
                .build();
        String hubJson = mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hubReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long hubId = objectMapper.readValue(hubJson, LocationResponse.class).getId();

        LocationRequest customerReq = LocationRequest.builder()
                .name("Customer Residence")
                .address("99 Elm Street")
                .latitude(40.7500)
                .longitude(-73.9850)
                .locationType("CUSTOMER")
                .build();
        String custJson = mockMvc.perform(post("/api/locations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long customerId = objectMapper.readValue(custJson, LocationResponse.class).getId();

        // Step 4: Create directed roads/edges (Depot -> Hub, Hub -> Customer)
        LocationEdgeRequest edge1 = LocationEdgeRequest.builder()
                .sourceLocationId(depotId)
                .destinationLocationId(hubId)
                .distance(8.5)
                .estimatedTime(15.0)
                .build();
        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edge1)))
                .andExpect(status().isCreated());

        LocationEdgeRequest edge2 = LocationEdgeRequest.builder()
                .sourceLocationId(hubId)
                .destinationLocationId(customerId)
                .distance(6.2)
                .estimatedTime(12.0)
                .build();
        mockMvc.perform(post("/api/edges")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edge2)))
                .andExpect(status().isCreated());

        // Step 5: Create Driver
        DriverRequest driverReq = DriverRequest.builder()
                .name("Robert Delivery")
                .phone("+15551234567")
                .licenseNumber("DL-NY-883920")
                .status(DriverStatus.AVAILABLE)
                .build();
        String driverJson = mockMvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(driverReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long driverId = objectMapper.readValue(driverJson, DriverResponse.class).getId();

        // Step 6: Create Vehicle
        VehicleRequest vehicleReq = VehicleRequest.builder()
                .registrationNumber("VAN-EXPRESS-01")
                .vehicleType(VehicleType.VAN)
                .capacity(1500.0)
                .status(VehicleStatus.AVAILABLE)
                .build();
        String vehicleJson = mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehicleReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long vehicleId = objectMapper.readValue(vehicleJson, VehicleResponse.class).getId();

        // Step 7: Create Package
        PackageRequest packageReq = PackageRequest.builder()
                .trackingNumber("PKG-2026-X99")
                .senderName("Global Supply Inc")
                .receiverName("Jane Customer")
                .sourceLocationId(depotId)
                .destinationLocationId(customerId)
                .weight(18.5)
                .priority(Priority.HIGH)
                .description("Priority Medical Equipment")
                .build();
        String packageJson = mockMvc.perform(post("/api/packages")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(packageReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();
        Long packageId = objectMapper.readValue(packageJson, PackageResponse.class).getId();

        // Step 8: Create Delivery
        CreateDeliveryRequest createDeliveryReq = CreateDeliveryRequest.builder()
                .packageId(packageId)
                .notes("Handle with extreme care")
                .build();
        String deliveryJson = mockMvc.perform(post("/api/deliveries")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDeliveryReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CREATED")))
                .andReturn().getResponse().getContentAsString();
        Long deliveryId = objectMapper.readValue(deliveryJson, DeliveryResponse.class).getId();

        // Step 9: Assign Driver and Vehicle
        AssignDeliveryRequest assignReq = AssignDeliveryRequest.builder()
                .driverId(driverId)
                .vehicleId(vehicleId)
                .build();
        mockMvc.perform(post("/api/deliveries/" + deliveryId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")));

        // Verify driver & vehicle are marked ON_DELIVERY / IN_USE
        assertThat(driverRepository.findById(driverId).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_DELIVERY);
        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getStatus()).isEqualTo(VehicleStatus.IN_USE);

        // Step 10 & 11: Run Dijkstra optimization and persist Route
        OptimizationRequest optReq = OptimizationRequest.builder()
                .deliveryId(deliveryId)
                .build();
        mockMvc.perform(post("/api/optimization")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(optReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeId", notNullValue()))
                .andExpect(jsonPath("$.versionNumber", is(1)))
                .andExpect(jsonPath("$.totalDistance", is(14.7))) // 8.5 + 6.2
                .andExpect(jsonPath("$.orderedLocationIds", hasSize(3)));

        // Step 12: Start delivery (IN_PROGRESS)
        DeliveryStatusUpdateRequest startReq = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.IN_PROGRESS)
                .reason("Dispatched from Main Depot")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        assertThat(packageRepository.findById(packageId).orElseThrow().getStatus()).isEqualTo(PackageStatus.IN_DELIVERY);

        // Step 13: Complete delivery (COMPLETED)
        DeliveryStatusUpdateRequest completeReq = DeliveryStatusUpdateRequest.builder()
                .status(DeliveryStatus.COMPLETED)
                .reason("Successfully delivered to recipient")
                .build();
        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));

        // Step 14: Verify package becomes DELIVERED
        assertThat(packageRepository.findById(packageId).orElseThrow().getStatus()).isEqualTo(PackageStatus.DELIVERED);

        // Step 15: Verify driver is released back to AVAILABLE
        assertThat(driverRepository.findById(driverId).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);

        // Step 16: Verify vehicle is released back to AVAILABLE
        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);

        // Step 17: Verify Dashboard metrics
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPackages", is(1)))
                .andExpect(jsonPath("$.packagesByStatus.DELIVERED", is(1)))
                .andExpect(jsonPath("$.totalDeliveries", is(1)))
                .andExpect(jsonPath("$.deliveriesByStatus.COMPLETED", is(1)))
                .andExpect(jsonPath("$.completedDeliveries", is(1)))
                .andExpect(jsonPath("$.failedDeliveries", is(0)))
                .andExpect(jsonPath("$.availableDrivers", is(1)))
                .andExpect(jsonPath("$.driversOnDelivery", is(0)))
                .andExpect(jsonPath("$.availableVehicles", is(1)))
                .andExpect(jsonPath("$.vehiclesInUse", is(0)))
                .andExpect(jsonPath("$.totalRoutes", is(1)))
                .andExpect(jsonPath("$.totalRouteDistance", is(14.7)));

        // Step 18: Verify Analytics metrics
        mockMvc.perform(get("/api/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packageStats.totalPackages", is(1)))
                .andExpect(jsonPath("$.packageStats.deliveredPackages", is(1)))
                .andExpect(jsonPath("$.deliveryStats.totalDeliveries", is(1)))
                .andExpect(jsonPath("$.deliveryStats.completedDeliveries", is(1)))
                .andExpect(jsonPath("$.deliveryStats.completionRate", is(100.0)))
                .andExpect(jsonPath("$.routeStats.totalRoutes", is(1)))
                .andExpect(jsonPath("$.routeStats.totalDistanceKm", is(14.7)));

        // Step 19: Generate and verify Delivery CSV
        String deliveryCsv = mockMvc.perform(get("/api/reports/deliveries")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(deliveryCsv).contains("delivery_id,package_tracking_number,driver,vehicle,status,created_at,dispatch_time,completion_time");
        assertThat(deliveryCsv).contains(deliveryId.toString());
        assertThat(deliveryCsv).contains("PKG-2026-X99");
        assertThat(deliveryCsv).contains("Robert Delivery");
        assertThat(deliveryCsv).contains("VAN-EXPRESS-01");
        assertThat(deliveryCsv).contains("COMPLETED");

        // Step 20: Generate and verify Route CSV
        String routeCsv = mockMvc.perform(get("/api/reports/routes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(routeCsv).contains("route_id,delivery_id,source,destination,distance_km,algorithm,created_at");
        assertThat(routeCsv).contains(deliveryId.toString());
        assertThat(routeCsv).contains("Main Depot");
        assertThat(routeCsv).contains("Customer Residence");
        assertThat(routeCsv).contains("14.7");
        assertThat(routeCsv).contains("DIJKSTRA");
    }
}
