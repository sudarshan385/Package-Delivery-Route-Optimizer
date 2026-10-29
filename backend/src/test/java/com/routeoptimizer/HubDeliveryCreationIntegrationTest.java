package com.routeoptimizer;

import com.routeoptimizer.dto.CreateDeliveryRequest;
import com.routeoptimizer.dto.DeliveryResponse;
import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Priority;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.exception.ResourceNotFoundException;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.repository.UserRepository;
import com.routeoptimizer.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class HubDeliveryCreationIntegrationTest {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location centralHub;
    private Location secondaryHub;
    private Location customerPickup;
    private Location customerDestination;
    private User adminUser;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        long timestamp = System.nanoTime();

        adminUser = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("admin")
                        .email("admin@test.com")
                        .password("$2a$10$abcdefghijklmnopqrstuv")
                        .fullName("System Admin")
                        .role(Role.ADMIN)
                        .isVerified(true)
                        .isBlocked(false)
                        .build())
        );

        centralHub = locationRepository.save(Location.builder()
                .name("Main Central Hub " + timestamp)
                .locationType("HUB")
                .address("100 MG Road Central")
                .city("Bengaluru")
                .latitude(12.9716)
                .longitude(77.5946)
                .build());

        secondaryHub = locationRepository.save(Location.builder()
                .name("North Regional Hub " + timestamp)
                .locationType("HUB")
                .address("200 Hebbal Hub")
                .city("Bengaluru")
                .latitude(13.0358)
                .longitude(77.5970)
                .build());

        customerPickup = locationRepository.save(Location.builder()
                .name("Customer Residence Pickup " + timestamp)
                .locationType("CUSTOMER")
                .address("12 5th Cross Indiranagar")
                .city("Bengaluru")
                .latitude(12.9784)
                .longitude(77.6408)
                .build());

        customerDestination = locationRepository.save(Location.builder()
                .name("Customer Office Destination " + timestamp)
                .locationType("CUSTOMER")
                .address("45 Outer Ring Road Bellandur")
                .city("Bengaluru")
                .latitude(12.9352)
                .longitude(77.6772)
                .build());
    }

    private Package createSamplePackage(String trackingSuffix) {
        return packageRepository.save(Package.builder()
                .trackingNumber("PKG-TEST-" + System.nanoTime() + "-" + trackingSuffix)
                .senderName("Alice Sender")
                .receiverName("Bob Receiver")
                .sourceLocation(customerPickup)
                .destinationLocation(customerDestination)
                .weight(4.5)
                .priority(Priority.MEDIUM)
                .status(PackageStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("Delivery creation with explicit central hub associates hub as source and preserves customer destination")
    void testCreateDeliveryWithExplicitCentralHubSource_AssociatesHubAndPreservesCustomerDestination() {
        Package pkg = createSamplePackage("HUB-EXPLICIT");

        CreateDeliveryRequest request = CreateDeliveryRequest.builder()
                .packageId(pkg.getId())
                .sourceLocationId(centralHub.getId())
                .notes("Dispatched via Main Central Hub")
                .build();

        DeliveryResponse response = deliveryService.createDelivery(request, adminUser.getUsername());

        assertThat(response).isNotNull();
        assertThat(response.getSourceLocationId()).isEqualTo(centralHub.getId());
        assertThat(response.getSourceLocationName()).isEqualTo(centralHub.getName());
        assertThat(response.getDestinationLocationId()).isEqualTo(customerDestination.getId());
        assertThat(response.getDestinationLocationName()).isEqualTo(customerDestination.getName());
        assertThat(response.getStatus()).isEqualTo(DeliveryStatus.CREATED);

        // Verify persisted entity in database
        Delivery persisted = deliveryRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getSourceLocation().getId()).isEqualTo(centralHub.getId());
        assertThat(persisted.getDestinationLocation().getId()).isEqualTo(customerDestination.getId());
        assertThat(persisted.getPackage().getId()).isEqualTo(pkg.getId());
    }

    @Test
    @DisplayName("Delivery creation without explicit source location backward-compatibly falls back to package source")
    void testCreateDeliveryWithoutExplicitSourceLocation_FallsBackToPackageSourceLocation() {
        Package pkg = createSamplePackage("FALLBACK-DEFAULT");

        CreateDeliveryRequest request = CreateDeliveryRequest.builder()
                .packageId(pkg.getId())
                .notes("Legacy point-to-point dispatch")
                .build();

        DeliveryResponse response = deliveryService.createDelivery(request, adminUser.getUsername());

        assertThat(response).isNotNull();
        assertThat(response.getSourceLocationId()).isEqualTo(customerPickup.getId());
        assertThat(response.getSourceLocationName()).isEqualTo(customerPickup.getName());
        assertThat(response.getDestinationLocationId()).isEqualTo(customerDestination.getId());
        assertThat(response.getStatus()).isEqualTo(DeliveryStatus.CREATED);
    }

    @Test
    @DisplayName("Delivery creation rejects inactive source location with BadRequestException")
    void testCreateDeliveryWithInactiveSourceLocation_ThrowsBadRequestException() {
        Location inactiveHub = Location.builder()
                .name("Decommissioned Hub " + System.nanoTime())
                .locationType("HUB")
                .address("Decommissioned St")
                .city("Bengaluru")
                .latitude(12.9500)
                .longitude(77.5800)
                .build();
        inactiveHub.setActive(false);
        inactiveHub = locationRepository.save(inactiveHub);

        Package pkg = createSamplePackage("INACTIVE-HUB");

        CreateDeliveryRequest request = CreateDeliveryRequest.builder()
                .packageId(pkg.getId())
                .sourceLocationId(inactiveHub.getId())
                .build();

        assertThrows(BadRequestException.class, () ->
                deliveryService.createDelivery(request, adminUser.getUsername())
        );
    }

    @Test
    @DisplayName("Delivery creation rejects non-existent source location with ResourceNotFoundException")
    void testCreateDeliveryWithNonExistentSourceLocation_ThrowsResourceNotFoundException() {
        Package pkg = createSamplePackage("NONEXISTENT-HUB");

        CreateDeliveryRequest request = CreateDeliveryRequest.builder()
                .packageId(pkg.getId())
                .sourceLocationId(999999L)
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                deliveryService.createDelivery(request, adminUser.getUsername())
        );
    }

    @Test
    @DisplayName("Unassigned deliveries correctly filter by associated Central Hub")
    void testUnassignedDeliveriesByHub_FiltersCorrectlyWhenExplicitHubAssigned() {
        Package pkg1 = createSamplePackage("HUB-DEL-1");
        Package pkg2 = createSamplePackage("HUB-DEL-2");
        Package pkg3 = createSamplePackage("OTHER-HUB-DEL-3");

        // Create 2 deliveries assigned to centralHub
        deliveryService.createDelivery(CreateDeliveryRequest.builder()
                .packageId(pkg1.getId())
                .sourceLocationId(centralHub.getId())
                .build(), adminUser.getUsername());

        deliveryService.createDelivery(CreateDeliveryRequest.builder()
                .packageId(pkg2.getId())
                .sourceLocationId(centralHub.getId())
                .build(), adminUser.getUsername());

        // Create 1 delivery assigned to secondaryHub
        deliveryService.createDelivery(CreateDeliveryRequest.builder()
                .packageId(pkg3.getId())
                .sourceLocationId(secondaryHub.getId())
                .build(), adminUser.getUsername());

        List<DeliveryResponse> centralHubUnassigned = deliveryService.getUnassignedDeliveriesByHub(centralHub.getId());
        List<DeliveryResponse> secondaryHubUnassigned = deliveryService.getUnassignedDeliveriesByHub(secondaryHub.getId());

        assertThat(centralHubUnassigned).hasSize(2);
        assertThat(centralHubUnassigned).extracting(DeliveryResponse::getSourceLocationId)
                .containsOnly(centralHub.getId());
        assertThat(centralHubUnassigned).extracting(DeliveryResponse::getDestinationLocationId)
                .containsOnly(customerDestination.getId());

        assertThat(secondaryHubUnassigned).hasSize(1);
        assertThat(secondaryHubUnassigned.get(0).getSourceLocationId()).isEqualTo(secondaryHub.getId());
    }
}
