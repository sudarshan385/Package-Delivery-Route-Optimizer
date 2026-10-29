package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.FulfillmentMode;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.exception.ConflictException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.service.DeliveryService;
import com.routeoptimizer.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class HubStagedPackageCreationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private GeocodingService geocodingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private HttpHeaders adminHeaders;
    private Location activeHub;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");

        // Seed Admin user
        User admin = User.builder()
                .username("admin_hub_test")
                .password(passwordEncoder.encode("SecretPass123"))
                .email("admin_hub_test@example.com")
                .fullName("Central Hub Admin")
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        userRepository.save(admin);

        LoginRequest loginRequest = new LoginRequest("admin_hub_test", "SecretPass123");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity("/api/auth/login", loginRequest, AuthResponse.class);
        adminToken = loginResp.getBody().getAccessToken();

        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Seed Active Central Hub
        activeHub = Location.builder()
                .name("Bengaluru Central Distribution Hub")
                .city("Bengaluru")
                .address("MG Road Distribution Yard")
                .latitude(12.9716)
                .longitude(77.5946)
                .locationType("HUB")
                .build();
        activeHub.setActive(true);
        activeHub.setVersion(0L);
        activeHub = locationRepository.save(activeHub);

        // Preload geocoding responses in test cache for reliable hermetic execution
        geocodingService.putCacheForTesting(
                GeocodingService.normalizeAddress("100 Feet Rd, Indiranagar, Bengaluru"),
                GeocodeResponse.builder()
                        .displayName("100 Feet Rd, Indiranagar, Bengaluru")
                        .latitude(12.9784)
                        .longitude(77.6408)
                        .build()
        );

        geocodingService.putCacheForTesting(
                GeocodingService.normalizeAddress("Whitefield Main Rd, Bengaluru"),
                GeocodeResponse.builder()
                        .displayName("Whitefield Main Rd, Bengaluru")
                        .latitude(12.9698)
                        .longitude(77.7499)
                        .build()
        );

        geocodingService.putCacheForTesting(
                GeocodingService.normalizeAddress("Koramangala 4th Block, Bengaluru"),
                GeocodeResponse.builder()
                        .displayName("Koramangala 4th Block, Bengaluru")
                        .latitude(12.9352)
                        .longitude(77.6245)
                        .build()
        );
    }

    @Test
    @DisplayName("1. HUB_STAGED: Package staged at Central Hub correctly sets sourceLocation to hub and geocodes customer destination without creating fake pickup")
    void testHubStagedPackageCreationSuccess() {
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Hub Staging Operations")
                .senderPhone("+91 9876543210")
                .receiverName("Priya Sharma")
                .receiverPhone("+91 9876543211")
                .weight(4.5)
                .build();

        ResponseEntity<PackageResponse> response = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                PackageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PackageResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getSourceLocationId()).isEqualTo(activeHub.getId());
        assertThat(body.getSourceLocationName()).isEqualTo(activeHub.getName());
        assertThat(body.getFulfillmentMode()).isEqualTo(FulfillmentMode.HUB_STAGED);
        assertThat(body.getStatus()).isEqualTo(PackageStatus.PENDING);
        assertThat(body.getSenderPhone()).isEqualTo("+91 9876543210");
        assertThat(body.getReceiverPhone()).isEqualTo("+91 9876543211");
        assertThat(body.getDeliveryLocation()).isEqualTo("100 Feet Rd, Indiranagar");

        // Verify in Database: No fake "-SRC" customer location was created
        long fakeSrcLocationsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locations WHERE name LIKE '%-SRC%'",
                Long.class
        );
        assertThat(fakeSrcLocationsCount).isZero();

        // Verify exactly one destination location was created
        long dstLocationsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locations WHERE name LIKE '%-DST%'",
                Long.class
        );
        assertThat(dstLocationsCount).isEqualTo(1L);

        // Verify Package entity in DB links directly to activeHub
        Package savedPkg = packageRepository.findById(body.getId()).orElseThrow();
        assertThat(savedPkg.getSourceLocation().getId()).isEqualTo(activeHub.getId());
        assertThat(savedPkg.getDestinationLocation().getId()).isEqualTo(body.getDestinationLocationId());
    }

    @Test
    @DisplayName("2. Destination Geocoding Failure: Invalid address prevents submission and creates NO synthetic coordinates or DB records")
    void testGeocodingFailureOnInvalidDestinationBlocksSubmission() {
        long initialPackageCount = packageRepository.count();
        long initialLocationCount = locationRepository.count();

        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("UNKNOWN_NONEXISTENT_STREET_NOT_GEOCODABLE_XYZ")
                .senderName("Hub Staging Operations")
                .receiverName("Invalid Receiver")
                .weight(2.0)
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Failed to geocode delivery location");

        // Verify NO package was created
        assertThat(packageRepository.count()).isEqualTo(initialPackageCount);

        // Verify NO invalid location record was created
        assertThat(locationRepository.count()).isEqualTo(initialLocationCount);
    }

    @Test
    @DisplayName("3. CUSTOMER_PICKUP: Legacy customer-pickup workflow remains 100% intact and functional")
    void testPreserveLegacyCustomerPickupWorkflow() {
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.CUSTOMER_PICKUP)
                .city("Bengaluru")
                .pickupLocation("Whitefield Main Rd")
                .deliveryLocation("Koramangala 4th Block")
                .senderName("Customer Ramesh")
                .receiverName("Customer Suresh")
                .weight(2.5)
                .build();

        ResponseEntity<PackageResponse> response = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                PackageResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PackageResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getFulfillmentMode()).isEqualTo(FulfillmentMode.CUSTOMER_PICKUP);
        assertThat(body.getSourceLocationId()).isNotNull();
        assertThat(body.getDestinationLocationId()).isNotNull();

        // In CUSTOMER_PICKUP mode, both -SRC and -DST locations are created
        Location src = locationRepository.findById(body.getSourceLocationId()).orElseThrow();
        Location dst = locationRepository.findById(body.getDestinationLocationId()).orElseThrow();

        assertThat(src.getLocationType()).isEqualTo("CUSTOMER");
        assertThat(dst.getLocationType()).isEqualTo("CUSTOMER");
        assertThat(src.getName()).endsWith("-SRC");
        assertThat(dst.getName()).endsWith("-DST");
    }

    @Test
    @DisplayName("4. Duplicate Delivery Prevention: Server-side validation blocks duplicate active deliveries for the same package")
    void testPreventDuplicatePackageDeliveryCreation() {
        // First create a hub-staged package
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Sender A")
                .receiverName("Receiver B")
                .weight(3.0)
                .build();

        ResponseEntity<PackageResponse> pkgResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                PackageResponse.class
        );
        Long packageId = pkgResp.getBody().getId();

        // Create first delivery
        CreateDeliveryRequest delReq1 = CreateDeliveryRequest.builder()
                .packageId(packageId)
                .notes("First delivery")
                .build();

        DeliveryResponse delivery1 = deliveryService.createDelivery(delReq1, "admin_hub_test");
        assertThat(delivery1).isNotNull();
        assertThat(delivery1.getSourceLocationId()).isEqualTo(activeHub.getId());

        // Attempt second delivery creation for the same package: Must throw ConflictException
        CreateDeliveryRequest delReq2 = CreateDeliveryRequest.builder()
                .packageId(packageId)
                .notes("Duplicate delivery attempt")
                .build();

        ConflictException conflict = assertThrows(ConflictException.class, () ->
                deliveryService.createDelivery(delReq2, "admin_hub_test")
        );
        assertThat(conflict.getMessage()).contains("already has an active delivery");
    }

    @Test
    @DisplayName("5. Inactive Hub Selection: HUB_STAGED package rejects inactive hub location with 400 Bad Request")
    void testHubStagedFailsWithInactiveHub() {
        Location inactiveHub = Location.builder()
                .name("Decommissioned Hub")
                .city("Bengaluru")
                .address("Old Industrial Area")
                .latitude(12.9000)
                .longitude(77.5000)
                .locationType("HUB")
                .build();
        inactiveHub.setActive(false);
        inactiveHub.setVersion(0L);
        inactiveHub = locationRepository.save(inactiveHub);

        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(inactiveHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Sender")
                .receiverName("Receiver")
                .weight(1.0)
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("source hub location is inactive");
    }

    @Test
    @DisplayName("6. Phone Validation: Malformed phone numbers are rejected with 400 Bad Request")
    void testInvalidPhoneNumberRejected() {
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Sender")
                .senderPhone("NOT_A_PHONE_@@@")
                .receiverName("Receiver")
                .weight(1.0)
                .build();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, adminHeaders),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Sender phone contains invalid characters");
    }
}
