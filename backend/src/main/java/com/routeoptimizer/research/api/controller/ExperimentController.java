package com.routeoptimizer.research.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.routeoptimizer.research.api.service.ExperimentResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.routeoptimizer.research.api.dto.DynamicReplanRequest;
import com.routeoptimizer.research.api.dto.DynamicReplanResponse;
import com.routeoptimizer.research.api.service.DynamicReplanningService;

@RestController
@RequestMapping("/api/experiments/ddqn")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentResultService experimentResultService;
    private final DynamicReplanningService dynamicReplanningService;

    @GetMapping("/latest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> getLatestExperimentResult() {
        return ResponseEntity.ok(experimentResultService.getLatestExperimentResult());
    }

    @PostMapping("/replan")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DynamicReplanResponse> runDynamicReplanning(@RequestBody DynamicReplanRequest request) {
        return ResponseEntity.ok(dynamicReplanningService.replan(request));
    }
}
