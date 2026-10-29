package com.routeoptimizer;

import com.routeoptimizer.dto.MultiStopOptimizationRequest;
import com.routeoptimizer.dto.MultiStopOptimizationResponse;
import com.routeoptimizer.entity.*;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.enums.DeliveryStatus;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.enums.StopType;
import com.routeoptimizer.enums.VehicleType;
import com.routeoptimizer.exception.BadRequestException;
import com.routeoptimizer.repository.*;
import com.routeoptimizer.service.RouteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MultiStopRouteServiceIntegrationTest {

    @Autowired
    private RouteService routeService;

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
    private RouteStopRepository routeStopRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Location hub;
    private Location customerA;
    private Location customerB;
    private Vehicle van;

    @BeforeEach
    void setUp() {
        TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);

        // Ensure Admin User exists
        userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("admin")
                        .password("$2a$10$abcdefghijklmnopqrstuv")
                        .email("admin@test.com")
                        .fullName("Test Administrator")
                        .role(Role.ADMIN)
                        .isVerified(true)
                        .isBlocked(false)
                        .build())
        );

        // Locations
        hub = locationRepository.save(Location.builder()
                .name("Test Central Hub")
                .address("Hub Address 1")
                .latitude(12.9716)
                .longitude(77.5946)
                .locationType("HUB")
                .build());

        customerA = locationRepository.save(Location.builder()
                .name("Customer Location A")
                .address("Customer Address A")
                .latitude(12.9800)
                .longitude(77.6000)
                .locationType("CUSTOMER")
                .build());

        customerB = locationRepository.save(Location.builder()
                .name("Customer Location B")
                .address("Customer Address B")
                .latitude(12.9900)
                .longitude(77.6100)
                .locationType("CUSTOMER")
                .build());

        // Directed road edges: Hub <-> A <-> B
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(hub).destinationLocation(customerA).distance(5.0).estimatedTime(10.0).build());
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerA).destinationLocation(hub).distance(5.0).estimatedTime(10.0).build());

        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerA).destinationLocation(customerB).distance(4.0).estimatedTime(8.0).build());
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerB).destinationLocation(customerA).distance(4.0).estimatedTime(8.0).build());

        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(hub).destinationLocation(customerB).distance(8.0).estimatedTime(15.0).build());
        locationEdgeRepository.save(LocationEdge.builder().sourceLocation(customerB).destinationLocation(hub).distance(8.0).estimatedTime(15.0).build());

        // Vehicle with 10.0 kg payload capacity
        van = vehicleRepository.save(Vehicle.builder()
                .registrationNumber("KA-01-VAN-" + System.nanoTime())
                .capacity(10.0)
                .vehicleType(VehicleType.VAN)
                .status(com.routeoptimizer.enums.VehicleStatus.AVAILABLE)
                .build());
    }

    private Delivery createDelivery(String trackingNum, Location dest, double weightKg) {
        Package pkg = packageRepository.save(Package.builder()
                .trackingNumber(trackingNum + "-" + System.nanoTime())
                .senderName("Central Hub Warehouse")
                .receiverName("Customer Receiver")
                .sourceLocation(hub)
                .destinationLocation(dest)
                .weight(weightKg)
                .status(PackageStatus.PENDING)
                .build());

        return deliveryRepository.save(Delivery.builder()
                .pkg(pkg)
                .sourceLocation(hub)
                .destinationLocation(dest)
                .status(DeliveryStatus.CREATED)
                .build());
    }

    @Test
    @DisplayName("12. Vehicle payload capacity validation: throws BadRequestException when packages exceed vehicle capacity")
    void testVehicleCapacityValidationFails() {
        // Delivery 1: 6.0 kg, Delivery 2: 5.0 kg -> Total 11.0 kg > Van capacity (10.0 kg)
        Delivery d1 = createDelivery("PKG-OV-1", customerA, 6.0);
        Delivery d2 = createDelivery("PKG-OV-2", customerB, 5.0);

        long initialRoutes = routeRepository.count();

        MultiStopOptimizationRequest request = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .vehicleId(van.getId())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                routeService.optimizeAndPersistMultiDeliveryRoute(request, "admin")
        );

        assertThat(ex.getMessage()).contains("exceeds vehicle payload capacity");
        assertThat(routeRepository.count()).isEqualTo(initialRoutes);
    }

    @Test
    @DisplayName("13. Successful multi-delivery route persistence and V9 schema association")
    void testMultiDeliveryRoutePersistenceSuccess() {
        // Delivery 1: 3.0 kg, Delivery 2: 4.0 kg -> Total 7.0 kg <= 10.0 kg
        Delivery d1 = createDelivery("PKG-OK-1", customerA, 3.0);
        Delivery d2 = createDelivery("PKG-OK-2", customerB, 4.0);

        OffsetDateTime departure = OffsetDateTime.of(2026, 10, 22, 10, 0, 0, 0, ZoneOffset.UTC);

        MultiStopOptimizationRequest request = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d2.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(true)
                .vehicleId(van.getId())
                .departureTime(departure)
                .build();

        MultiStopOptimizationResponse response = routeService.optimizeAndPersistMultiDeliveryRoute(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.getRouteId()).isNotNull();
        assertThat(response.getReturnToHub()).isTrue();
        assertThat(response.getTotalWeightKg()).isEqualTo(7.0);
        assertThat(response.getVehicleCapacityKg()).isEqualTo(10.0);
        assertThat(response.getCustomerStops()).hasSize(2);

        // Verify Route in DB
        Route persistedRoute = routeRepository.findById(response.getRouteId()).orElseThrow();
        assertThat(persistedRoute.getAlgorithmUsed()).isEqualTo("MULTI_STOP_2OPT");
        assertThat(persistedRoute.getIsCurrent()).isTrue();

        // Verify V9 Deliveries association (deliveries.route_id)
        Delivery reloadedD1 = deliveryRepository.findById(d1.getId()).orElseThrow();
        Delivery reloadedD2 = deliveryRepository.findById(d2.getId()).orElseThrow();
        assertThat(reloadedD1.getRoute()).isNotNull();
        assertThat(reloadedD1.getRoute().getId()).isEqualTo(persistedRoute.getId());
        assertThat(reloadedD2.getRoute()).isNotNull();
        assertThat(reloadedD2.getRoute().getId()).isEqualTo(persistedRoute.getId());

        // Verify V9 RouteStops association (route_stops.delivery_id, customer_stop_number)
        List<RouteStop> stops = routeStopRepository.findByRouteIdOrderByStopOrderAsc(persistedRoute.getId());
        List<RouteStop> customerStops = stops.stream()
                .filter(s -> s.getStopType() == StopType.CUSTOMER_STOP)
                .toList();

        assertThat(customerStops).hasSize(2);
        assertThat(customerStops.get(0).getCustomerStopNumber()).isEqualTo(1);
        assertThat(customerStops.get(0).getDelivery()).isNotNull();
        assertThat(customerStops.get(0).getEstimatedArrivalTime()).isNotNull();

        assertThat(customerStops.get(1).getCustomerStopNumber()).isEqualTo(2);
        assertThat(customerStops.get(1).getDelivery()).isNotNull();
        assertThat(customerStops.get(1).getEstimatedArrivalTime()).isNotNull();

        // Verify Hub return stop
        RouteStop returnStop = stops.get(stops.size() - 1);
        assertThat(returnStop.getStopType()).isEqualTo(StopType.HUB_RETURN);
        assertThat(returnStop.getLocation().getId()).isEqualTo(hub.getId());
    }

    @Test
    @DisplayName("14. Rejects duplicate delivery IDs in multi-stop route request")
    void testDuplicateDeliveryIdsRejected() {
        Delivery d1 = createDelivery("PKG-DUP-1", customerA, 2.0);

        MultiStopOptimizationRequest request = MultiStopOptimizationRequest.builder()
                .hubLocationId(hub.getId())
                .deliveryIds(List.of(d1.getId(), d1.getId()))
                .criterion("SHORTEST_DISTANCE")
                .returnToHub(false)
                .build();

        assertThrows(BadRequestException.class, () ->
                routeService.optimizeAndPersistMultiDeliveryRoute(request, "admin")
        );
    }
}
