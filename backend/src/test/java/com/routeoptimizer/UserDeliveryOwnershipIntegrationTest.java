package com.routeoptimizer;

import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.entity.Route;
import com.routeoptimizer.entity.User;
import com.routeoptimizer.enums.PackageStatus;
import com.routeoptimizer.enums.Role;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.repository.RouteRepository;
import com.routeoptimizer.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
public class UserDeliveryOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User user1;
    private User user2;
    private Package pkg1;

    @BeforeEach
    public void setup() {
        
        com.routeoptimizer.TestDatabaseGuard.verifyTestDatabase(jdbcTemplate);
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

        user1 = User.builder()
                .username("user1")
                .email("user1@example.com")
                .password(passwordEncoder.encode("pass"))
                .role(Role.USER)
                .build();
        user1.setActive(true);
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .username("user2")
                .email("user2@example.com")
                .password(passwordEncoder.encode("pass"))
                .role(Role.USER)
                .build();
        user2.setActive(true);
        user2 = userRepository.save(user2);

        User admin = User.builder()
                .username("admin")
                .email("admin@example.com")
                .password(passwordEncoder.encode("pass"))
                .role(Role.ADMIN)
                .build();
        admin.setActive(true);
        userRepository.save(admin);

        User driver1 = User.builder()
                .username("driver1")
                .email("driver1@example.com")
                .password(passwordEncoder.encode("pass"))
                .role(Role.DRIVER)
                .build();
        driver1.setActive(true);
        userRepository.save(driver1);

        Location loc1 = Location.builder().name("Loc1").latitude(1.0).longitude(1.0).build();
        loc1.setActive(true);
        loc1 = locationRepository.save(loc1);

        Location loc2 = Location.builder().name("Loc2").latitude(2.0).longitude(2.0).build();
        loc2.setActive(true);
        loc2 = locationRepository.save(loc2);

        pkg1 = Package.builder()
                .trackingNumber("TRK-USER1")
                .customer(user1)
                .senderName("Sender")
                .receiverName("Receiver")
                .weight(5.0)
                .status(PackageStatus.PENDING)
                .sourceLocation(loc1)
                .destinationLocation(loc2)
                .build();
        pkg1.setActive(true);
        pkg1 = packageRepository.save(pkg1);

        com.routeoptimizer.entity.Delivery delivery = com.routeoptimizer.entity.Delivery.builder()
                .pkg(pkg1)
                .sourceLocation(loc1)
                .destinationLocation(loc2)
                .status(com.routeoptimizer.enums.DeliveryStatus.CREATED)
                .build();
        delivery.setActive(true);
        delivery = deliveryRepository.save(delivery);

        Route route = Route.builder()
                .delivery(delivery)
                .sourceLocation(loc1)
                .destinationLocation(loc2)
                .versionNumber(1)
                .isCurrent(true)
                .totalDistance(10.0)
                .estimatedTime(15.0)
                .algorithmUsed("DIJKSTRA")
                .build();
        route.setActive(true);
        routeRepository.save(route);
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testUserCanListOwnDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackingNumber").value("TRK-USER1"));
    }

    @Test
    @WithMockUser(username = "user2", roles = "USER")
    public void testUserReceivesEmptyListWhenNoDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "user2", roles = "USER")
    public void testUserCannotAccessAnotherUsersDelivery() throws Exception {
        Long deliveryId = deliveryRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/deliveries/" + deliveryId).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAdminCanListAllDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "dispatcher", roles = "DISPATCHER")
    public void testDispatcherRoleCannotAccessDeliveries() throws Exception {
        mockMvc.perform(get("/api/deliveries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testCustomerCanAccessOwnCurrentRoute() throws Exception {
        Long deliveryId = deliveryRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/deliveries/" + deliveryId + "/routes/current").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryId").value(deliveryId))
                .andExpect(jsonPath("$.totalDistance").value(10.0));
    }

    @Test
    @WithMockUser(username = "user2", roles = "USER")
    public void testCustomerCannotAccessAnotherCustomerCurrentRoute() throws Exception {
        Long deliveryId = deliveryRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/deliveries/" + deliveryId + "/routes/current").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAdminCanAccessAnyCustomerCurrentRoute() throws Exception {
        Long deliveryId = deliveryRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/deliveries/" + deliveryId + "/routes/current").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryId").value(deliveryId));
    }

    @Test
    @WithMockUser(username = "driver1", roles = "DRIVER")
    public void testUnassignedDriverCannotAccessCurrentRoute() throws Exception {
        Long deliveryId = deliveryRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/deliveries/" + deliveryId + "/routes/current").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
