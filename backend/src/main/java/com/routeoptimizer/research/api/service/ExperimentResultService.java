package com.routeoptimizer.research.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeoptimizer.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ExperimentResultService {

    private final ObjectMapper objectMapper;
    private final Path resultFilePath;

    public ExperimentResultService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // The file is located at the backend root directory
        this.resultFilePath = Paths.get("experiment_result.json");
    }

    public JsonNode getLatestExperimentResult() {
        if (!Files.exists(resultFilePath)) {
            throw new ResourceNotFoundException("No DDQN experiment results are currently available.");
        }

        try {
            return objectMapper.readTree(resultFilePath.toFile());
        } catch (Exception e) {
            // Throwing a runtime exception will trigger a 500 Internal Server Error in GlobalExceptionHandler
            // matching the RFC 7807 problem details requirement.
            throw new RuntimeException("Failed to parse experiment result", e);
        }
    }
}
