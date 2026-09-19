package com.schemabridge.controller;

import com.schemabridge.dto.AviatorHealthResponse;
import com.schemabridge.service.aviator.AviatorMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aviator")
@Tag(name = "Aviator ADT Health", description = "Monitor OpenText Aviator ADT integration health and connectivity")
public class AviatorHealthController {

    private final AviatorMappingService aviatorMappingService;

    public AviatorHealthController(AviatorMappingService aviatorMappingService) {
        this.aviatorMappingService = aviatorMappingService;
    }

    @GetMapping("/health")
    @Operation(summary = "Check Aviator ADT integration health and latency")
    public ResponseEntity<AviatorHealthResponse> getHealth() {
        return ResponseEntity.ok(aviatorMappingService.healthCheck());
    }

    @PostMapping("/test-connection")
    @Operation(summary = "Test round-trip connection to Aviator ADT service")
    public ResponseEntity<AviatorHealthResponse> testConnection() {
        return ResponseEntity.ok(aviatorMappingService.healthCheck());
    }
}
