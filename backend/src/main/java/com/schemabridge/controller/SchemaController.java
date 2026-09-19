package com.schemabridge.controller;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.dto.SchemaImportRequest;
import com.schemabridge.dto.SchemaResponse;
import com.schemabridge.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/schemas")
@Tag(name = "Schema Management", description = "Import, validate, and inspect source and target schemas")
public class SchemaController {

    private final ProjectService projectService;

    public SchemaController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/source")
    @Operation(summary = "Import source schema or sample payload")
    public ResponseEntity<SchemaResponse> importSourceSchema(
            @PathVariable String projectId,
            @Valid @RequestBody SchemaImportRequest request) {
        request.setDirection(Direction.SOURCE_TO_TARGET);
        SchemaResponse response = projectService.importSchema(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/target")
    @Operation(summary = "Import target schema or sample payload")
    public ResponseEntity<SchemaResponse> importTargetSchema(
            @PathVariable String projectId,
            @Valid @RequestBody SchemaImportRequest request) {
        request.setDirection(Direction.TARGET_TO_SOURCE);
        SchemaResponse response = projectService.importSchema(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all imported schemas for a project")
    public ResponseEntity<List<SchemaResponse>> getSchemas(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getSchemasByProject(projectId));
    }
}
