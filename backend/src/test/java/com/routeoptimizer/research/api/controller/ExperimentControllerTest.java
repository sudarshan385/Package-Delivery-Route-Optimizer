package com.routeoptimizer.research.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")

@SpringBootTest
@AutoConfigureMockMvc
class ExperimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final Path resultFilePath = Paths.get("experiment_result.json");
    private final Path backupFilePath = Paths.get("experiment_result_backup.json");

    @BeforeEach
    void setUp() throws Exception {
        // Backup existing file if any
        if (Files.exists(resultFilePath)) {
            Files.copy(resultFilePath, backupFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Restore from backup
        if (Files.exists(backupFilePath)) {
            Files.copy(backupFilePath, resultFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.delete(backupFilePath);
        } else {
            Files.deleteIfExists(resultFilePath);
        }
    }

    private void writeValidJson() throws Exception {
        String json = """
        {
          "seed": 42,
          "dataset": {
            "totalRoutes": 20,
            "trainingRoutes": 14,
            "validationRoutes": 3,
            "testRoutes": 3,
            "totalStops": 2737,
            "totalPackages": 2737
          },
          "configuration": {
            "trainingEpisodes": 2,
            "maxSteps": 10,
            "learningRate": 0.001,
            "discountFactor": 0.99,
            "epsilonStart": 1.0,
            "epsilonMinimum": 0.01,
            "epsilonDecay": 0.995,
            "batchSize": 32,
            "minimumReplaySize": 10,
            "targetUpdateInterval": 10
          },
          "training": {
            "episodes": 2,
            "updates": 8419,
            "finalEpsilon": 0.01,
            "averageReward": 0.0,
            "bestReward": 0.0,
            "averageEpisodeSteps": 10.0
          },
          "test": {
            "routes": 3,
            "cmr": 0.0084,
            "ed": 0.0084,
            "averageReward": 0.8408
          },
          "dijkstra": {
            "available": false,
            "reason": "The Amazon Last Mile dataset used for this experiment does not provide an authoritative road-network graph or road-travel-time weights required for a legitimate Dijkstra comparison."
          }
        }
        """;
        Files.writeString(resultFilePath, json);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLatestExperimentResult_Success() throws Exception {
        writeValidJson();

        mockMvc.perform(get("/api/experiments/ddqn/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed", is(42)))
                .andExpect(jsonPath("$.dataset.totalRoutes", is(20)))
                .andExpect(jsonPath("$.dataset.trainingRoutes", is(14)))
                .andExpect(jsonPath("$.test.cmr", is(0.0084)))
                .andExpect(jsonPath("$.test.ed", is(0.0084)))
                .andExpect(jsonPath("$.training.updates", is(8419)))
                .andExpect(jsonPath("$.dijkstra.available", is(false)))
                .andExpect(jsonPath("$.dijkstra.reason", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLatestExperimentResult_NotFound() throws Exception {
        Files.deleteIfExists(resultFilePath);

        mockMvc.perform(get("/api/experiments/ddqn/latest"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Resource Not Found")))
                .andExpect(jsonPath("$.detail", is("No DDQN experiment results are currently available.")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:resource-not-found")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLatestExperimentResult_MalformedJson() throws Exception {
        Files.writeString(resultFilePath, "{ malformed: true ");

        mockMvc.perform(get("/api/experiments/ddqn/latest"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title", is("Internal Server Error")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:internal-server-error")));
    }

    @Test
    void testGetLatestExperimentResult_Unauthorized() throws Exception {
        writeValidJson();

        // No @WithMockUser, so it should be rejected
        mockMvc.perform(get("/api/experiments/ddqn/latest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DRIVER") // Driver doesn't have ADMIN role
    void testGetLatestExperimentResult_Forbidden() throws Exception {
        writeValidJson();

        mockMvc.perform(get("/api/experiments/ddqn/latest"))
                .andExpect(status().isForbidden());
    }
}
