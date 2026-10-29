package com.routeoptimizer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
public class PublicAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testUnauthenticatedAccessToRegisterIsAllowed() throws Exception {
        // Will fail with 400 Bad Request if it reaches the controller and validation fails,
        // which proves Spring Security permitAll() worked.
        // If it returns 401/403, then Spring Security blocked it.
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest()); // reaches controller validation
    }

    @Test
    public void testUnauthenticatedAccessToLoginIsAllowed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest()); // reaches controller validation
    }

    @Test
    public void testUnauthenticatedAccessToSetupStatusIsAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/setup/status"))
                .andExpect(status().isOk());
    }

    @Test
    public void testProtectedEndpointRemainsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }
}
