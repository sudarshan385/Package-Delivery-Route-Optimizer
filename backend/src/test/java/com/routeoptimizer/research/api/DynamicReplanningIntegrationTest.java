package com.routeoptimizer.research.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.entity.Delivery;
import com.routeoptimizer.entity.Location;
import com.routeoptimizer.entity.LocationEdge;
import com.routeoptimizer.entity.Package;
import com.routeoptimizer.repository.DeliveryRepository;
import com.routeoptimizer.repository.LocationEdgeRepository;
import com.routeoptimizer.repository.LocationRepository;
import com.routeoptimizer.repository.PackageRepository;
import com.routeoptimizer.research.api.dto.DynamicReplanRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.routeoptimizer.enums.DeliveryStatus;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DynamicReplanningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private LocationEdgeRepository locationEdgeRepository;

    private Location l1, l2, l3;
    private Delivery d1, d2;

    @BeforeEach
    void setup() {
        locationEdgeRepository.deleteAll();
        deliveryRepository.deleteAll();
        packageRepository.deleteAll();
        locationRepository.deleteAll();

        l1 = new Location();
        l1.setName("Depot");
        l1.setLatitude(1.0);
        l1.setLongitude(1.0);
        l1 = locationRepository.save(l1);

        l2 = new Location();
        l2.setName("Customer 1");
        l2.setLatitude(2.0);
        l2.setLongitude(2.0);
        l2 = locationRepository.save(l2);

        l3 = new Location();
        l3.setName("Customer 2");
        l3.setLatitude(3.0);
        l3.setLongitude(3.0);
        l3 = locationRepository.save(l3);

        LocationEdge e1 = new LocationEdge();
        e1.setSourceLocation(l1);
        e1.setDestinationLocation(l2);
        e1.setDistance(10.0);
        e1.setEstimatedTime(15.0);
        locationEdgeRepository.save(e1);

        LocationEdge e2 = new LocationEdge();
        e2.setSourceLocation(l2);
        e2.setDestinationLocation(l3);
        e2.setDistance(5.0);
        e2.setEstimatedTime(10.0);
        locationEdgeRepository.save(e2);
        
        LocationEdge e3 = new LocationEdge();
        e3.setSourceLocation(l1);
        e3.setDestinationLocation(l3);
        e3.setDistance(15.0);
        e3.setEstimatedTime(25.0);
        locationEdgeRepository.save(e3);

        Package p1 = new Package();
        p1.setSourceLocation(l1);
        p1.setDestinationLocation(l2);
        p1.setDescription("P1");
        p1.setWeight(1.0);
        p1.setTrackingNumber("TRK1");
        p1.setSenderName("Sender1");
        p1.setSenderPhone("12345");
        p1.setReceiverName("Recv1");
        p1.setReceiverPhone("12345");
        p1 = packageRepository.save(p1);

        Package p2 = new Package();
        p2.setSourceLocation(l1);
        p2.setDestinationLocation(l3);
        p2.setDescription("P2");
        p2.setWeight(2.0);
        p2.setTrackingNumber("TRK2");
        p2.setSenderName("Sender2");
        p2.setSenderPhone("12345");
        p2.setReceiverName("Recv2");
        p2.setReceiverPhone("12345");
        p2 = packageRepository.save(p2);

        d1 = new Delivery();
        d1.setPkg(p1);
        d1.setStatus(DeliveryStatus.CREATED);
        d1 = deliveryRepository.save(d1);

        d2 = new Delivery();
        d2.setPkg(p2);
        d2.setStatus(DeliveryStatus.CREATED);
        d2 = deliveryRepository.save(d2);
    }

    @AfterEach
    void cleanup() {
        locationEdgeRepository.deleteAll();
        deliveryRepository.deleteAll();
        packageRepository.deleteAll();
        locationRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testReplanValidRequest() throws Exception {
        DynamicReplanRequest request = new DynamicReplanRequest();
        request.setDriverLocationId(l1.getId());
        request.setCurrentDeliveryIds(List.of(d1.getId()));
        request.setNewDeliveryId(d2.getId());

        mockMvc.perform(post("/api/experiments/ddqn/replan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible", is(true)))
                .andExpect(jsonPath("$.initialRoute").exists())
                .andExpect(jsonPath("$.revisedRoute").exists())
                .andExpect(jsonPath("$.calculationSource", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testReplanMissingEdges() throws Exception {
        locationEdgeRepository.deleteAll(); // Break the graph

        DynamicReplanRequest request = new DynamicReplanRequest();
        request.setDriverLocationId(l1.getId());
        request.setCurrentDeliveryIds(List.of(d1.getId()));
        request.setNewDeliveryId(d2.getId());

        mockMvc.perform(post("/api/experiments/ddqn/replan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible", is(false)))
                .andExpect(jsonPath("$.rejectionReason", containsString("No feasible routes found")));
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void testReplanForbidden() throws Exception {
        DynamicReplanRequest request = new DynamicReplanRequest();
        request.setDriverLocationId(l1.getId());
        request.setCurrentDeliveryIds(List.of(d1.getId()));
        request.setNewDeliveryId(d2.getId());

        mockMvc.perform(post("/api/experiments/ddqn/replan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
