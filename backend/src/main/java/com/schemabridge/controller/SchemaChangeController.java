package com.schemabridge.controller;

import com.schemabridge.domain.enums.SchemaType;
import com.schemabridge.dto.SchemaChangeAnalysisRequest;
import com.schemabridge.dto.SchemaChangeAnalysisResponse;
import com.schemabridge.service.SchemaChangeDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/schema-change")
@Tag(name = "Schema Change Analysis", description = "Detect schema evolution, breaking changes, and impacted rules")
public class SchemaChangeController {

    private final SchemaChangeDetectionService schemaChangeDetectionService;

    public SchemaChangeController(SchemaChangeDetectionService schemaChangeDetectionService) {
        this.schemaChangeDetectionService = schemaChangeDetectionService;
    }

    @PostMapping("/analyse")
    @Operation(summary = "Analyze a new schema version against previous version for added/removed fields and impacted rules")
    public ResponseEntity<SchemaChangeAnalysisResponse> analyzeSchemaChange(
            @PathVariable String projectId,
            @Valid @RequestBody SchemaChangeAnalysisRequest request) {
        SchemaChangeAnalysisResponse response = schemaChangeDetectionService.analyzeChange(
                request.getSystemDefinitionId(), request.getNewSchemaContent(), SchemaType.JSON_SCHEMA);
        return ResponseEntity.ok(response);
    }
}
