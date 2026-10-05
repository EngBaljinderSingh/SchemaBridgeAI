package com.schemabridge.controller;

import com.schemabridge.domain.enums.Direction;
import com.schemabridge.dto.SchemaImportRequest;
import com.schemabridge.dto.SchemaResponse;
import com.schemabridge.service.ProjectService;
import com.schemabridge.service.SchemaExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/schemas")
@Tag(name = "Schema Management", description = "Import, validate, and inspect source and target schemas")
public class SchemaController {

    private final ProjectService projectService;
    private final SchemaExtractionService schemaExtractionService;

    public SchemaController(ProjectService projectService, SchemaExtractionService schemaExtractionService) {
        this.projectService = projectService;
        this.schemaExtractionService = schemaExtractionService;
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

    @PostMapping(value = "/source/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a Swagger/OpenAPI (JSON or YAML) or JSON Schema file for the source system")
    public ResponseEntity<SchemaResponse> uploadSourceSchema(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String systemName) {
        SchemaImportRequest request = schemaExtractionService.buildImportRequestFromFile(
                file, Direction.SOURCE_TO_TARGET, systemName);
        SchemaResponse response = projectService.importSchema(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/target/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a Swagger/OpenAPI (JSON or YAML) or JSON Schema file for the target system")
    public ResponseEntity<SchemaResponse> uploadTargetSchema(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String systemName) {
        SchemaImportRequest request = schemaExtractionService.buildImportRequestFromFile(
                file, Direction.TARGET_TO_SOURCE, systemName);
        SchemaResponse response = projectService.importSchema(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all imported schemas for a project")
    public ResponseEntity<List<SchemaResponse>> getSchemas(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getSchemasByProject(projectId));
    }

    @PostMapping("/trace")
    @Operation(summary = "Trace a live HTTP payload or sample JSON from a system without Swagger and auto-infer its schema")
    public ResponseEntity<SchemaResponse> tracePayload(
            @PathVariable String projectId,
            @Valid @RequestBody com.schemabridge.dto.TracePayloadRequest request) {
        Direction dir = request.getDirection() != null ? request.getDirection() : Direction.SOURCE_TO_TARGET;
        SchemaImportRequest importReq = schemaExtractionService.buildImportRequestFromTrace(
                request.getPayload(), dir, request.getSystemName(), request.getEndpointPath());
        SchemaResponse response = projectService.importSchema(projectId, importReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/parse-endpoints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Parse and list all API endpoints from an uploaded Swagger/OpenAPI file")
    public ResponseEntity<List<com.schemabridge.dto.ApiEndpointSummaryDto>> parseEndpointsFromFile(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file) {
        try {
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok(schemaExtractionService.extractEndpointsFromSpec(content));
        } catch (Exception e) {
            throw new com.schemabridge.exception.ValidationException("Unable to parse endpoints from file: " + e.getMessage());
        }
    }

    @PostMapping("/parse-endpoints/raw")
    @Operation(summary = "Parse and list all API endpoints from raw OpenAPI content")
    public ResponseEntity<List<com.schemabridge.dto.ApiEndpointSummaryDto>> parseEndpointsFromRaw(
            @PathVariable String projectId,
            @RequestBody String rawContent) {
        return ResponseEntity.ok(schemaExtractionService.extractEndpointsFromSpec(rawContent));
    }

    @PostMapping("/import-selected")
    @Operation(summary = "Import schema filtered strictly for the selected API endpoint paths")
    public ResponseEntity<SchemaResponse> importSelectedEndpoints(
            @PathVariable String projectId,
            @RequestBody com.schemabridge.dto.EndpointSelectionImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                projectService.importSchemaWithEndpoints(projectId, request));
    }
}
