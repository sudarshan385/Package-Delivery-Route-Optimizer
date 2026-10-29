package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import com.routeoptimizer.entity.User;
import com.routeoptimizer.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AdminApprovalWorkflowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String driverToken;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");

        // Create Admin
        User admin = User.builder()
                .username("sudarshan")
                .password(passwordEncoder.encode("password123"))
                .email("admin@example.com")
                .fullName("Admin")
                .role(Role.ADMIN)
                .isVerified(true)
                .isBlocked(false)
                .build();
        admin.setActive(true);
        userRepository.save(admin);
        
        LoginRequest adminLogin = new LoginRequest("sudarshan", "password123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity("/api/auth/login", adminLogin, AuthResponse.class);
        adminToken = loginResp.getBody().getAccessToken();

        LoginRequest driverLogin = new LoginRequest("shyam", "password123");
        ResponseEntity<AuthResponse> driverLoginResp = restTemplate.postForEntity("/api/auth/login", driverLogin, AuthResponse.class);
        if (driverLoginResp.getStatusCode() == HttpStatus.OK) {
            driverToken = driverLoginResp.getBody().getAccessToken();
        } else {
            RegisterRequest register = new RegisterRequest();
            register.setUsername("shyam");
            register.setPassword("password123");
            register.setEmail("shyam@example.com");
            register.setFullName("Driver Shyam");
            register.setRole(Role.DRIVER);
            register.setLicenseNumber("DL-SHYAM-1234");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(register, headers), AuthResponse.class);
            
            driverToken = restTemplate.postForEntity("/api/auth/login", driverLogin, AuthResponse.class).getBody().getAccessToken();
        }
    }

    @Test
    void testGeocodingAndAdminApprovalWorkflow() {
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        
        HttpHeaders driverHeaders = new HttpHeaders();
        driverHeaders.setBearerAuth(driverToken);

        ResponseEntity<GeocodeResponse> geoResp = restTemplate.exchange("/api/locations/geocode?address=Bengaluru", HttpMethod.GET, new HttpEntity<>(adminHeaders), GeocodeResponse.class);
        assertThat(geoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(geoResp.getBody().getLatitude()).isNotNull();

        LocationRequest hubReq = new LocationRequest();
        hubReq.setName("Central Hub " + System.currentTimeMillis());
        hubReq.setCity("Bengaluru");
        hubReq.setAddress("MG Road");
        hubReq.setLatitude(12.9716);
        hubReq.setLongitude(77.5946);
        hubReq.setLocationType("HUB");
        ResponseEntity<LocationResponse> hubResp = restTemplate.postForEntity("/api/locations", new HttpEntity<>(hubReq, adminHeaders), LocationResponse.class);
        assertThat(hubResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long hubId = hubResp.getBody().getId();

        PackageRequest pkgReq = new PackageRequest();
        pkgReq.setCity("Bengaluru");
        pkgReq.setPickupLocation("Whitefield");
        pkgReq.setDeliveryLocation("Marathahalli");
        pkgReq.setSenderName("Sender");
        pkgReq.setReceiverName("Receiver");
        pkgReq.setWeight(5.0);
        ResponseEntity<PackageResponse> pkgResp = restTemplate.postForEntity("/api/packages", new HttpEntity<>(pkgReq, adminHeaders), PackageResponse.class);
        assertThat(pkgResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long pkgId = pkgResp.getBody().getId();
        Long pkgSrcId = pkgResp.getBody().getSourceLocationId();
        Long pkgDstId = pkgResp.getBody().getDestinationLocationId();

        ApproveLocationRequest appReq = new ApproveLocationRequest();
        appReq.setConnectToLocationId(hubId);
        appReq.setCreateIncomingEdge(true);
        appReq.setCreateOutgoingEdge(false);
        appReq.setIncomingDistance(5.0);
        
        ResponseEntity<String> appFailResp = restTemplate.postForEntity("/api/locations/" + pkgSrcId + "/approve", new HttpEntity<>(appReq, driverHeaders), String.class);
        assertThat(appFailResp.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);

        appReq.setIncomingDistance(0.0);
        ResponseEntity<String> appBadResp = restTemplate.postForEntity("/api/locations/" + pkgSrcId + "/approve", new HttpEntity<>(appReq, adminHeaders), String.class);
        assertThat(appBadResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<LocationResponse> checkLoc = restTemplate.exchange("/api/locations/" + pkgSrcId, HttpMethod.GET, new HttpEntity<>(adminHeaders), LocationResponse.class);
        assertThat(checkLoc.getBody().getActive()).isFalse();

        appReq.setIncomingDistance(5.0);
        appReq.setCreateOutgoingEdge(true);
        appReq.setOutgoingDistance(5.0);
        String testIncomingGeom = "{\"type\":\"LineString\",\"coordinates\":[[77.59,12.97],[77.60,12.98]]}";
        String testOutgoingGeom = "{\"type\":\"LineString\",\"coordinates\":[[77.60,12.98],[77.59,12.97]]}";
        appReq.setIncomingGeometryGeojson(testIncomingGeom);
        appReq.setOutgoingGeometryGeojson(testOutgoingGeom);
        ResponseEntity<LocationResponse> appSucc = restTemplate.postForEntity("/api/locations/" + pkgSrcId + "/approve", new HttpEntity<>(appReq, adminHeaders), LocationResponse.class);
        assertThat(appSucc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(appSucc.getBody().getActive()).isTrue();

        // Verify incoming and outgoing edges both persisted geometry in database
        String inGeomDb = jdbcTemplate.queryForObject(
                "SELECT geometry_geojson FROM location_edges WHERE source_location_id = ? AND destination_location_id = ?",
                String.class, hubId, pkgSrcId
        );
        assertThat(inGeomDb).isEqualTo(testIncomingGeom);

        String outGeomDb = jdbcTemplate.queryForObject(
                "SELECT geometry_geojson FROM location_edges WHERE source_location_id = ? AND destination_location_id = ?",
                String.class, pkgSrcId, hubId
        );
        assertThat(outGeomDb).isEqualTo(testOutgoingGeom);

        ApproveLocationRequest appDestReq = new ApproveLocationRequest();
        appDestReq.setConnectToLocationId(hubId);
        appDestReq.setCreateIncomingEdge(true);
        appDestReq.setCreateOutgoingEdge(false);
        appDestReq.setIncomingDistance(4.0);
        ResponseEntity<LocationResponse> appDestSucc = restTemplate.postForEntity("/api/locations/" + pkgDstId + "/approve", new HttpEntity<>(appDestReq, adminHeaders), LocationResponse.class);
        assertThat(appDestSucc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(appDestSucc.getBody().getActive()).isTrue();

        OptimizationRequest optReq = new OptimizationRequest();
        optReq.setStartLocationId(pkgSrcId);
        optReq.setDestinationLocationId(pkgDstId);
        optReq.setCriterion("SHORTEST_DISTANCE");
        
        ResponseEntity<OptimizationResponse> optResp = restTemplate.postForEntity("/api/optimization", new HttpEntity<>(optReq, adminHeaders), OptimizationResponse.class);
        assertThat(optResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(optResp.getBody().isPathExists()).isTrue();
        
        ResponseEntity<PackageResponse> pkgCheck = restTemplate.exchange("/api/packages/" + pkgId, HttpMethod.GET, new HttpEntity<>(adminHeaders), PackageResponse.class);
        assertThat(pkgCheck.getBody().getSourceLocationId()).isEqualTo(pkgSrcId);
        assertThat(pkgCheck.getBody().getDestinationLocationId()).isEqualTo(pkgDstId);
    }
}
