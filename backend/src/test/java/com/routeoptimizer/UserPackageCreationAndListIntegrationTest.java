package com.routeoptimizer;

import com.routeoptimizer.dto.*;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.FulfillmentMode;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.service.GeocodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class UserPackageCreationAndListIntegrationTest {

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
    private GeocodingService geocodingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.routeoptimizer.security.JwtTokenProvider tokenProvider;

    private User userAlice;
    private User userBob;
    private User adminUser;

    private String aliceToken;
    private String bobToken;
    private String adminToken;

    private HttpHeaders aliceHeaders;
    private HttpHeaders bobHeaders;
    private HttpHeaders adminHeaders;

    private Location activeHub;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
        jdbcTemplate.execute("TRUNCATE TABLE deliveries, delivery_status_history, route_edges, route_stops, routes, packages, location_edges, vehicles, drivers, locations, users RESTART IDENTITY CASCADE");

        // 1. Seed Customer Alice (USER)
        userAlice = User.builder()
                .username("alice_customer")
                .password(passwordEncoder.encode("Password123!"))
                .email("alice@example.com")
                .fullName("Alice Customer")
                .role(Role.USER)
                .build();
        userAlice.setActive(true);
        userAlice = userRepository.save(userAlice);

        // 2. Seed Customer Bob (USER)
        userBob = User.builder()
                .username("bob_customer")
                .password(passwordEncoder.encode("Password123!"))
                .email("bob@example.com")
                .fullName("Bob Customer")
                .role(Role.USER)
                .build();
        userBob.setActive(true);
        userBob = userRepository.save(userBob);

        // 3. Seed Admin User
        adminUser = User.builder()
                .username("admin_ops")
                .password(passwordEncoder.encode("AdminPass123!"))
                .email("admin_ops@example.com")
                .fullName("Operations Admin")
                .role(Role.ADMIN)
                .build();
        adminUser.setActive(true);
        adminUser = userRepository.save(adminUser);

        // Tokens generated directly via tokenProvider
        aliceToken = tokenProvider.generateTokenForUsername(userAlice.getUsername());
        aliceHeaders = new HttpHeaders();
        aliceHeaders.setBearerAuth(aliceToken);
        aliceHeaders.setContentType(MediaType.APPLICATION_JSON);

        bobToken = tokenProvider.generateTokenForUsername(userBob.getUsername());
        bobHeaders = new HttpHeaders();
        bobHeaders.setBearerAuth(bobToken);
        bobHeaders.setContentType(MediaType.APPLICATION_JSON);

        adminToken = tokenProvider.generateTokenForUsername(adminUser.getUsername());
        adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        // Seed Active Hub Location
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
                GeocodingService.normalizeAddress("Koramangala 4th Block, Bengaluru"),
                GeocodeResponse.builder()
                        .displayName("Koramangala 4th Block, Bengaluru")
                        .latitude(12.9352)
                        .longitude(77.6245)
                        .build()
        );
    }

    @Test
    @DisplayName("1. USER creates a valid package: persisted in PostgreSQL, customer assigned, status PENDING, no delivery created")
    void testUserCreatesPackageSuccess() {
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Alice Customer")
                .senderPhone("+91 9876543210")
                .receiverName("Priya Sharma")
                .receiverPhone("+91 9876543211")
                .weight(3.2)
                .priority(Priority.HIGH)
                .description("Fragile electronics intake")
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, aliceHeaders),
                PackageResponse.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PackageResponse body = createResp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getId()).isNotNull();
        assertThat(body.getTrackingNumber()).isNotBlank().startsWith("PKG-");
        assertThat(body.getStatus()).isEqualTo(PackageStatus.PENDING);
        assertThat(body.getCustomerId()).isEqualTo(userAlice.getId());
        assertThat(body.getSenderName()).isEqualTo("Alice Customer");
        assertThat(body.getReceiverName()).isEqualTo("Priya Sharma");
        assertThat(body.getWeight()).isEqualTo(3.2);
        assertThat(body.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(body.getSourceLocationName()).isEqualTo(activeHub.getName());
        assertThat(body.getDeliveryLocation()).isEqualTo("100 Feet Rd, Indiranagar");

        // Verify package is persisted in PostgreSQL
        Package dbPkg = packageRepository.findById(body.getId()).orElse(null);
        assertThat(dbPkg).isNotNull();
        assertThat(dbPkg.getCustomer()).isNotNull();
        assertThat(dbPkg.getCustomer().getId()).isEqualTo(userAlice.getId());

        // Verify package creation does NOT create a delivery automatically
        assertThat(deliveryRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("2. List Visibility: USER calls GET /api/packages and sees newly created package immediately")
    void testUserListVisibility() {
        // Alice creates package
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Alice Customer")
                .receiverName("Priya Sharma")
                .weight(2.5)
                .priority(Priority.MEDIUM)
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, aliceHeaders),
                PackageResponse.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long createdPkgId = createResp.getBody().getId();

        // Alice lists packages
        ResponseEntity<TestPageResponse<PackageResponse>> listResp = restTemplate.exchange(
                "/api/packages",
                HttpMethod.GET,
                new HttpEntity<>(aliceHeaders),
                new ParameterizedTypeReference<TestPageResponse<PackageResponse>>() {}
        );

        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        TestPageResponse<PackageResponse> page = listResp.getBody();
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).anyMatch(p -> p.getId().equals(createdPkgId) &&
                p.getTrackingNumber().equals(createResp.getBody().getTrackingNumber()) &&
                p.getStatus() == PackageStatus.PENDING &&
                p.getCustomerId().equals(userAlice.getId()));
    }

    @Test
    @DisplayName("3. Role-based isolation: Bob cannot see Alice's package in list or by ID")
    void testRoleBasedIsolationBetweenUsers() {
        // Alice creates package
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Alice Customer")
                .receiverName("Priya Sharma")
                .weight(2.5)
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, aliceHeaders),
                PackageResponse.class
        );
        Long alicePkgId = createResp.getBody().getId();

        // Bob lists packages -> should NOT see Alice's package
        ResponseEntity<TestPageResponse<PackageResponse>> bobListResp = restTemplate.exchange(
                "/api/packages",
                HttpMethod.GET,
                new HttpEntity<>(bobHeaders),
                new ParameterizedTypeReference<TestPageResponse<PackageResponse>>() {}
        );
        assertThat(bobListResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bobListResp.getBody().getContent()).isEmpty();

        // Bob attempts to get Alice's package by ID -> 404 Not Found
        ResponseEntity<String> bobGetResp = restTemplate.exchange(
                "/api/packages/" + alicePkgId,
                HttpMethod.GET,
                new HttpEntity<>(bobHeaders),
                String.class
        );
        assertThat(bobGetResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("4. ADMIN can see all packages including user-created packages")
    void testAdminCanSeeUserCreatedPackages() {
        // Alice creates package
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Alice Customer")
                .receiverName("Priya Sharma")
                .weight(2.0)
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, aliceHeaders),
                PackageResponse.class
        );
        Long alicePkgId = createResp.getBody().getId();

        // Admin lists packages -> sees Alice's package
        ResponseEntity<TestPageResponse<PackageResponse>> adminListResp = restTemplate.exchange(
                "/api/packages",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<TestPageResponse<PackageResponse>>() {}
        );

        assertThat(adminListResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminListResp.getBody().getContent()).anyMatch(p -> p.getId().equals(alicePkgId));
    }

    @Test
    @DisplayName("5. Package creation with email login identifier correctly assigns customer ownership")
    void testUserCreationWithEmailIdentifier() {
        // Token generated with user's email as subject
        String tokenByEmail = tokenProvider.generateTokenForUsername("alice@example.com");
        HttpHeaders emailHeaders = new HttpHeaders();
        emailHeaders.setBearerAuth(tokenByEmail);
        emailHeaders.setContentType(MediaType.APPLICATION_JSON);

        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("Koramangala 4th Block")
                .senderName("Alice By Email")
                .receiverName("Bob Receiver")
                .weight(1.8)
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, emailHeaders),
                PackageResponse.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody().getCustomerId()).isEqualTo(userAlice.getId());

        // And GET /api/packages with email-authenticated token returns the package
        ResponseEntity<TestPageResponse<PackageResponse>> listResp = restTemplate.exchange(
                "/api/packages",
                HttpMethod.GET,
                new HttpEntity<>(emailHeaders),
                new ParameterizedTypeReference<TestPageResponse<PackageResponse>>() {}
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().getContent()).anyMatch(p -> p.getId().equals(createResp.getBody().getId()));
    }

    @Test
    @DisplayName("6. Security: USER cannot pass different customerId (impersonation rejected with 400)")
    void testUserCannotImpersonateCustomer() {
        PackageRequest request = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Alice Customer")
                .receiverName("Priya Sharma")
                .weight(1.5)
                .customerId(userBob.getId()) // Alice tries to attribute package to Bob
                .build();

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(request, aliceHeaders),
                String.class
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("Customers cannot create packages on behalf of other users.");
    }

    @Test
    @DisplayName("7. ADMIN can explicitly assign customerId during package creation and update")
    void testAdminCanExplicitlyAssignAndModifyCustomerId() {
        // Admin creates package assigned to Alice
        PackageRequest createRequest = PackageRequest.builder()
                .fulfillmentMode(FulfillmentMode.HUB_STAGED)
                .sourceLocationId(activeHub.getId())
                .city("Bengaluru")
                .deliveryLocation("100 Feet Rd, Indiranagar")
                .senderName("Warehouse Admin")
                .receiverName("Alice Customer")
                .weight(4.0)
                .customerId(userAlice.getId())
                .build();

        ResponseEntity<PackageResponse> createResp = restTemplate.postForEntity(
                "/api/packages",
                new HttpEntity<>(createRequest, adminHeaders),
                PackageResponse.class
        );

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResp.getBody()).isNotNull();
        assertThat(createResp.getBody().getCustomerId()).isEqualTo(userAlice.getId());

        Long pkgId = createResp.getBody().getId();

        // Admin updates the package to reassign customer to Bob
        PackageRequest updateRequest = PackageRequest.builder()
                .senderName("Warehouse Admin")
                .receiverName("Bob Customer")
                .weight(4.0)
                .customerId(userBob.getId())
                .build();

        ResponseEntity<PackageResponse> updateResp = restTemplate.exchange(
                "/api/packages/" + pkgId,
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, adminHeaders),
                PackageResponse.class
        );

        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResp.getBody()).isNotNull();
        assertThat(updateResp.getBody().getCustomerId()).isEqualTo(userBob.getId());
    }

    // Helper class for Spring Page deserialization in TestRestTemplate
    public static class TestPageResponse<T> {
        private java.util.List<T> content = new java.util.ArrayList<>();
        private int totalPages;
        private long totalElements;

        public java.util.List<T> getContent() { return content; }
        public void setContent(java.util.List<T> content) { this.content = content; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    }
}
