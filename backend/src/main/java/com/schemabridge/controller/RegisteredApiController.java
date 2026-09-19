package com.schemabridge.controller;

import com.schemabridge.dto.ApiCreateRequest;
import com.schemabridge.dto.ApiUpdateRequest;
import com.schemabridge.dto.MappingRuleDto;
import com.schemabridge.dto.RegisteredApiDto;
import com.schemabridge.service.RegisteredApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/apis")
@Tag(name = "API Registry", description = "Register and manage multiple individual APIs, endpoints, and schema details under an Integration App")
public class RegisteredApiController {

    private final RegisteredApiService apiService;

    public RegisteredApiController(RegisteredApiService apiService) {
        this.apiService = apiService;
    }

    @GetMapping
    @Operation(summary = "List all registered APIs under this integration app")
    public ResponseEntity<List<RegisteredApiDto>> getApis(@PathVariable String projectId) {
        return ResponseEntity.ok(apiService.getApis(projectId));
    }

    @PostMapping
    @Operation(summary = "Register a new API endpoint under this app with method, path, and schemas")
    public ResponseEntity<RegisteredApiDto> createApi(
            @PathVariable String projectId,
            @Valid @RequestBody ApiCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(apiService.createApi(projectId, request));
    }

    @GetMapping("/{apiId}")
    @Operation(summary = "Get registered API details by ID")
    public ResponseEntity<RegisteredApiDto> getApiById(
            @PathVariable String projectId,
            @PathVariable String apiId) {
        return ResponseEntity.ok(apiService.getApiById(projectId, apiId));
    }

    @PutMapping("/{apiId}")
    @Operation(summary = "Update registered API endpoint details and schemas")
    public ResponseEntity<RegisteredApiDto> updateApi(
            @PathVariable String projectId,
            @PathVariable String apiId,
            @Valid @RequestBody ApiUpdateRequest request) {
        return ResponseEntity.ok(apiService.updateApi(projectId, apiId, request));
    }

    @DeleteMapping("/{apiId}")
    @Operation(summary = "Delete registered API from the app")
    public ResponseEntity<Void> deleteApi(
            @PathVariable String projectId,
            @PathVariable String apiId) {
        apiService.deleteApi(projectId, apiId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{apiId}/mappings/generate")
    @Operation(summary = "Generate 5-level mapping suggestions for this specific registered API")
    public ResponseEntity<List<MappingRuleDto>> generateMappings(
            @PathVariable String projectId,
            @PathVariable String apiId) {
        return ResponseEntity.ok(apiService.generateMappingsForApi(projectId, apiId));
    }

    @PostMapping("/{apiId}/transform")
    @Operation(summary = "Execute deterministic payload transformation for this registered API")
    public ResponseEntity<Map<String, Object>> executeTransform(
            @PathVariable String projectId,
            @PathVariable String apiId,
            @RequestBody Map<String, Object> inputPayload) {
        return ResponseEntity.ok(apiService.executeTransformForApi(projectId, apiId, inputPayload));
    }
}
